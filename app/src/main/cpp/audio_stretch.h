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
#pragma once

#include <cstdint>
#include <vector>
#include <atomic>
#include "ring_buffer.h"

// --- SoundTouch (optional time-stretch) ------------------------------------
// LICENSE WARNING — read before enabling AUDIO_USE_SOUNDTOUCH:
//
// SoundTouch is licensed under the GNU Lesser General Public License v2.1
// (LGPLv2.1).  Statically linking an LGPL library into a non-LGPL binary
// requires one of the following:
//   (a) The user can relink the binary with a different version of SoundTouch
//       (requires shipping object files or position-independent static library)
//   (b) OR, link SoundTouch as a *separate shared library* (.so), which keeps
//       it LGPL-independent.
//
// The CURRENT Android build does NOT define AUDIO_USE_SOUNDTOUCH, so
// SoundTouch is NOT compiled in.  If you re-enable it you MUST:
//   1. Add SoundTouch to THIRD-PARTY-NOTICES.md and third_party_licenses.txt.
//   2. Link it as a separate libsoundtouch.so (not statically merged).
//   3. Provide the LGPLv2.1 license text to users.
// Failure to do this creates an LGPL violation in the distributed APK.
// ----------------------------------------------------------------------------
// Set by CMake when the SoundTouch submodule is present (see CMakeLists.txt).
// When absent, TimeStretch falls back to passthrough so the app still builds.
#ifdef AUDIO_USE_SOUNDTOUCH
#include <SoundTouch.h>
#endif

// How produced game audio is reconciled with real-time playback during
// fast-forward / slow-motion.
enum class StretchMode {
    Off = 0,          // passthrough ring buffer (legacy behavior: FF overruns, slow-mo drops)
    TimeStretch = 1,  // pitch-preserving time-stretch via SoundTouch (recommended, default)
    Mute = 2          // silence (classic "mute during FF/slow-mo" behavior)
};

// Sits between the SPU (producer, our_audio_batch) and the Oboe callback
// (consumer, onAudioReady). Translates the emulation's variable production
// rate into a steady real-time stream without muting or pitch-shifting.
//
//   push(): feed produced game audio (interleaved int16, `frames` frames).
//   pop() : pull real-time audio for playback; returns frames actually filled.
//           The caller MUST zero-fill the remainder (underrun handling).
//
// Rate = emulation speed: 1.0 normal, >1 fast-forward, <1 slow-motion.
//
// Threading: the producer pushes into a lock-free ring buffer; the Oboe
// callback (audio thread) is the ONLY thread that touches SoundTouch itself,
// pumping ring-buffer samples into it each pop(). SoundTouch is not safe for
// concurrent put/receive across threads, so this serialization is required.
class AudioStretch {
public:
    AudioStretch();

    // (Re)configure. Clears all internal buffers.
    void init(int sampleRate, int channels);

    void setMode(StretchMode m);
    StretchMode mode() const { return mode_.load(); }

    // Set emulation speed. A change triggers a reset to drop any
    // partially-stretched tail (mirrors DuckStation's EmptyStretchBuffers()).
    void setRate(float rate);
    float rate() const { return rate_; }

    // Feed `frames` frames of interleaved int16 audio produced by the core.
    void push(const int16_t* in, size_t frames);

    // Pull up to `frames` frames for real-time playback. Returns frames filled.
    size_t pop(int16_t* out, size_t frames);

    // Drop all buffered audio (stream recreate / pause / speed change).
    void clear();

    // Pre-fill output with silence so the first callbacks don't underrun.
    void prefillSilence(size_t frames);

    // Source frames queued ahead of the stretcher (diagnostics only).
    size_t pendingFrames() const;

private:
    static constexpr size_t kSrcSamples = 1u << 17; // producer->stretcher ring

    void pushPassthrough(const int16_t* in, size_t frames);
    size_t popPassthrough(int16_t* out, size_t frames);
    void pushTimeStretch(const int16_t* in, size_t frames);
    size_t popTimeStretch(int16_t* out, size_t frames);

    int sampleRate_ = 44100;
    int channels_   = 2;
    float rate_     = 1.0f;
    std::atomic<StretchMode> mode_{StretchMode::TimeStretch};

    // Passthrough path
    RingBuffer<int16_t, 32768> pbuf_;

#ifdef AUDIO_USE_SOUNDTOUCH
    // SoundTouch instance (audio-thread only).
    soundtouch::SoundTouch st_;
    // Producer -> SoundTouch hand-off (lock-free).
    RingBuffer<int16_t, kSrcSamples> srcBuf_;
    std::vector<float>  scratch_;   // float workspace for put/receive
    std::vector<int16_t> pumpTmp_;   // int16 workspace for pumping
    // SoundTouch is not thread-safe; all st_ mutations are deferred and applied
    // on the audio thread (inside popTimeStretch) using these flags.
    std::atomic<bool> pendingRateChange_{false};
    std::atomic<bool> pendingStClear_{false};
#endif
};
