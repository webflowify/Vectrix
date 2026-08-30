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
#include <cstdio>
#include <cstring>
#include <vector>

static constexpr uint32_t SAVE_STATE_MAGIC = 0x50535354; // "PSST"
static constexpr uint32_t SAVE_STATE_VERSION = 5;

// GPU VRAM: 1024 x 512 pixels, 16-bit each
static constexpr uint32_t GPU_VRAM_SIZE = 1024 * 512 * 2;

// SPU data sizes (matches SPUFreeze_t / SPUFREEZE_F2_MAX_SIZE from psemu_plugin_defs.h)
static constexpr uint32_t SPU_HEADER_SIZE = 528;   // sizeof(SPUFreeze_t): 8+4+4+0x200
static constexpr uint32_t SPU_RAM_SIZE = 512 * 1024;
static constexpr uint32_t SPU_PART2_MAX = 49152;   // SPUFREEZE_F2_MAX_SIZE = 0xC000

#pragma pack(push, 1)
struct CoreState {
    // CPU
    uint32_t regs[32];       // General purpose registers
    uint32_t pc;             // Program counter
    uint32_t lo, hi;         // Multiply/divide registers

    // Coprocessor 0 (system control)
    uint32_t cp0_regs[32];

    // GPU
    uint32_t gpu_regs[16];
    uint32_t fb_width;
    uint32_t fb_height;

    // SPU (placeholder)
    uint32_t spu_regs[32];

    // CD-ROM (placeholder)
    uint32_t cdrom_pos;
    uint32_t cdrom_status;

    // PSX memory
    uint8_t psx_ram[2 * 1024 * 1024];     // 2 MB main RAM
    uint8_t bios_rom[512 * 1024];          // 512 KB BIOS ROM
    uint8_t hw_regs[64 * 1024];            // 64 KB hardware registers

    // Memory card slot state
    uint8_t memcard_data[128 * 1024]; // 128 KB card image

    // Tag
    char game_id[16];
    char timestamp[32];

    // v3 additions: GPU extended registers and VRAM (appended at end for backward compat)
    uint32_t gpu_ex_regs[8];     // GPU extended registers (ex_regs[0-7])
    uint8_t gpu_vram[GPU_VRAM_SIZE]; // GPU VRAM (1 MB)

    // v4 additions: critical CPU state missing from v3 causes black textures & hangs
    uint32_t cp2_data[32];       // GTE data registers (COP2 data)
    uint32_t cp2_ctrl[32];       // GTE control registers (COP2 control)
    uint32_t cpu_code;           // Current instruction word
    uint32_t cpu_cycle;          // CPU cycle counter
    uint32_t cpu_interrupt;      // Pending interrupt mask
    uint32_t cpu_intCycle[40];   // Interrupt cycle timing (20 pairs of sCycle,cycle)
    uint32_t cpu_event_cycles[20]; // Event target cycles
    uint32_t cpu_psxNextCounter;
    uint32_t cpu_psxNextsCounter;
    uint32_t cpu_next_interupt;
    uint32_t cpu_gteBusyCycle;
    uint32_t cpu_muldivBusyCycle;
    uint32_t cpu_biuReg;
    uint32_t cpu_biosBranchCheck;
    uint32_t cpu_gpuIdleAfter;
    // v4: SPU state (dfsound plugin freeze data)
    uint8_t spu_hdr_data[SPU_HEADER_SIZE]; // SPUFreeze_t structure
    uint32_t spu_data_size;     // Total SPU data size (hdr + ram + part2)
    uint8_t spu_ram[SPU_RAM_SIZE]; // SPU RAM (512 KB)
    uint8_t spu_part2[SPU_PART2_MAX]; // SPU channel state
};
#pragma pack(pop)

static constexpr uint32_t CORE_SIZE_V1 = 4*32 + 4 + 4 + 4 + 4*32 + 4*16 + 4 + 4 + 4*32 + 4 + 4 + 128*1024 + 16 + 32;
static constexpr uint32_t CORE_SIZE_V2 = 4*32 + 4 + 4 + 4 + 4*32 + 4*16 + 4 + 4 + 4*32 + 4 + 4
              + 2*1024*1024 + 512*1024 + 64*1024 + 128*1024 + 16 + 32;

static constexpr uint32_t CORE_SIZE_V3 = CORE_SIZE_V2 + 8*4 + GPU_VRAM_SIZE;

static constexpr uint32_t CORE_SIZE_V4 = CORE_SIZE_V3
    + 32*4 + 32*4          // cp2_data, cp2_ctrl (GTE registers)
    + 4*11                 // cpu_code, cpu_cycle, cpu_interrupt,
                          // cpu_psxNextCounter, cpu_psxNextsCounter, cpu_next_interupt,
                          // cpu_gteBusyCycle, cpu_muldivBusyCycle, cpu_biuReg,
                          // cpu_biosBranchCheck, cpu_gpuIdleAfter
    + 40*4                 // cpu_intCycle (20 pairs of sCycle+cycle)
    + 20*4                 // cpu_event_cycles
    + SPU_HEADER_SIZE + 4  // spu_hdr_data, spu_data_size
    + SPU_RAM_SIZE         // spu_ram
    + SPU_PART2_MAX;       // spu_part2

static_assert(sizeof(CoreState) == CORE_SIZE_V4,
              "CoreState size mismatch -- check packing");

static inline int core_save_state(const char* path, const CoreState* state,
                                  const uint8_t* screenshot_rgba,
                                  uint32_t screenshot_w, uint32_t screenshot_h) {
    FILE* f = fopen(path, "wb");
    if (!f) return -1;

    uint32_t core_size = sizeof(CoreState);

    fwrite(&SAVE_STATE_MAGIC, 4, 1, f);
    fwrite(&SAVE_STATE_VERSION, 4, 1, f);
    fwrite(&core_size, 4, 1, f);
    fwrite(state, 1, core_size, f);

    fwrite(&screenshot_w, 4, 1, f);
    fwrite(&screenshot_h, 4, 1, f);
    if (screenshot_w > 0 && screenshot_h > 0 && screenshot_rgba) {
        fwrite(screenshot_rgba, 1, screenshot_w * screenshot_h * 4, f);
    }

    fclose(f);
    return 0;
}

static inline int core_load_state(const char* path, CoreState* state,
                                  uint8_t** screenshot_rgba_out,
                                  uint32_t* screenshot_w_out,
                                  uint32_t* screenshot_h_out) {
    FILE* f = fopen(path, "rb");
    if (!f) return -1;

    memset(state, 0, sizeof(CoreState));

    uint32_t magic, version, core_size;
    if (fread(&magic, 4, 1, f) != 1 || magic != SAVE_STATE_MAGIC) {
        fclose(f);
        return -2; // Bad magic
    }
    if (fread(&version, 4, 1, f) != 1 || version > SAVE_STATE_VERSION) {
        fclose(f);
        return -3; // Unsupported version
    }
    if (fread(&core_size, 4, 1, f) != 1) {
        fclose(f);
        return -4; // Read error
    }

    // Version 1 saves had a smaller CoreState (no PSX memory regions).
    // Version 2 saves had no GPU VRAM.
    // Version 3 saves include GPU VRAM but no GTE/timing/SPU state.
    // Version 4 saves include full CPU timing, GTE, and SPU state.
    if (version == 1 && core_size == CORE_SIZE_V1) {
        if (fread(state, 1, core_size, f) != core_size) {
            fclose(f);
            return -5; // Truncated file
        }
        // PSX memory regions and GPU VRAM left zeroed (no data in v1 saves)
    } else if (version <= 2 && core_size == CORE_SIZE_V2) {
        size_t read_size = core_size;
        if (fread(state, 1, read_size, f) != read_size) {
            fclose(f);
            return -5;
        }
        // gpu_vram left zeroed (not present in v2 saves)
    } else if (version == 3 && core_size == CORE_SIZE_V3) {
        // v3: read up to gpu_vram, v4 fields (cp2, timing, spu) left zeroed
        if (fread(state, 1, core_size, f) != core_size) {
            fclose(f);
            return -5;
        }
        // v4 fields zeroed — caller must check and handle degraded load
    } else if (core_size == sizeof(CoreState)) {
        if (fread(state, 1, sizeof(CoreState), f) != sizeof(CoreState)) {
            fclose(f);
            return -5;
        }
    } else {
        fclose(f);
        return -4; // Size mismatch
    }

    uint32_t sw = 0, sh = 0;
    fread(&sw, 4, 1, f);
    fread(&sh, 4, 1, f);

    if (sw > 0 && sh > 0 && sw <= 2048 && sh <= 2048) {
        *screenshot_w_out = sw;
        *screenshot_h_out = sh;
        size_t pixel_count = (size_t)sw * sh;
        *screenshot_rgba_out = (uint8_t*)malloc(pixel_count * 4);
        if (*screenshot_rgba_out) {
            fread(*screenshot_rgba_out, 1, pixel_count * 4, f);
        }
    }

    fclose(f);
    return 0;
}
