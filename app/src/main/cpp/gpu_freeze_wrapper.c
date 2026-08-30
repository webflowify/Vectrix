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

#include "gpu.h"

// GPUfreeze is implemented in plugins/gpulib/gpu.c — declare it directly
// to avoid including plugins.h (which typedefs GPUfreeze as a function pointer
// type, breaking C++ compilation).
extern long GPUfreeze(uint32_t type, GPUFreeze_t *freeze, uint16_t **vram_ptr);

// Include gpulib GPU header for struct psx_gpu and renderer functions
// (C file — no C++ typedef conflict issues)
// ROOT_DIR (core/) is in the include path, so this resolves correctly
#include "plugins/gpulib/gpu.h"

long gpu_freeze_save(GPUFreeze_t *freeze, uint16_t **vram_ptr) {
    return GPUfreeze(1, freeze, vram_ptr);
}

long gpu_freeze_load(GPUFreeze_t *freeze, uint16_t **vram_ptr) {
    // FIX: gpu.regs is NOT restored by GPUfreeze load (the memcpy is commented
    // out in gpu.c). GPUwriteStatus has an early-return:
    //   if (cmd > 1 && cmd != 5 && gpu.regs[cmd] == data) return;
    // If gpu.regs already matches the saved values (common during FMV where
    // display mode doesn't change between frames), ALL GP1 side effects are
    // skipped — update_width(), update_height(), renderer_notify_screen_change()
    // never fire, and gpu.screen geometry is never reconstructed.
    //
    // Fix: temporarily set gpu.regs to 0xFF to force all GP1 commands to fire
    // their side effects, then restore gpu.regs from freeze data afterward.

    // Log pre-load state for debugging
    SysPrintf("gpu_freeze_load PRE: regs[5]=0x%08x regs[8]=0x%08x "
              "screen.hres=%d screen.vres=%d status=0x%08x\n",
              gpu.regs[5], gpu.regs[8],
              gpu.screen.hres, gpu.screen.vres, gpu.status);

    // Overwrite gpu.regs with values that won't match any saved GP1 commands,
    // so the early-return in GPUwriteStatus never triggers.
    memset(gpu.regs, 0xFF, sizeof(gpu.regs));

    long result = GPUfreeze(0, freeze, vram_ptr);

    // Restore gpu.regs from freeze data. GPUwriteStatus already set regs[2..8]
    // during GP1 replay, but regs[0] and regs[1] were not replayed. Full
    // restore ensures consistency with the saved state.
    memcpy(gpu.regs, freeze->ulControl, sizeof(gpu.regs));

    // Log post-load state
    SysPrintf("gpu_freeze_load POST: regs[5]=0x%08x regs[8]=0x%08x "
              "screen.hres=%d screen.vres=%d status=0x%08x\n",
              gpu.regs[5], gpu.regs[8],
              gpu.screen.hres, gpu.screen.vres, gpu.status);

    return result;
}

// Called after VRAM is memcpy'd into gpu.vram following a state load.
// GPUfreeze's renderer_update_caches() runs BEFORE VRAM is restored,
// so the NEON renderer's texture cache is built from stale data.
// This function rebuilds caches from the correct VRAM and forces a
// display update on the next vblank.
void gpu_post_load_sync(void) {
    // Log GPU screen state for debugging FMV corruption issues
    SysPrintf("gpu_post_load_sync: screen hres=%d vres=%d x=%d y=%d w=%d h=%d "
              "src_x=%d src_y=%d status=0x%08x dims_changed=%d\n",
              gpu.screen.hres, gpu.screen.vres, gpu.screen.x, gpu.screen.y,
              gpu.screen.w, gpu.screen.h, gpu.screen.src_x, gpu.screen.src_y,
              gpu.status, gpu.state.dims_changed);

    // Rebuild renderer texture caches from the now-correct VRAM data.
    // state_changed=1 forces a full cache rebuild.
    renderer_update_caches(0, 0, 1024, 512, 1);

    // Force display update: without this, GPUupdateLace() returns early
    // because fb_dirty_display_area is 0 after GPUfreeze load, causing
    // the screen to stay black until the game writes to GPU register 5.
    gpu.state.fb_dirty_display_area = 1;
    gpu.state.fb_dirty = 1;
    gpu.state.dims_changed = 1;

    // Notify renderer of the display area so it picks up restored screen coords
    renderer_notify_screen_change(&gpu.screen);
}
