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
#include <cstdlib>
#include <cstring>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

// Capture framebuffer from an XRGB8888 source buffer.
// src_fb: source pixels in XRGB8888 format (0x00RRGGBB)
// src_w, src_h: source dimensions
// Returns allocated RGBA buffer (caller must free), sets out_width/out_height.
static inline uint8_t* capture_framebuffer_from(const uint32_t* src_fb,
                                                 uint32_t src_w, uint32_t src_h,
                                                 uint32_t* out_width, uint32_t* out_height) {
    if (!src_fb || src_w == 0 || src_h == 0) return nullptr;

    const uint32_t w = src_w;
    const uint32_t h = src_h;
    size_t size = (size_t)w * h * 4;
    uint8_t* pixels = (uint8_t*)malloc(size);
    if (!pixels) return nullptr;

    for (uint32_t y = 0; y < h; y++) {
        for (uint32_t x = 0; x < w; x++) {
            uint32_t src_pixel = src_fb[y * w + x];
            size_t idx = (size_t)(y * w + x) * 4;
            // XRGB8888 (0x00RRGGBB) -> RGBA
            pixels[idx + 0] = (src_pixel >> 16) & 0xFF; // R
            pixels[idx + 1] = (src_pixel >> 8) & 0xFF;  // G
            pixels[idx + 2] = src_pixel & 0xFF;          // B
            pixels[idx + 3] = 255;                        // A
        }
    }

    *out_width = w;
    *out_height = h;
    return pixels;
}

// Write RGBA pixels to a PNG file.
// Returns 0 on success.
static inline int write_png(const char* path, const uint8_t* rgba,
                            uint32_t w, uint32_t h) {
    return stbi_write_png(path, (int)w, (int)h, 4, rgba, (int)w * 4);
}