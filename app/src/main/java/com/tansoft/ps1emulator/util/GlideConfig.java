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
package com.tansoft.ps1emulator.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Centralized Glide configuration optimized for PS1 Emulator.
 *
 * This module configures Glide with memory-conscious settings that balance
 * image loading performance with the emulator's high memory demands:
 * - Reduced memory cache (emulator needs RAM for PS1 core)
 * - Efficient bitmap pool for recycling
 * - Disk cache for thumbnails and asset icons
 * - RGB_565 format where possible (50% less memory than ARGB_8888)
 */
@GlideModule
public class GlideConfig extends AppGlideModule {

    private static final String TAG = "GlideConfig";

    // Emulator uses significant memory for PS1 core, framebuffers, and audio.
    // Keep Glide's memory footprint conservative to avoid OOM during gameplay.
    private static final float MEMORY_CACHE_MULTIPLIER = 0.12f;  // 12% of available memory
    private static final float BITMAP_POOL_MULTIPLIER = 0.10f;    // 10% of available memory
    private static final long DISK_CACHE_SIZE = 50L * 1024 * 1024; // 50 MB disk cache

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        super.applyOptions(context, builder);

        // Calculate memory-appropriate cache sizes
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(2)  // Default is 2, but we'll override below
                .build();

        int memoryCacheSize = (int) (calculator.getMemoryCacheSize() * MEMORY_CACHE_MULTIPLIER);
        int bitmapPoolSize = (int) (calculator.getBitmapPoolSize() * BITMAP_POOL_MULTIPLIER);

        // Apply conservative memory cache (emulator needs headroom for PS1 core)
        builder.setMemoryCache(new LruResourceCache(memoryCacheSize));

        // Bitmap pool for recycling decoded bitmaps
        builder.setBitmapPool(new LruBitmapPool(bitmapPoolSize));

        // Disk cache for thumbnails and asset icons
        builder.setDiskCache(
                new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));

        // Default options: prefer RGB_565 for non-transparent images (50% less memory)
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .disallowHardwareConfig()  // Prevents hardware bitmap issues
        );

        Log.d(TAG, "Glide configured: memoryCache=" + (memoryCacheSize / 1024) + "KB" +
                ", bitmapPool=" + (bitmapPoolSize / 1024) + "KB" +
                ", diskCache=" + (DISK_CACHE_SIZE / 1024 / 1024) + "MB");
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide,
                                   @NonNull Registry registry) {
        super.registerComponents(context, glide, registry);
        // No custom components needed - using Glide's built-in decoders
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // Disable manifest parsing for faster initialization
        // We configure everything programmatically in applyOptions()
        return false;
    }
}
