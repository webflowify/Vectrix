cmake_minimum_required(VERSION 3.22.1)
project("ps1emulatorcore" C CXX ASM)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Directories
set(ROOT_DIR     ${CMAKE_CURRENT_SOURCE_DIR}/core)
set(CORE_DIR     ${ROOT_DIR}/libpcsxcore)
set(SPU_DIR      ${ROOT_DIR}/plugins/dfsound)
set(GPU_DIR      ${ROOT_DIR}/plugins/gpulib)
set(CDR_DIR      ${ROOT_DIR}/plugins/cdrcimg)
set(FRONTEND_DIR ${ROOT_DIR}/frontend)
set(NEON_DIR     ${ROOT_DIR}/plugins/gpu_neon)
set(UNAI_DIR     ${ROOT_DIR}/plugins/gpu_unai)
set(PEOPS_DIR    ${ROOT_DIR}/plugins/dfxvideo)
set(DYNAREC_DIR  ${ROOT_DIR}/libpcsxcore/new_dynarec)
set(DEPS_DIR     ${ROOT_DIR}/deps)
set(LCHDR_DIR    ${DEPS_DIR}/libchdr)
set(LCHDR_LZMA   ${LCHDR_DIR}/deps/lzma-24.05)
set(LCHDR_ZSTD   ${LCHDR_DIR}/deps/zstd-1.5.6/lib)
set(LIBRETRO_COMMON ${DEPS_DIR}/libretro-common)

# Core sources
set(CORE_SOURCES
    ${CORE_DIR}/cdriso.c
    ${CORE_DIR}/cdrom.c
    ${CORE_DIR}/cdrom-async.c
    ${CORE_DIR}/cheat.c
    ${CORE_DIR}/database.c
    ${CORE_DIR}/decode_xa.c
    ${CORE_DIR}/mdec.c
    ${CORE_DIR}/misc.c
    ${CORE_DIR}/pad.c
    ${CORE_DIR}/plugins.c
    ${CORE_DIR}/ppf.c
    ${CORE_DIR}/psxbios.c
    ${CORE_DIR}/psxcommon.c
    ${CORE_DIR}/psxcounters.c
    ${CORE_DIR}/psxdma.c
    ${CORE_DIR}/psxevents.c
    ${CORE_DIR}/psxhw.c
    ${CORE_DIR}/psxinterpreter.c
    ${CORE_DIR}/psxmem.c
    ${CORE_DIR}/r3000a.c
    ${CORE_DIR}/sio.c
    ${CORE_DIR}/spu.c
    ${CORE_DIR}/gpu.c
    ${CORE_DIR}/gte.c
    ${CORE_DIR}/gte_nf.c
    ${CORE_DIR}/gte_divider.c
)

# SPU sources
set(SPU_SOURCES
    ${SPU_DIR}/dma.c
    ${SPU_DIR}/freeze.c
    ${SPU_DIR}/registers.c
    ${SPU_DIR}/spu.c
    ${SPU_DIR}/out.c
    ${SPU_DIR}/nullsnd.c
)

# GPU sources
set(GPU_SOURCES
    ${GPU_DIR}/gpu.c
    ${GPU_DIR}/prim.c
    ${GPU_DIR}/vout_pl.c
    ${GPU_DIR}/gpu_async.c
)

# CD-ROM image reader
set(CDR_SOURCES
    ${CDR_DIR}/cdrcimg.c
)

# Frontend sources
set(FRONTEND_SOURCES
    ${FRONTEND_DIR}/main.c
    ${FRONTEND_DIR}/plugin.c
    ${FRONTEND_DIR}/cspace.c
    ${FRONTEND_DIR}/libretro.c
    ${FRONTEND_DIR}/pcsxr-threads.c

)

# libchdr
set(LCHDR_SOURCES
    ${LCHDR_DIR}/src/libchdr_bitstream.c
    ${LCHDR_DIR}/src/libchdr_cdrom.c
    ${LCHDR_DIR}/src/libchdr_chd.c
    ${LCHDR_DIR}/src/libchdr_flac.c
    ${LCHDR_DIR}/src/libchdr_huffman.c
    ${LCHDR_LZMA}/src/Alloc.c
    ${LCHDR_LZMA}/src/CpuArch.c
    ${LCHDR_LZMA}/src/Delta.c
    ${LCHDR_LZMA}/src/LzFind.c
    ${LCHDR_LZMA}/src/LzmaDec.c
    ${LCHDR_LZMA}/src/LzmaEnc.c
    ${LCHDR_LZMA}/src/Sort.c
    ${LCHDR_ZSTD}/common/entropy_common.c
    ${LCHDR_ZSTD}/common/error_private.c
    ${LCHDR_ZSTD}/common/fse_decompress.c
    ${LCHDR_ZSTD}/common/xxhash.c
    ${LCHDR_ZSTD}/common/zstd_common.c
    ${LCHDR_ZSTD}/decompress/huf_decompress.c
    ${LCHDR_ZSTD}/decompress/zstd_ddict.c
    ${LCHDR_ZSTD}/decompress/zstd_decompress_block.c
    ${LCHDR_ZSTD}/decompress/zstd_decompress.c
)

# libretro-common VFS support
set(LIBRETRO_COMMON_SOURCES
    ${LIBRETRO_COMMON}/compat/compat_posix_string.c
    ${LIBRETRO_COMMON}/compat/fopen_utf8.c
    ${LIBRETRO_COMMON}/compat/compat_strl.c
    ${LIBRETRO_COMMON}/encodings/encoding_utf.c
    ${LIBRETRO_COMMON}/file/file_path.c
    ${LIBRETRO_COMMON}/streams/file_stream.c
    ${LIBRETRO_COMMON}/streams/file_stream_transforms.c
    ${LIBRETRO_COMMON}/string/stdstring.c
    ${LIBRETRO_COMMON}/time/rtime.c
    ${LIBRETRO_COMMON}/vfs/vfs_implementation.c
    ${LIBRETRO_COMMON}/features/features_cpu.c
)

# Dynarec sources (ARM64)
set(DYNAREC_SOURCES
    ${DYNAREC_DIR}/new_dynarec.c
    ${DYNAREC_DIR}/pcsxmem.c
    ${DYNAREC_DIR}/emu_if.c
)

# GPU NEON for ARM64
set(GPU_NEON_SOURCES
    ${NEON_DIR}/psx_gpu_if.c
    ${NEON_DIR}/psx_gpu/psx_gpu_simd.c
)

# Assembly sources (ARM64)
set(ASM_SOURCES
    ${CORE_DIR}/gte_arm64.S
    ${CORE_DIR}/gte_nf_arm64.S
    ${DYNAREC_DIR}/linkage_arm64.S
)

# Collect all sources
set(ALL_SOURCES
    ${CORE_SOURCES}
    ${SPU_SOURCES}
    ${GPU_SOURCES}
    ${CDR_SOURCES}
    ${FRONTEND_SOURCES}
    ${LCHDR_SOURCES}
    ${LIBRETRO_COMMON_SOURCES}
    ${DYNAREC_DIR}/emu_if.c
    jni_bridge.cpp
    audio_oboe.cpp
    gpu_freeze_wrapper.c
)

# Software GPU renderer for non-ARM
set(GPU_UNAI_SOURCES
    ${UNAI_DIR}/gpulib_if.cpp
    ${UNAI_DIR}/old/if.cpp
)
if(NOT CMAKE_SYSTEM_PROCESSOR MATCHES "arm|aarch64")
    list(APPEND ALL_SOURCES ${GPU_UNAI_SOURCES})
endif()

# ARM-specific sources (dynarec, GPU NEON, ARM64 assembly)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm|aarch64")
    # Dynarec sources (full implementation for ARM, requires ARM mode not Thumb)
    list(APPEND ALL_SOURCES
        ${DYNAREC_DIR}/new_dynarec.c
        ${DYNAREC_DIR}/pcsxmem.c
    )
    # GPU NEON sources
    list(APPEND ALL_SOURCES
        ${NEON_DIR}/psx_gpu_if.c
        ${NEON_DIR}/psx_gpu/psx_gpu_simd.c
    )
    # On 32-bit ARM (armeabi-v7a) the ENTIRE core must be built in ARM mode,
    # not Thumb. new_dyna_pcsx_mem_init() (libpcsxcore/new_dynarec/pcsxmem.c)
    # stores handler function pointers and aborts (SIGABRT) if any carry the
    # Thumb LSB (map_item_'s "has LSB set" check). Thumb-compiled handlers in
    # psxcounters.c, cdrom.c, mdec.c, spu.c, sio.c, psxhw.c, gpu.c, ... have
    # LSB=1 and crash on armeabi-v7a. Upstream pcsx_rearmed forces -marm on the
    # whole armv7 build (core/configure / Makefile.libretro: "must disable thumb
    # as recompiler can't handle it"), so mirror that here. Limiting -marm to
    # just the dynarec files is NOT enough: their mapped handlers live in the
    # rest of the core. (Assembly sources are appended later and are already
    # ARM, so they are intentionally left out of this list.)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "^arm")
        set_source_files_properties(${ALL_SOURCES} PROPERTIES COMPILE_FLAGS "-marm")
    endif()

    # NDRC_THREAD (threaded block compilation) is intentionally NOT enabled on
    # aarch64. The new_dynarec shares file-scope state (e.g. literalcount) across
    # the compile thread and the emulation thread, and the code cache is linked
    # from the emulation thread via ndrc_patch_link() concurrently with the
    # compile thread writing new blocks. On aarch64 this races on the shared
    # code cache: ndrc_patch_link() lands on a cache page that the compile thread
    # already reused and overwrote with non-branch code, so set_jump_target()
    # (assem_arm64.c) hits its `abort()` -> SIGABRT inside new_recompile_block.
    # Upstream still treats the threaded recompiler as experimental ("likely
    # break some platforms that I can't test"). Single-threaded compilation is
    # the long-standing, well-tested path on arm64. See also upstream commit
    # "drc: avoid some UB" which moved those shared globals for the same reason.
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
        set(ARM_DEFINITIONS
            HAVE_ARI64
            GPU_NEON
            NEON_BUILD
            SIMD_BUILD
            TEXTURE_CACHE_4BPP
            TEXTURE_CACHE_8BPP
        )
    else()
        set(ARM_DEFINITIONS
            HAVE_ARI64
            NDRC_THREAD
            GPU_NEON
            NEON_BUILD
            SIMD_BUILD
            TEXTURE_CACHE_4BPP
            TEXTURE_CACHE_8BPP
        )
    endif()
endif()

# ARM64-specific: assembly sources + assembler flags
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    set_property(SOURCE ${ASM_SOURCES} APPEND PROPERTY COMPILE_FLAGS "-x assembler-with-cpp")
    list(APPEND ALL_SOURCES ${ASM_SOURCES})
endif()

# ARM32-specific: assembly sources (GTE + dynarec linkage + SPU)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^arm")
    set(ASM_SOURCES_ARM
        ${CORE_DIR}/gte_arm.S
        ${CORE_DIR}/gte_nf_arm.S
        ${DYNAREC_DIR}/linkage_arm.S
        ${SPU_DIR}/arm_utils.S
    )
    # NEON GTE assembly: requires ARM mode + NEON FPU (not Thumb)
    set(ASM_SOURCES_NEON
        ${CORE_DIR}/gte_neon.S
        ${CORE_DIR}/gte_nf_neon.S
    )
    set_property(SOURCE ${ASM_SOURCES_ARM} APPEND PROPERTY COMPILE_FLAGS "-x assembler-with-cpp")
    set_property(SOURCE ${ASM_SOURCES_NEON} APPEND PROPERTY COMPILE_FLAGS "-x assembler-with-cpp -marm -mfpu=neon")
    list(APPEND ALL_SOURCES ${ASM_SOURCES_ARM} ${ASM_SOURCES_NEON})
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED ${ALL_SOURCES})

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ROOT_DIR}
    ${ROOT_DIR}/include
    ${CORE_DIR}
    ${SPU_DIR}
    ${GPU_DIR}
    ${UNAI_DIR}
    ${CDR_DIR}
    ${FRONTEND_DIR}
    ${DEPS_DIR}/crypto
    ${LCHDR_DIR}/include
    ${LCHDR_LZMA}/include
    ${LCHDR_ZSTD}
    ${LIBRETRO_COMMON}/include
)

# Core compile definitions (matching Android.mk)
# DRC_DISABLE: ari64 dynarec is ARM-only, lightrec is not built for x86/x86_64
if(NOT CMAKE_SYSTEM_PROCESSOR MATCHES "arm|aarch64")
    set(DRC_DISABLE_FLAG DRC_DISABLE)
endif()

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    ${DRC_DISABLE_FLAG}
    HAVE_LIBRETRO
    NO_FRONTEND
    ANDROID
    REARMED
    P_HAVE_MMAP=1
    P_HAVE_PTHREAD=1
    P_HAVE_POSIX_MEMALIGN=1
    HAVE_CHD
    Z7_ST
    ZSTD_DISABLE_ASM
    USE_LIBRETRO_VFS
    USE_ASYNC_CDROM
    USE_ASYNC_GPU
    USE_ASYNC_SPU
    HAVE_RTHREADS
    ${ARM_DEFINITIONS}
)

# Optimization. These are appended after CMAKE_C_FLAGS_<CONFIG>, so the -O3 here
# overrides the -O0 that AGP's Debug config would otherwise apply to the whole
# PS1 core (which makes it unplayably slow). Mirrors upstream pcsx_rearmed's
# Makefile.libretro flags for the Android/ARM targets.
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -O3
    -fomit-frame-pointer
    -fno-strict-aliasing
    -ffast-math
    -ffunction-sections
    -fdata-sections
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-pointer-sign
)

# Upstream pins the dynarec to -O2: "less bloat" (core/Makefile:212). -O3 here
# inflates the generated code without measurable gain.
set_source_files_properties(
    ${DYNAREC_DIR}/new_dynarec.c
    PROPERTIES COMPILE_OPTIONS "-O2"
)

# =============================================================================
# SOUNDTOUCH — LICENSE WARNING (AUDIO_USE_SOUNDTOUCH is currently NOT set)
# SoundTouch is licensed under LGPLv2.1.  If you add it to this build you MUST:
#   1. Link it as a SEPARATE shared library (libsoundtouch.so), NOT merged
#      statically into libps1emulatorcore.so — static linking an LGPL library
#      requires the user to be able to relink, which is impractical on Android.
#   2. Add SoundTouch to THIRD-PARTY-NOTICES.md and third_party_licenses.txt.
#   3. Bundle the LGPLv2.1 license text (LGPL-2.1.txt) with the distribution.
# See audio_stretch.h for a full explanation.
# =============================================================================

find_package(oboe REQUIRED CONFIG)


target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,--gc-sections"
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    GLESv2
    z
    oboe::oboe
)
