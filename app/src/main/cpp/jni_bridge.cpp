/*
 * PS1 Emulator for Android
 * Copyright (C) 2024-2026 MST. ROKIEA SULTANA
 * 2 No. College Gate Bylane, Mymensingh - 2207, Bangladesh (BD)
*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#include <cstring>
#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstdarg>
#include <atomic>
#include <new>
#include <pthread.h>
#include <sched.h>
#include <cerrno>
#include <sys/resource.h>
#include <jni.h>

#include <android/log.h>
#include <android/trace.h>

#include "audio_oboe.h"
#include "debug_log.h"
#include "profiler_trace.h"
#include "save_state.h"
#include "screenshot.h"

extern "C" {
#include <libretro.h>

#include "libpcsxcore/psxcommon.h"
#include "libpcsxcore/misc.h"
#include "libpcsxcore/r3000a.h"
#include "libpcsxcore/psxmem.h"
#include "libpcsxcore/cdrom.h"
#include "libpcsxcore/gpu.h"
#include "libpcsxcore/plugins.h"
#include "libpcsxcore/psxevents.h"
#include "libpcsxcore/sio.h"
#include "libpcsxcore/psxcounters.h"
#include "libpcsxcore/mdec.h"
#include "frontend/plugin_lib.h"
#include "frontend/main.h"
#include "frontend/cspace.h"
}

// Wrapper functions declared in gpu_freeze_wrapper.c (C file avoids the C++ typedef conflict
// where plugins.h typedefs GPUfreeze as a function pointer type, shadowing the actual function)
extern "C" {
    long gpu_freeze_save(GPUFreeze_t *freeze, uint16_t **vram_ptr);
    long gpu_freeze_load(GPUFreeze_t *freeze, uint16_t **vram_ptr);
    // Post-load sync: rebuilds renderer texture caches from restored VRAM and
    // forces a display update. Must be called after VRAM is memcpy'd.
    void gpu_post_load_sync(void);
}

#include "freeze_helpers.h"
#include "rewind_buffer.h"

// Safe wrapper: psxCpu may be null if retro_init() failed or core was deinitialized.
static inline void safeCpuNotify(int type, void *param) {
    if (psxCpu && psxCpu->Notify) {
        psxCpu->Notify(static_cast<R3000Anote>(type), param);
    }
}

// --- Libretro function declarations (defined in core/frontend/libretro.c) -------
extern void retro_init(void);
extern void retro_deinit(void);
extern bool retro_load_game(const struct retro_game_info *info);
extern void retro_run(void);
extern void retro_reset(void);
extern void retro_get_system_info(struct retro_system_info *info);
extern void retro_get_system_av_info(struct retro_system_av_info *info);
extern void retro_set_environment(retro_environment_t cb);
extern void retro_set_video_refresh(retro_video_refresh_t cb);
extern void retro_set_audio_sample(retro_audio_sample_t cb);
extern void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb);
extern void retro_set_input_poll(retro_input_poll_t cb);
extern void retro_set_input_state(retro_input_state_t cb);

// --- Libretro callback state ---------------------------------------------------

// PS1 maximum output geometry (see docs.libretro.com/library/pcsx_rearmed:
// max width 1024, max height 512). The framebuffer must be able to hold it �
// clamping to 320x240 crops hi-res modes such as 512x240 and 640x480 instead of
// scaling them.
static const unsigned FB_MAX_WIDTH  = 1024;
static const unsigned FB_MAX_HEIGHT = 512;

static uint32_t sFramebuffer[FB_MAX_WIDTH * FB_MAX_HEIGHT];
static unsigned sFbWidth = 384;
static unsigned sFbHeight = 240;
static size_t sFbPitch = 384 * 4;
static bool sFbDirty = false;
static int32_t sInputButtons = 0;
static float sAnalogX = 0.0f;
static float sAnalogY = 0.0f;
static float sRightAnalogX = 0.0f;
static float sRightAnalogY = 0.0f;
static bool sInitialized = false;
static bool sGameLoaded = false;
static enum retro_pixel_format sPixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;

// Fast-forward state
static std::atomic<int> sFastForwardMultiplier{1};
static std::atomic<bool> sFastForwardEnabled{false};

// Slow-motion state (non-static: accessed from audio_oboe.cpp for sample stretching)
std::atomic<bool> sSlowMotionEnabled{false};
std::atomic<int> sSlowMotionDivisor{1};

// Rewind state
static std::atomic<bool> sRewindEnabled{false};
static std::atomic<bool> sRewindClearPending{false};  // deferred clear request from UI thread
static std::atomic<int> sRewindInterval{20};     // frames between snapshots (set by depth)
static std::atomic<int> sRewindFrameCounter{0};  // counts frames since last snapshot

static char sBiosDir[512] = {0};
static char sSaveDir[512] = {0};

// --- Libretro core options -----------------------------------------------------
// PCSX-ReARMed reads its configuration through RETRO_ENVIRONMENT_GET_VARIABLE.
// For most options libretro.c uses the pattern
//     if (environ_cb(GET_VARIABLE, &var) && var.value) { ...apply... }
// with no else branch, so anything we fail to answer silently keeps the
// zero-initialised default � which for `thread_rendering` means "off". Answering
// these explicitly is what enables the core's threading, and is the single
// largest performance win available to the frontend.
//
// Values must match the option definitions in core/frontend/libretro_core_options.h.
struct CoreOption {
    const char* key;
    char        value[24];
};

static CoreOption sCoreOptions[] = {
    // --- Threading: the big wins -----------------------------------------
    // "auto" -> gpu_async_enable(-1) -> on when >1 CPU core is detected.
    { "pcsx_rearmed_gpu_thread_rendering",   "auto" },
    { "pcsx_rearmed_spu_thread",             "enabled" },
    { "pcsx_rearmed_drc_thread",             "auto" },

    // --- CPU -------------------------------------------------------------
    { "pcsx_rearmed_drc",                    "enabled" },
    // "auto" keeps the stock cycle multiplier. Under-clocking (e.g. "57") is a
    // real speedup but causes glitches/hangs in some titles, so it stays opt-in.
    { "pcsx_rearmed_psxclock",               "auto" },

    // --- Video -----------------------------------------------------------
    // Dithering costs a full-frame pass to hide banding that is barely visible
    // on a phone panel.
    { "pcsx_rearmed_dithering",              "disabled" },
    // Frame duping: lets the core skip re-sending an unchanged frame.
    { "pcsx_rearmed_duping_enable",          "enabled" },
    // 2x internal resolution is explicitly documented as "slow".
    { "pcsx_rearmed_neon_enhancement_enable", "disabled" },
    { "pcsx_rearmed_neon_enhancement_no_main", "disabled" },
    { "pcsx_rearmed_neon_interlace_enable",  "disabled" },

    // --- Storage ---------------------------------------------------------
    // Default is 12 sectors; flash storage on Android benefits from more
    // read-ahead to avoid mid-frame stalls during streaming/FMV.
    { "pcsx_rearmed_cd_readahead",           "128" },

    // --- Audio -----------------------------------------------------------
    { "pcsx_rearmed_spu_interpolation",      "simple" },
    { "pcsx_rearmed_spu_reverb",             "enabled" },

    // --- Existing frontend choices ---------------------------------------
    // --- Memory cards -------------------------------------------------
    // "shared" => the CORE itself owns the card files (CreateMcd/LoadMcd/
    // SaveMcd) in the save directory, formatting them and tracking free blocks
    // natively. This is exactly how RetroArch ships PCSX-ReARMed and avoids the
    // "insert another memory card / delete 1 block" full-card bug that the
    // libretro (frontend-persisted) path produced. Card 1/2 map to
    // <saveDir>/pcsx-card1.mcd and <saveDir>/pcsx-card2.mcd.
    { "pcsx_rearmed_memcard1",              "shared" },
    { "pcsx_rearmed_memcard2",              "shared" },

    { "pcsx_rearmed_bios",                   "HLE" },
    // Keep 32-bit output: our_video_refresh can then take a straight memcpy per
    // row. The RGB565 path would need a per-pixel unpack, which costs more CPU
    // than the extra bandwidth saves.
    { "pcsx_rearmed_rgb32_output",           "enabled" },
    { "pcsx_rearmed_show_bios_bootlogo",     "disabled" },
    { "pcsx_rearmed_region",                 "auto" },
};

static std::atomic<bool> sCoreOptionsDirty{false};

static const char* lookupCoreOption(const char* key) {
    for (auto& opt : sCoreOptions) {
        if (strcmp(opt.key, key) == 0) {
            return opt.value[0] ? opt.value : nullptr;
        }
    }
    return nullptr;
}

static bool setCoreOption(const char* key, const char* value) {
    for (auto& opt : sCoreOptions) {
        if (strcmp(opt.key, key) == 0) {
            snprintf(opt.value, sizeof(opt.value), "%s", value);
            sCoreOptionsDirty.store(true);
            LOGI("Core option %s = %s", key, opt.value);
            return true;
        }
    }
    LOGW("setCoreOption: unknown key %s", key);
    return false;
}

// --- Ps1Buttons ? libretro bit positions --------------------------------------
// Ps1Buttons:  SELECT(0), L3(1), R3(2), START(3), UP(4), RIGHT(5), DOWN(6), LEFT(7),
//              L2(8), R2(9), L1(10), R1(11), TRIANGLE(12), CIRCLE(13), CROSS(14), SQUARE(15)
// Libretro:    B(0)=CROSS, Y(1)=SQUARE, SELECT(2), START(3), UP(4), DOWN(5), LEFT(6),
//              RIGHT(7), A(8)=CIRCLE, X(9)=TRIANGLE, L(10)=L1, R(11)=R1, L2(12), R2(13),
//              L3(14), R3(15)
static const int ps1ToLibretroBit[16] = {
    2,   // SELECT(0) ? LIBRETRO_SELECT(2)
    14,  // L3(1)     ? LIBRETRO_L3(14)
    15,  // R3(2)     ? LIBRETRO_R3(15)
    3,   // START(3)  ? LIBRETRO_START(3)
    4,   // UP(4)     ? LIBRETRO_UP(4)
    7,   // RIGHT(5)  ? LIBRETRO_RIGHT(7)
    5,   // DOWN(6)   ? LIBRETRO_DOWN(5)
    6,   // LEFT(7)   ? LIBRETRO_LEFT(6)
    12,  // L2(8)     ? LIBRETRO_L2(12)
    13,  // R2(9)     ? LIBRETRO_R2(13)
    10,  // L1(10)    ? LIBRETRO_L1(10)
    11,  // R1(11)    ? LIBRETRO_R1(11)
    9,   // TRIANGLE(12) ? LIBRETRO_X(9)
    8,   // CIRCLE(13) ? LIBRETRO_A(8)
    0,   // CROSS(14)  ? LIBRETRO_B(0)
    1,   // SQUARE(15) ? LIBRETRO_Y(1)
};

// --- Audio ---------------------------------------------------------------------

static const int32_t kAudioSampleRate = 44100;
static const int32_t kAudioChannels = 2;

// Buffer size requested from Java. Written by nativeSetAudioBufferSize() (UI
// thread), read by the emulation thread, which is the only thread that opens or
// closes the audio stream � see applyPendingAudioConfig().
static std::atomic<int32_t> gRequestedBufferFrames{512};

// Buffer size the live stream was opened with. Emulation thread only.
static int32_t sCurrentBufferFrames = 0;

// Audio mute control � checked in the Oboe callback.
// Set from Java via nativeSetAudioEnabled().
std::atomic<bool> gAudioEnabled{true};

// Open or reopen the audio stream when something asked for it. Called from the
// emulation thread at the top of every frame.
//
// All stream lifecycle work is funnelled through here on purpose. Opening a
// stream from the UI thread (which is where a settings change arrives) while the
// emulation thread is initialising or shutting down was racing on the stream
// pointer itself: both threads could stop/close/replace the same stream, which
// left stale pointers behind and orphaned running streams. It also blocked the
// UI thread for as long as the audio device took to hand out a stream.
static void applyPendingAudioConfig() {
    int32_t requested = gRequestedBufferFrames.load(std::memory_order_relaxed);

    if (requested != sCurrentBufferFrames) {
        // Record the new size even if the open fails, so a device that cannot
        // give us a stream is not retried on every single frame.
        sCurrentBufferFrames = requested;
        LOGI("Audio: reopening stream with framesPerCallback=%d", requested);
        audioStreamOpen(kAudioSampleRate, kAudioChannels, requested);
        return;
    }

    if (audioStreamTakeRestartRequest()) {
        LOGI("Audio: reopening stream after system close");
        audioStreamOpen(kAudioSampleRate, kAudioChannels, sCurrentBufferFrames);
    }
}

// --- Libretro callbacks --------------------------------------------------------

static void our_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0 || pitch == 0) return;
    if (width > 4096 || height > 2048) return; // sanity-limit absurd geometries
    unsigned clipW = (width > 1024) ? 1024 : width;
    unsigned clipH = (height > 512) ? 512 : height;
    sFbWidth = clipW;
    sFbHeight = clipH;

    if (sPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        sFbPitch = (pitch < clipW * 4) ? pitch : clipW * 4;
        const uint8_t* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = reinterpret_cast<uint8_t*>(sFramebuffer);
        for (unsigned y = 0; y < clipH; y++) {
            memcpy(dst + y * clipW * 4, src + y * pitch, sFbPitch);
        }
    } else if (sPixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        sFbPitch = clipW * 4;
        const uint16_t* src16 = static_cast<const uint16_t*>(data);
        for (unsigned y = 0; y < clipH; y++) {
            const uint16_t* row = reinterpret_cast<const uint16_t*>(
                static_cast<const uint8_t*>(data) + y * pitch);
            for (unsigned x = 0; x < clipW; x++) {
                uint16_t p = row[x];
                uint32_t r = ((p >> 11) & 0x1F) << 3;
                uint32_t g = ((p >> 5) & 0x3F) << 2;
                uint32_t b = (p & 0x1F) << 3;
                sFramebuffer[y * clipW + x] = (r << 16) | (g << 8) | b;
            }
        }
    }
    sFbDirty = true;
}

static size_t our_audio_batch(const int16_t *data, size_t frames) {
    if (frames == 0) return 0;
    size_t totalSamples = frames * 2;
    if (audioRingBuffer.push(data, totalSamples)) {
        return frames;
    }
    size_t space = audioRingBuffer.capacity() - audioRingBuffer.available();
    space = space & ~1u;
    if (space >= 2) {
        audioRingBuffer.push(data, space);
        return space / 2;
    }
    return 0;
}

static void our_input_poll(void) {
}

static int16_t our_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (device == RETRO_DEVICE_JOYPAD && port == 0) {
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
            int32_t libretroMask = 0;
            for (int i = 0; i < 16; i++) {
                if (sInputButtons & (1 << i)) {
                    libretroMask |= (1 << ps1ToLibretroBit[i]);
                }
            }
            return static_cast<int16_t>(libretroMask & 0xFFFF);
        }
        for (int i = 0; i < 16; i++) {
            if (ps1ToLibretroBit[i] == static_cast<int>(id)) {
                return (sInputButtons & (1 << i)) ? 1 : 0;
            }
        }
    }
    if (device == RETRO_DEVICE_ANALOG && port == 0) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X)
                return static_cast<int16_t>(sAnalogX * 32767.0f);
            if (id == RETRO_DEVICE_ID_ANALOG_Y)
                return static_cast<int16_t>(sAnalogY * 32767.0f);
        }
        if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X)
                return static_cast<int16_t>(sRightAnalogX * 32767.0f);
            if (id == RETRO_DEVICE_ID_ANALOG_Y)
                return static_cast<int16_t>(sRightAnalogY * 32767.0f);
        }
    }
    return 0;
}

// --- Libretro environment callback ---------------------------------------------

static void retro_log_func(enum retro_log_level level, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int androidLevel = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: androidLevel = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_WARN:  androidLevel = ANDROID_LOG_WARN; break;
        case RETRO_LOG_ERROR: androidLevel = ANDROID_LOG_ERROR; break;
        default: break;
    }
    __android_log_vprint(androidLevel, "PS1Core", fmt, ap);
    va_end(ap);
}

static bool our_environment(unsigned cmd, void *data) {
    switch (cmd) {
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
        struct retro_log_callback *cb = static_cast<struct retro_log_callback*>(data);
        cb->log = retro_log_func;
        return true;
    }
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
        const char **dir = static_cast<const char **>(data);
        if (sBiosDir[0]) {
            *dir = sBiosDir;
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
        const char **dir = static_cast<const char **>(data);
        if (sSaveDir[0]) {
            *dir = sSaveDir;
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
        // We can dupe: when the core reports an unchanged frame it passes NULL
        // to video_refresh, our_video_refresh returns early leaving sFbDirty
        // false, and the GL thread simply redraws the existing texture. Saying
        // "false" here forces the core to re-send (and us to re-convert) every
        // frame, and disables its frameskip logic entirely.
        *static_cast<bool*>(data) = true;
        return true;
    }
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
        enum retro_pixel_format fmt = *static_cast<enum retro_pixel_format*>(data);
        if (fmt == RETRO_PIXEL_FORMAT_XRGB8888 || fmt == RETRO_PIXEL_FORMAT_RGB565) {
            sPixelFormat = fmt;
            return true;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: {
        return true;
    }
    case RETRO_ENVIRONMENT_SET_ROTATION:
    case RETRO_ENVIRONMENT_SET_GEOMETRY:
    case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
    case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
    case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE:
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
    case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
    case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
    case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
    case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
    case RETRO_ENVIRONMENT_SET_MESSAGE:
    case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:
    case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
    case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
    case RETRO_ENVIRONMENT_SET_HW_RENDER:
    case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE:
    case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
    case RETRO_ENVIRONMENT_SET_VARIABLES:
        return true;
    case RETRO_ENVIRONMENT_GET_VARIABLE: {
        struct retro_variable *var = static_cast<struct retro_variable*>(data);
        var->value = lookupCoreOption(var->key);
        return var->value != nullptr;
    }
    case RETRO_ENVIRONMENT_SET_VARIABLE: {
        return true;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
        bool *updated = static_cast<bool*>(data);
        *updated = sCoreOptionsDirty.exchange(false);
        return true;
    }
    default:
        return false;
    }
}

// --- Thread priority -----------------------------------------------------------

// Android apps do not hold CAP_SYS_NICE, so requesting a real-time policy
// (SCHED_RR/SCHED_FIFO) fails with EPERM and leaves the emulation thread at
// default priority. Lowering the nice value is the strongest lever available,
// and it does take effect. Java-side callers should additionally use
// Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO).
static void raiseEmulationThreadPriority() {
    errno = 0;
    if (setpriority(PRIO_PROCESS, 0, -16) != 0 && errno != 0) {
        // -16 needs a privileged uid on some ROMs; fall back to the highest
        // value an ordinary app is reliably allowed.
        errno = 0;
        if (setpriority(PRIO_PROCESS, 0, -8) != 0 && errno != 0) {
            LOGW("raiseEmulationThreadPriority: setpriority failed (errno=%d)", errno);
            return;
        }
    }
    LOGI("Emulation thread priority raised (nice=%d)", getpriority(PRIO_PROCESS, 0));
}

// --- JNI: nativeInit ----------------------------------------------------------

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeInit(
        JNIEnv* env,
        jclass /* clazz */,
        jstring biosPath,
        jstring card1Path,
        jstring card2Path) {
    ATRACE_FN();
    env->PushLocalFrame(16);

    if (sInitialized) {
        LOGW("nativeInit: already initialized, returning 0");
        env->PopLocalFrame(nullptr);
        return 0;
    }

    LOGI("nativeInit: initializing PCSX-ReARMed core (HLE BIOS mode)");

    // Extract BIOS directory from biosPath if provided
    if (biosPath != nullptr) {
        const char* path = env->GetStringUTFChars(biosPath, nullptr);
        if (path && path[0] != '\0') {
            // Extract directory part
            const char* lastSlash = strrchr(path, '/');
            if (lastSlash) {
                size_t dirLen = lastSlash - path;
                if (dirLen < sizeof(sBiosDir)) {
                    memcpy(sBiosDir, path, dirLen);
                    sBiosDir[dirLen] = '\0';
                    LOGD("nativeInit: bios dir = %s", sBiosDir);
                }
            }
            env->ReleaseStringUTFChars(biosPath, path);
        } else if (path) {
            env->ReleaseStringUTFChars(biosPath, path);
        }
    }

    // Set up memory card paths
    const char* c1 = card1Path ? env->GetStringUTFChars(card1Path, nullptr) : nullptr;
    const char* c2 = card2Path ? env->GetStringUTFChars(card2Path, nullptr) : nullptr;
    if (c1) {
        strncpy(Config.Mcd1, c1, sizeof(Config.Mcd1) - 1);
        env->ReleaseStringUTFChars(card1Path, c1);
    }
    if (c2) {
        strncpy(Config.Mcd2, c2, sizeof(Config.Mcd2) - 1);
        env->ReleaseStringUTFChars(card2Path, c2);
    }
    LOGD("nativeInit: cards=%s, %s", Config.Mcd1, Config.Mcd2);

    // Set save directory based on card path (use parent directory)
    if (c1) {
        const char* lastSlash = strrchr(Config.Mcd1, '/');
        if (lastSlash) {
            size_t dirLen = lastSlash - Config.Mcd1;
            if (dirLen < sizeof(sSaveDir)) {
                memcpy(sSaveDir, Config.Mcd1, dirLen);
                sSaveDir[dirLen] = '\0';
            }
        }
    }

    // Set libretro callbacks
    retro_set_environment(our_environment);
    retro_set_video_refresh(our_video_refresh);
    retro_set_audio_sample_batch(our_audio_batch);
    retro_set_input_poll(our_input_poll);
    retro_set_input_state(our_input_state);

    // Initialize the PCSX-ReARMed core
    retro_init();

    // Initialize audio. audioStreamOpen() clears and prefills the ring buffer.
    sCurrentBufferFrames = gRequestedBufferFrames.load(std::memory_order_relaxed);
    if (!audioStreamOpen(kAudioSampleRate, kAudioChannels, sCurrentBufferFrames)) {
        LOGE("Failed to create Oboe stream � continuing without audio");
    }

    sInputButtons = 0;
    sAnalogX = 0.0f;
    sAnalogY = 0.0f;
    sRightAnalogX = 0.0f;
    sRightAnalogY = 0.0f;
    sGameLoaded = false;
    sPixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;

    // Clear framebuffer to prevent stale data from previous sessions
    // from showing through when the new game renders partial frames
    memset(sFramebuffer, 0, sizeof(sFramebuffer));
    sFbWidth = 384;
    sFbHeight = 240;
    sFbDirty = false;

    sInitialized = true;

    // Initialize rewind buffer (30 snapshots ~ ~4.2MB = ~126MB)
    gRewindBuffer.init(30);

    LOGI("nativeInit complete");
    env->PopLocalFrame(nullptr);
    return 0;
}

// --- JNI: nativeLoadGame -------------------------------------------------------

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeLoadGame(
        JNIEnv* env,
        jclass /* clazz */,
        jstring romPath) {
    ATRACE_FN();
    env->PushLocalFrame(8);

    if (!sInitialized) {
        LOGE("nativeLoadGame: core not initialized");
        env->PopLocalFrame(nullptr);
        return -2;
    }

    const char* path = env->GetStringUTFChars(romPath, nullptr);
    if (!path) {
        LOGE("nativeLoadGame: romPath is null");
        env->PopLocalFrame(nullptr);
        return -3;
    }

    LOGI("nativeLoadGame: romPath=%s", path);

    struct retro_game_info info;
    memset(&info, 0, sizeof(info));
    info.path = path;

    bool result = retro_load_game(&info);
    env->ReleaseStringUTFChars(romPath, path);

    if (!result) {
        LOGE("nativeLoadGame: retro_load_game failed");
        env->PopLocalFrame(nullptr);
        return -1;
    }

    sGameLoaded = true;
    LOGI("nativeLoadGame: game loaded successfully");
    env->PopLocalFrame(nullptr);
    return 0;
}

// --- JNI: nativeSetAudioBufferSize --------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetAudioBufferSize(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint frames) {
    if (frames < 128) frames = 128;
    if (frames > 1024) frames = 1024;
    int32_t powerOf2 = 1;
    while (powerOf2 < frames) powerOf2 <<= 1;
    if (powerOf2 > 1024) powerOf2 = 1024;

    // Only publish the request. This is called on the UI thread (settings
    // change, activity resume); the emulation thread picks the value up on its
    // next frame and does the actual open/close. Touching the stream from here
    // would race with nativeInit()/nativeShutdown() and could leave a stream
    // running with no emulator behind it.
    gRequestedBufferFrames.store(powerOf2, std::memory_order_relaxed);
}

// --- JNI: nativeSaveState / nativeLoadState -----------------------------------

// --- Extra state save (v5): SIO, CD-ROM, counters, MDEC, dynarec, pad --------
// Serializes all freeze function state into a memory buffer via SaveFuncs redirect.
static uint32_t save_extra_state_to_buffer(uint8_t *buf, size_t capacity) {
    SaveFuncsGuard guard;

    MemFile mf = { buf, 0, 0, capacity };

    FREEZE_SAVE_SECTION(mf, sioFreeze);
    FREEZE_SAVE_SECTION(mf, cdrFreeze);
    FREEZE_SAVE_SECTION(mf, psxRcntFreeze);
    FREEZE_SAVE_SECTION(mf, mdecFreeze);
    FREEZE_SAVE_SECTION(mf, ndrc_freeze);
    FREEZE_SAVE_SECTION(mf, padFreeze);

    return (uint32_t)mf.size;
}

// --- Extra state load (v5): deserializes freeze function state from buffer ---
static void load_extra_state_from_buffer(const uint8_t *buf, uint32_t total_size) {
    SaveFuncsGuard guard;

    size_t pos = 0;

    // Each FREEZE_LOAD_SECTION reads [size(4)][data] and calls freeze_func(f, 0)
    // If a section is missing or corrupted, we break out � degraded but functional
    FREEZE_LOAD_SECTION(buf, pos, total_size, sioFreeze);
    FREEZE_LOAD_SECTION(buf, pos, total_size, cdrFreeze);
    FREEZE_LOAD_SECTION(buf, pos, total_size, psxRcntFreeze);
    FREEZE_LOAD_SECTION(buf, pos, total_size, mdecFreeze);
    FREEZE_LOAD_SECTION(buf, pos, total_size, ndrc_freeze);
    FREEZE_LOAD_SECTION(buf, pos, total_size, padFreeze);

    LOGI("load_extra_state: loaded %zu bytes of extra state", pos);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSaveState(
        JNIEnv* env,
        jclass /* clazz */,
        jstring jPath) {
    ATRACE_FN();
    env->PushLocalFrame(8);
    if (!sInitialized) {
        LOGE("nativeSaveState: not initialized");
        env->PopLocalFrame(nullptr);
        return -2;
    }
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) { env->PopLocalFrame(nullptr); return -3; }

    LOGI("nativeSaveState: saving to '%s'", path);

    // CoreState is ~4MB, too large for the stack � allocate on heap
    CoreState* statePtr = new (std::nothrow) CoreState;
    if (!statePtr) {
        LOGE("nativeSaveState: failed to allocate CoreState");
        env->ReleaseStringUTFChars(jPath, path);
        return -1;
    }
    CoreState& state = *statePtr;
    memset(&state, 0, sizeof(state));

    // Notify CPU core to sync cached state with psxRegs (needed for dynarec)
    safeCpuNotify(R3000ACPU_NOTIFY_BEFORE_SAVE, NULL);

    // CPU: GPRs (r0..r31), PC, LO, HI
    memcpy(state.regs, psxRegs.GPR.r, sizeof(state.regs));
    state.pc = psxRegs.pc;
    state.lo = psxRegs.GPR.n.lo;
    state.hi = psxRegs.GPR.n.hi;

    // Coprocessor 0
    memcpy(state.cp0_regs, psxRegs.CP0.r, sizeof(state.cp0_regs));

    // GPU: use GPUfreeze for proper state save (flushes pending commands,
    // saves all control/extended registers, and provides VRAM pointer)
    GPUFreeze_t gpuFreeze;
    memset(&gpuFreeze, 0, sizeof(gpuFreeze));
    gpuFreeze.ulFreezeVersion = 1;
    uint16_t* vramPtr = nullptr;
    gpu_freeze_save(&gpuFreeze, &vramPtr);
    // Copy GPU control registers from freeze struct into our save state
    memcpy(state.gpu_regs, gpuFreeze.ulControl, sizeof(state.gpu_regs));
    // Copy GPU extended registers (stored at ulControl[0xe0..0xe7])
    memcpy(state.gpu_ex_regs, gpuFreeze.ulControl + 0xe0, sizeof(state.gpu_ex_regs));

    // GPU VRAM: copy from the VRAM pointer returned by GPUfreeze
    if (vramPtr) {
        memcpy(state.gpu_vram, vramPtr, GPU_VRAM_SIZE);
    }

    // v4: Coprocessor 2 (GTE) � geometry transform engine registers
    memcpy(state.cp2_data, psxRegs.CP2.CP2D.r, sizeof(state.cp2_data));
    memcpy(state.cp2_ctrl, psxRegs.CP2.CP2C.r, sizeof(state.cp2_ctrl));

    // v4: CPU timing state � cycle counter, interrupts, event scheduling
    state.cpu_code           = psxRegs.code;
    state.cpu_cycle          = psxRegs.cycle;
    state.cpu_interrupt      = psxRegs.interrupt;
    memcpy(state.cpu_intCycle, psxRegs.intCycle, sizeof(state.cpu_intCycle));
    memcpy(state.cpu_event_cycles, psxRegs.event_cycles, sizeof(state.cpu_event_cycles));
    state.cpu_psxNextCounter  = psxRegs.psxNextCounter;
    state.cpu_psxNextsCounter = psxRegs.psxNextsCounter;
    state.cpu_next_interupt   = psxRegs.next_interupt;
    state.cpu_gteBusyCycle    = psxRegs.gteBusyCycle;
    state.cpu_muldivBusyCycle = psxRegs.muldivBusyCycle;
    state.cpu_biuReg          = psxRegs.biuReg;
    state.cpu_biosBranchCheck = psxRegs.biosBranchCheck;
    state.cpu_gpuIdleAfter    = psxRegs.gpuIdleAfter;

    // v4: SPU state via SPU_freeze plugin function
    state.spu_data_size = 0;
    if (SPU_freeze) {
        unsigned short *spuram = NULL;
        // Use stack-local SPUFreeze_t for proper alignment (packed struct may be unaligned)
        SPUFreeze_t spu_hdr_local;
        memset(&spu_hdr_local, 0, sizeof(spu_hdr_local));
        SPU_freeze(1, &spu_hdr_local, &spuram, state.spu_part2, psxRegs.cycle);
        memcpy(state.spu_hdr_data, &spu_hdr_local, sizeof(spu_hdr_local));
        state.spu_data_size = spu_hdr_local.Size;
        if (spuram) {
            memcpy(state.spu_ram, spuram, SPU_RAM_SIZE);
        }
    }

    // SPU: placeholder � plugin handles its own freeze via SPU_freeze in full save path
    memset(state.spu_regs, 0, sizeof(state.spu_regs));

    // CD-ROM
    state.cdrom_pos = 0;
    state.cdrom_status = 0;

    // PSX memory: RAM (2 MB), BIOS ROM (512 KB), hardware registers (64 KB)
    if (psxRegs.ptrs.psxM) memcpy(state.psx_ram, psxRegs.ptrs.psxM, sizeof(state.psx_ram));
    if (psxRegs.ptrs.psxR) memcpy(state.bios_rom, psxRegs.ptrs.psxR, sizeof(state.bios_rom));
    if (psxRegs.ptrs.psxH) memcpy(state.hw_regs, psxRegs.ptrs.psxH, sizeof(state.hw_regs));

    // Game ID
    strncpy(state.game_id, CdromId, sizeof(state.game_id) - 1);

    // Screenshot: capture actual framebuffer (XRGB8888 -> RGBA)
    uint32_t sw = 0, sh = 0;
    uint8_t* screenshot = capture_framebuffer_from(sFramebuffer, sFbWidth, sFbHeight, &sw, &sh);
    char png_path[1024];
    snprintf(png_path, sizeof(png_path), "%s.png", path);
    write_png(png_path, screenshot, sw, sh);

    int result = core_save_state(path, &state, screenshot, sw, sh);
    free(screenshot);

    // v5: Append extra state section (SIO, CD-ROM, counters, MDEC, dynarec, pad)
    if (result == 0) {
        uint8_t *extra_buf = (uint8_t *)malloc(EXTRA_STATE_BUF_SIZE);
        if (extra_buf) {
            uint32_t extra_size = save_extra_state_to_buffer(extra_buf, EXTRA_STATE_BUF_SIZE);
            if (extra_size > 0) {
                FILE *f = fopen(path, "ab"); // append after CoreState + screenshot
                if (f) {
                    fwrite(&EXTRA_STATE_MAGIC, 4, 1, f);
                    fwrite(&extra_size, 4, 1, f);
                    fwrite(extra_buf, 1, extra_size, f);
                    fclose(f);
                    LOGI("nativeSaveState: extra state saved (%u bytes)", extra_size);
                }
            }
            free(extra_buf);
        }
    }

    delete statePtr;
    env->ReleaseStringUTFChars(jPath, path);
    env->PopLocalFrame(nullptr);
    return (result == 0) ? 0 : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeLoadState(
        JNIEnv* env,
        jclass /* clazz */,
        jstring jPath) {
    ATRACE_FN();
    env->PushLocalFrame(8);
    if (!sInitialized) {
        LOGE("nativeLoadState: not initialized");
        env->PopLocalFrame(nullptr);
        return -2;
    }
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) { env->PopLocalFrame(nullptr); return -3; }

    LOGI("nativeLoadState: loading from '%s'", path);

    // CoreState is ~4MB, too large for the stack � allocate on heap
    CoreState* statePtr = new (std::nothrow) CoreState;
    if (!statePtr) {
        LOGE("nativeLoadState: failed to allocate CoreState");
        env->ReleaseStringUTFChars(jPath, path);
        return -1;
    }
    CoreState& state = *statePtr;
    memset(&state, 0, sizeof(state));

    uint8_t* screenshot = nullptr;
    uint32_t sw = 0, sh = 0;
    int result = core_load_state(path, &state, &screenshot, &sw, &sh);
    LOGI("nativeLoadState: core_load_state returned %d", result);

    // v5: Try to read extra state section (SIO, CD-ROM, counters, MDEC, dynarec, pad)
    // This is appended after the CoreState + screenshot in v5 save files.
    uint8_t *extra_buf = nullptr;
    uint32_t extra_size = 0;
    {
        FILE *ef = fopen(path, "rb");
        if (ef) {
            // Compute offset: header(12) + CoreState + screenshot_header(8) + screenshot
            // Re-read header to get exact core_size (handles v1-v3 size differences)
            uint32_t hdr_magic, hdr_version, hdr_core_size;
            fread(&hdr_magic, 4, 1, ef);
            fread(&hdr_version, 4, 1, ef);
            fread(&hdr_core_size, 4, 1, ef);
            fseek(ef, hdr_core_size, SEEK_CUR);   // skip CoreState
            uint32_t hdr_sw = 0, hdr_sh = 0;
            fread(&hdr_sw, 4, 1, ef);
            fread(&hdr_sh, 4, 1, ef);
            fseek(ef, (long)hdr_sw * hdr_sh * 4, SEEK_CUR); // skip screenshot
            // Try to read extra section
            uint32_t ext_magic = 0;
            if (fread(&ext_magic, 4, 1, ef) == 1 && ext_magic == EXTRA_STATE_MAGIC) {
                fread(&extra_size, 4, 1, ef);
                if (extra_size > 0 && extra_size < 256 * 1024) {
                    extra_buf = (uint8_t *)malloc(extra_size);
                    if (extra_buf) {
                        size_t nread = fread(extra_buf, 1, extra_size, ef);
                        if (nread != extra_size) {
                            free(extra_buf);
                            extra_buf = nullptr;
                            extra_size = 0;
                        }
                    }
                }
            }
            fclose(ef);
            LOGI("nativeLoadState: extra section %s (size=%u)",
                 extra_buf ? "found" : "not found", extra_size);
        }
    }

    if (result == 0) {
        // Notify CPU core to sync cached state before we overwrite psxRegs
        safeCpuNotify(R3000ACPU_NOTIFY_BEFORE_SAVE, NULL);

        // CPU: GPRs, PC, LO, HI
        memcpy(psxRegs.GPR.r, state.regs, sizeof(state.regs));
        psxRegs.pc = state.pc;
        psxRegs.GPR.n.lo = state.lo;
        psxRegs.GPR.n.hi = state.hi;

        // Coprocessor 0
        memcpy(psxRegs.CP0.r, state.cp0_regs, sizeof(state.cp0_regs));

        // PSX memory: RAM (2 MB), BIOS ROM (512 KB), hardware registers (64 KB)
        if (psxRegs.ptrs.psxM) memcpy(psxRegs.ptrs.psxM, state.psx_ram, sizeof(state.psx_ram));
        if (psxRegs.ptrs.psxR) memcpy(psxRegs.ptrs.psxR, state.bios_rom, sizeof(state.bios_rom));
        if (psxRegs.ptrs.psxH) memcpy(psxRegs.ptrs.psxH, state.hw_regs, sizeof(state.hw_regs));

        // GPU: restore via GPUfreeze for proper state restore
        // (flushes pending commands, restores all registers, syncs renderer)
        uint16_t* vramPtr = nullptr;
        GPUFreeze_t gpuFreeze;
        memset(&gpuFreeze, 0, sizeof(gpuFreeze));
        gpuFreeze.ulFreezeVersion = 1;
        // Restore GPU control registers into freeze struct
        memcpy(gpuFreeze.ulControl, state.gpu_regs, sizeof(state.gpu_regs));
        // Restore GPU extended registers (stored at ulControl[0xe0..0xe7])
        memcpy(gpuFreeze.ulControl + 0xe0, state.gpu_ex_regs, sizeof(state.gpu_ex_regs));
        // Restore GPU status
        gpuFreeze.ulStatus = state.gpu_regs[0];
        // Call GPUfreeze to restore GPU state and get VRAM pointer
        gpu_freeze_load(&gpuFreeze, &vramPtr);
        LOGI("nativeLoadState: GPUfreeze load done, vramPtr=%p", (void*)vramPtr);
        // Copy saved VRAM into the GPU's VRAM buffer
        if (vramPtr) {
            memcpy(vramPtr, state.gpu_vram, GPU_VRAM_SIZE);
            LOGI("nativeLoadState: VRAM restored (%u bytes)", GPU_VRAM_SIZE);
        } else {
            LOGW("nativeLoadState: vramPtr is null, VRAM not restored");
        }

        // CRITICAL: Rebuild renderer texture caches AFTER VRAM restore.
        // GPUfreeze's renderer_update_caches() ran BEFORE VRAM was copied,
        // so the NEON renderer's texture cache was built from stale data.
        // This also forces a display update on the next vblank.
        gpu_post_load_sync();
        LOGI("nativeLoadState: post-load sync done (caches rebuilt, display forced)");

        // v4: Restore Coprocessor 2 (GTE) registers � critical for 3D geometry
        memcpy(psxRegs.CP2.CP2D.r, state.cp2_data, sizeof(state.cp2_data));
        memcpy(psxRegs.CP2.CP2C.r, state.cp2_ctrl, sizeof(state.cp2_ctrl));

        // v4: Restore CPU timing state
        psxRegs.code           = state.cpu_code;
        psxRegs.cycle          = state.cpu_cycle;
        psxRegs.interrupt      = state.cpu_interrupt;
        memcpy(psxRegs.intCycle, state.cpu_intCycle, sizeof(state.cpu_intCycle));
        memcpy(psxRegs.event_cycles, state.cpu_event_cycles, sizeof(state.cpu_event_cycles));
        psxRegs.psxNextCounter  = state.cpu_psxNextCounter;
        psxRegs.psxNextsCounter = state.cpu_psxNextsCounter;
        psxRegs.next_interupt   = state.cpu_next_interupt;
        psxRegs.gteBusyCycle    = state.cpu_gteBusyCycle;
        psxRegs.muldivBusyCycle = state.cpu_muldivBusyCycle;
        psxRegs.biuReg          = state.cpu_biuReg;
        psxRegs.biosBranchCheck = state.cpu_biosBranchCheck;
        psxRegs.gpuIdleAfter    = state.cpu_gpuIdleAfter;

        // v4: Clean up runtime flags (matches PCSX native LoadState behavior)
        psxRegs.branching = 0;
        psxRegs.cpuInRecursion = 0;
        psxRegs.gpuIdleAfter = psxRegs.cycle - 1;

        // Clear GPU nBUSY bit in HW register to prevent stuck-busy after load
        HW_GPU_STATUS &= ~PSXGPU_nBUSY;

        // v4: Restore SPU state via SPU_freeze plugin function
        if (SPU_freeze && state.spu_data_size > 0) {
            unsigned short *spuram = NULL;
            // First call: allocate SPU RAM buffer
            SPU_freeze(0, NULL, &spuram, NULL, 0);
            if (spuram) {
                memcpy(spuram, state.spu_ram, SPU_RAM_SIZE);
            }
            // Use stack-local SPUFreeze_t for proper alignment
            SPUFreeze_t spu_hdr_local;
            memcpy(&spu_hdr_local, state.spu_hdr_data, sizeof(spu_hdr_local));
            uint32_t part2_size = state.spu_data_size - SPU_HEADER_SIZE - SPU_RAM_SIZE;
            if ((int32_t)part2_size > 0 && part2_size <= SPU_PART2_MAX) {
                SPU_freeze(0, &spu_hdr_local, &spuram, state.spu_part2, psxRegs.cycle);
            } else {
                // Fallback: just restore header + RAM
                SPU_freeze(0, &spu_hdr_local, &spuram, NULL, psxRegs.cycle);
            }
            LOGI("nativeLoadState: SPU state restored (%u bytes)", state.spu_data_size);
        }

        // v5: Restore extra state (SIO, CD-ROM, counters, MDEC, dynarec, pad).
        // These are all the freeze functions that the native PCSX LoadState calls
        // but were missing from our v4 save format.
        // The extra state includes ndrc_freeze which revalidates dynarec blocks
        // from saved addresses � much faster than ndrc_clear_full() which wipes
        // everything and forces full recompilation from scratch.
        bool extra_loaded = false;
        if (extra_buf && extra_size > 0) {
            load_extra_state_from_buffer(extra_buf, extra_size);
            extra_loaded = true;
            LOGI("nativeLoadState: extra state loaded via freeze functions");
        }
        free(extra_buf);
        extra_buf = nullptr;

        if (!extra_loaded) {
            // Fallback for old (v4 and earlier) saves: clear dynarec cache
            // Note: this is slower than ndrc_freeze() because all blocks must
            // be recompiled from scratch on the first frame after load.
            ndrc_clear_full();
            LOGI("nativeLoadState: no extra state � using ndrc_clear_full() fallback");
        }

        // v4: Recalculate event_cycles from restored intCycle data
        events_restore();

        // Notify CPU core that RAM/state was replaced.
        // IMPORTANT: use R3000ACPU_NOTIFY_AFTER_LOAD, NOT _AFTER_LOAD_STATE.
        // _AFTER_LOAD_STATE assumes ndrc_freeze() already invalidated the
        // ari64 translation cache, but that is only safe if the cache has NOT
        // wrapped since the snapshot was taken. During normal play the ari64
        // translation cache fills and wraps, so the saved block list points at
        // OVERWRITTEN generated code -> SIGSEGV inside retro_run on the next
        // executed (stale) block. _AFTER_LOAD triggers ari64_reset() which
        // invalidates every translated block, so they are safely recompiled
        // from the restored RAM instead of executing garbage.
        safeCpuNotify(R3000ACPU_NOTIFY_AFTER_LOAD, NULL);

        LOGI("nativeLoadState: state restored for game '%s'", state.game_id);
    } else {
        LOGE("nativeLoadState: core_load_state FAILED with code %d for '%s'", result, path);
    }
    free(screenshot);
    delete statePtr;
    env->ReleaseStringUTFChars(jPath, path);
    env->PopLocalFrame(nullptr);
    return (result == 0) ? 0 : -1;
}

// --- Rewind: capture snapshot into ring buffer --------------------------------
// Captures the current emulator state (CoreState + extra state) into the next
// available slot in the rewind ring buffer. Called from nativeEmulateFrame()
// every sRewindInterval frames when rewind is enabled.
static void captureRewindSnapshot() {
    if (!sInitialized || !sGameLoaded) return;

    uint8_t* slot = gRewindBuffer.nextSlot();
    if (!slot) return;

    // The slot layout matches RewindSnapshot: CoreState + extraSize + extraData
    CoreState* state = reinterpret_cast<CoreState*>(slot);
    memset(state, 0, sizeof(CoreState));

    uint32_t* extraSizePtr = reinterpret_cast<uint32_t*>(slot + sizeof(CoreState));
    uint8_t* extraData = slot + sizeof(CoreState) + 4;

    // Notify CPU core to sync cached state with psxRegs
    safeCpuNotify(R3000ACPU_NOTIFY_BEFORE_SAVE, NULL);

    // CPU: GPRs, PC, LO, HI
    memcpy(state->regs, psxRegs.GPR.r, sizeof(state->regs));
    state->pc = psxRegs.pc;
    state->lo = psxRegs.GPR.n.lo;
    state->hi = psxRegs.GPR.n.hi;

    // Coprocessor 0
    memcpy(state->cp0_regs, psxRegs.CP0.r, sizeof(state->cp0_regs));

    // GPU: use GPUfreeze for proper state save
    GPUFreeze_t gpuFreeze;
    memset(&gpuFreeze, 0, sizeof(gpuFreeze));
    gpuFreeze.ulFreezeVersion = 1;
    uint16_t* vramPtr = nullptr;
    gpu_freeze_save(&gpuFreeze, &vramPtr);
    memcpy(state->gpu_regs, gpuFreeze.ulControl, sizeof(state->gpu_regs));
    memcpy(state->gpu_ex_regs, gpuFreeze.ulControl + 0xe0, sizeof(state->gpu_ex_regs));
    if (vramPtr) {
        memcpy(state->gpu_vram, vramPtr, GPU_VRAM_SIZE);
    }

    // Coprocessor 2 (GTE)
    memcpy(state->cp2_data, psxRegs.CP2.CP2D.r, sizeof(state->cp2_data));
    memcpy(state->cp2_ctrl, psxRegs.CP2.CP2C.r, sizeof(state->cp2_ctrl));

    // CPU timing state
    state->cpu_code           = psxRegs.code;
    state->cpu_cycle          = psxRegs.cycle;
    state->cpu_interrupt      = psxRegs.interrupt;
    memcpy(state->cpu_intCycle, psxRegs.intCycle, sizeof(state->cpu_intCycle));
    memcpy(state->cpu_event_cycles, psxRegs.event_cycles, sizeof(state->cpu_event_cycles));
    state->cpu_psxNextCounter  = psxRegs.psxNextCounter;
    state->cpu_psxNextsCounter = psxRegs.psxNextsCounter;
    state->cpu_next_interupt   = psxRegs.next_interupt;
    state->cpu_gteBusyCycle    = psxRegs.gteBusyCycle;
    state->cpu_muldivBusyCycle = psxRegs.muldivBusyCycle;
    state->cpu_biuReg          = psxRegs.biuReg;
    state->cpu_biosBranchCheck = psxRegs.biosBranchCheck;
    state->cpu_gpuIdleAfter    = psxRegs.gpuIdleAfter;

    // SPU state via SPU_freeze
    state->spu_data_size = 0;
    if (SPU_freeze) {
        unsigned short *spuram = NULL;
        SPUFreeze_t spu_hdr_local;
        memset(&spu_hdr_local, 0, sizeof(spu_hdr_local));
        SPU_freeze(1, &spu_hdr_local, &spuram, state->spu_part2, psxRegs.cycle);
        memcpy(state->spu_hdr_data, &spu_hdr_local, sizeof(spu_hdr_local));
        state->spu_data_size = spu_hdr_local.Size;
        if (spuram) {
            memcpy(state->spu_ram, spuram, SPU_RAM_SIZE);
        }
    }

    // PSX memory
    if (psxRegs.ptrs.psxM) memcpy(state->psx_ram, psxRegs.ptrs.psxM, sizeof(state->psx_ram));
    if (psxRegs.ptrs.psxR) memcpy(state->bios_rom, psxRegs.ptrs.psxR, sizeof(state->bios_rom));
    if (psxRegs.ptrs.psxH) memcpy(state->hw_regs, psxRegs.ptrs.psxH, sizeof(state->hw_regs));

    // Game ID
    strncpy(state->game_id, CdromId, sizeof(state->game_id) - 1);

    // Extra state (SIO, CDROM, counters, MDEC, dynarec, pad)
    *extraSizePtr = save_extra_state_to_buffer(extraData, EXTRA_STATE_BUF_SIZE);

    gRewindBuffer.commit();
}

// --- Rewind: restore state from snapshot --------------------------------------
// Restores emulator state from the most recent snapshot in the rewind buffer.
// Returns true if a snapshot was restored, false if the buffer is empty.
static bool restoreRewindSnapshot() {
    if (!sInitialized || !sGameLoaded) return false;
    if (gRewindBuffer.empty()) return false;

    uint8_t* slot = gRewindBuffer.topSlot();
    if (!slot) return false;

    const CoreState* state = reinterpret_cast<const CoreState*>(slot);
    const uint32_t* extraSizePtr = reinterpret_cast<const uint32_t*>(slot + sizeof(CoreState));
    const uint8_t* extraData = slot + sizeof(CoreState) + 4;

    // Notify CPU core to sync cached state before we overwrite psxRegs
    safeCpuNotify(R3000ACPU_NOTIFY_BEFORE_SAVE, NULL);

    // CPU: GPRs, PC, LO, HI
    memcpy(psxRegs.GPR.r, state->regs, sizeof(state->regs));
    psxRegs.pc = state->pc;
    psxRegs.GPR.n.lo = state->lo;
    psxRegs.GPR.n.hi = state->hi;

    // Coprocessor 0
    memcpy(psxRegs.CP0.r, state->cp0_regs, sizeof(state->cp0_regs));

    // PSX memory
    if (psxRegs.ptrs.psxM) memcpy(psxRegs.ptrs.psxM, state->psx_ram, sizeof(state->psx_ram));
    if (psxRegs.ptrs.psxR) memcpy(psxRegs.ptrs.psxR, state->bios_rom, sizeof(state->bios_rom));
    if (psxRegs.ptrs.psxH) memcpy(psxRegs.ptrs.psxH, state->hw_regs, sizeof(state->hw_regs));

    // GPU: restore via GPUfreeze
    uint16_t* vramPtr = nullptr;
    GPUFreeze_t gpuFreeze;
    memset(&gpuFreeze, 0, sizeof(gpuFreeze));
    gpuFreeze.ulFreezeVersion = 1;
    memcpy(gpuFreeze.ulControl, state->gpu_regs, sizeof(state->gpu_regs));
    memcpy(gpuFreeze.ulControl + 0xe0, state->gpu_ex_regs, sizeof(state->gpu_ex_regs));
    gpuFreeze.ulStatus = state->gpu_regs[0];
    gpu_freeze_load(&gpuFreeze, &vramPtr);
    if (vramPtr) {
        memcpy(vramPtr, state->gpu_vram, GPU_VRAM_SIZE);
    }
    gpu_post_load_sync();

    // Coprocessor 2 (GTE)
    memcpy(psxRegs.CP2.CP2D.r, state->cp2_data, sizeof(state->cp2_data));
    memcpy(psxRegs.CP2.CP2C.r, state->cp2_ctrl, sizeof(state->cp2_ctrl));

    // CPU timing state
    psxRegs.code           = state->cpu_code;
    psxRegs.cycle          = state->cpu_cycle;
    psxRegs.interrupt      = state->cpu_interrupt;
    memcpy(psxRegs.intCycle, state->cpu_intCycle, sizeof(state->cpu_intCycle));
    memcpy(psxRegs.event_cycles, state->cpu_event_cycles, sizeof(state->cpu_event_cycles));
    psxRegs.psxNextCounter  = state->cpu_psxNextCounter;
    psxRegs.psxNextsCounter = state->cpu_psxNextsCounter;
    psxRegs.next_interupt   = state->cpu_next_interupt;
    psxRegs.gteBusyCycle    = state->cpu_gteBusyCycle;
    psxRegs.muldivBusyCycle = state->cpu_muldivBusyCycle;
    psxRegs.biuReg          = state->cpu_biuReg;
    psxRegs.biosBranchCheck = state->cpu_biosBranchCheck;
    psxRegs.gpuIdleAfter    = state->cpu_gpuIdleAfter;

    // Clean up runtime flags
    psxRegs.branching = 0;
    psxRegs.cpuInRecursion = 0;
    psxRegs.gpuIdleAfter = psxRegs.cycle - 1;
    HW_GPU_STATUS &= ~PSXGPU_nBUSY;

    // SPU state
    if (SPU_freeze && state->spu_data_size > 0) {
        unsigned short *spuram = NULL;
        SPU_freeze(0, NULL, &spuram, NULL, 0);
        if (spuram) {
            memcpy(spuram, state->spu_ram, SPU_RAM_SIZE);
        }
        SPUFreeze_t spu_hdr_local;
        memcpy(&spu_hdr_local, state->spu_hdr_data, sizeof(spu_hdr_local));
        uint32_t part2_size = state->spu_data_size - SPU_HEADER_SIZE - SPU_RAM_SIZE;
        if ((int32_t)part2_size > 0 && part2_size <= SPU_PART2_MAX) {
            SPU_freeze(0, &spu_hdr_local, &spuram, const_cast<uint8_t*>(state->spu_part2), psxRegs.cycle);
        } else {
            SPU_freeze(0, &spu_hdr_local, &spuram, NULL, psxRegs.cycle);
        }
    }

    // Extra state (SIO, CDROM, counters, MDEC, dynarec, pad)
    if (*extraSizePtr > 0) {
        load_extra_state_from_buffer(extraData, *extraSizePtr);
    } else {
        ndrc_clear_full();
    }

    events_restore();
        // Notify CPU core that RAM/state was replaced.
        // Use R3000ACPU_NOTIFY_AFTER_LOAD (not _AFTER_LOAD_STATE): the latter relies
        // on ndrc_freeze() having already invalidated the ari64 translation cache,
        // which is only valid if the cache has not wrapped since the snapshot was
        // captured. Over a long session the translation cache wraps, leaving the
        // saved block list pointing at overwritten generated code -> SIGSEGV in
        // retro_run. _AFTER_LOAD runs ari64_reset() which invalidates all translated
        // blocks so they are recompiled cleanly from the restored RAM.
        safeCpuNotify(R3000ACPU_NOTIFY_AFTER_LOAD, NULL);

    // Pop after successful restore
    gRewindBuffer.pop();

    // Clear audio ring buffer to prevent stale audio
    audioRingBuffer.clear();

    return true;
}

// --- JNI: Rewind controls ----------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetRewindEnabled(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jboolean enabled) {
    sRewindEnabled.store(enabled);
    if (!enabled) {
        // Defer the clear to the emulation thread to avoid racing with
        // captureRewindSnapshot()/restoreRewindSnapshot().
        sRewindClearPending.store(true, std::memory_order_release);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetRewindInterval(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint frames) {
    if (frames < 1) frames = 1;
    if (frames > 300) frames = 300;
    sRewindInterval.store(frames, std::memory_order_relaxed);
    sRewindFrameCounter.store(0, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeRewindStep(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    bool result = restoreRewindSnapshot();
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeGetRewindBufferCount(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return gRewindBuffer.count();
}

// --- JNI: Settings stubs ------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetAudioEnabled(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jboolean enabled) {
    gAudioEnabled = enabled;
    // Clear the ring buffer so stale audio doesn't play when unmuting
    if (!enabled) {
        audioRingBuffer.clear();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeClearAudioRingBuffer(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    audioRingBuffer.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetFastForwardSpeed(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint multiplier) {
    if (multiplier < 1) multiplier = 1;
    if (multiplier > 32) multiplier = 32;
    sFastForwardMultiplier.store(multiplier);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeGetFastForwardSpeed(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return sFastForwardMultiplier.load();
}

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetFastForwardEnabled(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jboolean enabled) {
    sFastForwardEnabled.store(enabled);
}

// --- JNI: Slow-motion controls -----------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetSlowMotionEnabled(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jboolean enabled) {
    sSlowMotionEnabled.store(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetSlowMotionDivisor(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint divisor) {
    if (divisor < 1) divisor = 1;
    if (divisor > 4) divisor = 4;
    sSlowMotionDivisor.store(divisor);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeGetSlowMotionDivisor(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return sSlowMotionDivisor.load();
}

// --- JNI: nativeSetCoreOption -------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeSetCoreOption(
        JNIEnv* env,
        jclass /* clazz */,
        jstring key,
        jstring value) {
    if (key == nullptr || value == nullptr) return JNI_FALSE;
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    bool ok = (k && v) && setCoreOption(k, v);
    if (k) env->ReleaseStringUTFChars(key, k);
    if (v) env->ReleaseStringUTFChars(value, v);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// --- JNI: nativeShutdown ------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeShutdown(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    ATRACE_FN();
    LOGI("nativeShutdown called");

    if (sInitialized) {
        retro_deinit();
        sInitialized = false;
        sGameLoaded = false;
    }

    gRewindBuffer.deinit();
    // Closes the stream and clears the ring buffer. After this no stream exists
    // until the emulation thread opens one in nativeInit() again � a settings
    // change arriving now only updates gRequestedBufferFrames.
    audioStreamClose();
    sCurrentBufferFrames = 0;
    LOGI("nativeShutdown complete");
}

// --- JNI: nativeGetResolutionScale --------------------------------------------

extern "C" JNIEXPORT jfloat JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeGetResolutionScale(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return 1.0f;
}

// --- JNI: nativeGetFps --------------------------------------------------------

extern "C" JNIEXPORT jfloat JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeGetFps(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    // Config.PsxType: 0 = NTSC (60fps), 1 = PAL (50fps)
    return Config.PsxType ? 50.0f : 60.0f;
}

// --- JNI: nativeEmulateFrame --------------------------------------------------

static const int AUDIO_SAMPLE_COUNT = 1024;

extern "C" JNIEXPORT jint JNICALL
Java_com_tansoft_ps1emulator_core_EmulatorBridge_nativeEmulateFrame(
        JNIEnv* env,
        jclass /* clazz */,
        jint buttons,
        jfloat analogX,
        jfloat analogY,
        jfloat rightAnalogX,
        jfloat rightAnalogY,
        jobject framebufferBuffer,
        jobject audioBuffer) {
    ATRACE_FN();
    env->PushLocalFrame(4);

    static bool prioritySet = false;
    if (!prioritySet) {
        raiseEmulationThreadPriority();
        prioritySet = true;
    }

    if (!sInitialized) {
        LOGE("nativeEmulateFrame: core not initialized");
        env->PopLocalFrame(nullptr);
        return 0;
    }

    if (!sGameLoaded) {
        LOGE("nativeEmulateFrame: no game loaded");
        env->PopLocalFrame(nullptr);
        return 0;
    }

    // Audio stream lifecycle belongs to this thread only (see
    // applyPendingAudioConfig). Two relaxed atomic loads per frame.
    applyPendingAudioConfig();

    sInputButtons = buttons;
    sAnalogX = analogX;
    sAnalogY = analogY;
    sRightAnalogX = rightAnalogX;
    sRightAnalogY = rightAnalogY;

    // Run one or more frames of emulation via libretro
    // When fast-forward is enabled, run N frames per call (N = multiplier)
    int framesToRun = sFastForwardEnabled.load() ? sFastForwardMultiplier.load() : 1;
    sFbDirty = false;
    {
        ScopedTrace _trace("Emulation: retro_run");
        for (int i = 0; i < framesToRun; i++) {
            retro_run();
        }
    }

    // Rewind: process deferred clear request from UI thread (safe here, no race)
    if (sRewindClearPending.exchange(false, std::memory_order_acquire)) {
        gRewindBuffer.clear();
        sRewindFrameCounter = 0;
    }

    // Rewind: capture snapshot at configured interval
    if (sRewindEnabled.load() && !sFastForwardEnabled.load()) {
        sRewindFrameCounter++;
        if (sRewindFrameCounter >= sRewindInterval) {
            sRewindFrameCounter = 0;
            captureRewindSnapshot();
        }
    }

    // Clear Java audio buffer (audio goes through ring buffer ? Oboe)
    auto* audio = static_cast<int16_t*>(env->GetDirectBufferAddress(audioBuffer));
    if (audio) {
        memset(audio, 0, AUDIO_SAMPLE_COUNT * sizeof(int16_t));
    }

    // The core duped this frame: nothing was drawn, so leave the previous image
    // on screen and skip both the buffer swap and the GL upload.
    if (!sFbDirty) { env->PopLocalFrame(nullptr); return 0; }

    auto* fb = static_cast<uint32_t*>(env->GetDirectBufferAddress(framebufferBuffer));
    if (!fb) { env->PopLocalFrame(nullptr); return 0; }

    unsigned w = (sFbWidth  > FB_MAX_WIDTH)  ? FB_MAX_WIDTH  : sFbWidth;
    unsigned h = (sFbHeight > FB_MAX_HEIGHT) ? FB_MAX_HEIGHT : sFbHeight;
    if (w == 0 || h == 0) { env->PopLocalFrame(nullptr); return 0; }

    // Validate the Java-side buffer is large enough for the copy
    jlong fbCapacity = env->GetDirectBufferCapacity(framebufferBuffer);
    if (fbCapacity < 0 || static_cast<size_t>(fbCapacity) < static_cast<size_t>(w) * h * sizeof(uint32_t)) {
        LOGE("nativeEmulateFrame: framebuffer too small (%lld < %zu)", (long long)fbCapacity,
             static_cast<size_t>(w) * h * sizeof(uint32_t));
        env->PopLocalFrame(nullptr);
        return 0;
    }

    // Straight copy — no colour conversion. The core already gave us XRGB8888;
    // the byte order difference against GL_RGBA is corrected for free by a
    // .bgra swizzle in the fragment shader.
    //
    // Rows are packed tightly at `w` (not at a fixed stride) because GLES 2.0
    // has no GL_UNPACK_ROW_LENGTH — glTexSubImage2D can only read a tightly
    // packed w*h block.
    {
        ScopedTrace _trace("Emulation: framebuffer copy");
        if (w == sFbWidth) {
            memcpy(fb, sFramebuffer, static_cast<size_t>(w) * h * sizeof(uint32_t));
        } else {
            for (unsigned y = 0; y < h; y++) {
                memcpy(fb + static_cast<size_t>(y) * w,
                       sFramebuffer + static_cast<size_t>(y) * sFbWidth,
                       static_cast<size_t>(w) * sizeof(uint32_t));
            }
        }
    }

    jint result = static_cast<jint>((w << 16) | h);
    env->PopLocalFrame(nullptr);
    return result;
}
        }
    }

    return static_cast<jint>((w << 16) | h);
}
