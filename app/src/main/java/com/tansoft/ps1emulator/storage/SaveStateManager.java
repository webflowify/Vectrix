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
package com.tansoft.ps1emulator.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.tansoft.ps1emulator.core.EmulatorBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SaveStateManager {

    private static final String TAG = "SaveStateManager";
    public static final int MAX_SLOTS = 10;
    private static final String SAVE_DIR = "savestates";
    private static final String FILE_PREFIX = "slot_";
    private static final String FILE_SUFFIX = ".psst";

    public static File getSaveDir(Context context, String gameDiscId) {
        File dir = new File(context.getFilesDir(), SAVE_DIR + File.separator + gameDiscId);
        dir.mkdirs();
        return dir;
    }

    public static File getSaveFile(Context context, String gameDiscId, int slot) {
        return new File(getSaveDir(context, gameDiscId), FILE_PREFIX + slot + FILE_SUFFIX);
    }

    public static File getThumbnailFile(Context context, String gameDiscId, int slot) {
        return new File(getSaveDir(context, gameDiscId), FILE_PREFIX + slot + ".png");
    }

    public static boolean saveState(Context context, String gameDiscId, int slot) {
        File dir = getSaveDir(context, gameDiscId);
        dir.mkdirs();
        File file = getSaveFile(context, gameDiscId, slot);
        File pngFile = getThumbnailFile(context, gameDiscId, slot);
        android.util.Log.d(TAG, "saveState: slot=" + slot
            + " savePath=" + file.getAbsolutePath()
            + " expectedPng=" + pngFile.getAbsolutePath()
            + " gameDiscId=" + gameDiscId);

        // Delete any stale cached PNG before saving so the overwrite always
        // picks up the fresh thumbnail written by nativeSaveState().
        if (pngFile.exists()) {
            boolean deleted = pngFile.delete();
            android.util.Log.d(TAG, "saveState: deleted old PNG=" + pngFile.getName() + " result=" + deleted);
        }
        // Also delete any orphaned .psst.png from the old buggy path
        File orphanPng = new File(dir, FILE_PREFIX + slot + FILE_SUFFIX + ".png");
        if (orphanPng.exists()) {
            boolean deleted = orphanPng.delete();
            android.util.Log.d(TAG, "saveState: deleted orphan PNG=" + orphanPng.getName() + " result=" + deleted);
        }

        int result = EmulatorBridge.nativeSaveState(file.getAbsolutePath());
        android.util.Log.d(TAG, "saveState: nativeSaveState returned " + result
            + " pngNowExists=" + pngFile.exists()
            + " pngSize=" + (pngFile.exists() ? pngFile.length() : 0));
        return result == 0;
    }

    public static boolean loadState(Context context, String gameDiscId, int slot) {
        File file = getSaveFile(context, gameDiscId, slot);
        android.util.Log.d(TAG, "loadState: path=" + file.getAbsolutePath() + " exists=" + file.exists() + " gameDiscId=" + gameDiscId + " slot=" + slot);
        if (!file.exists()) {
            android.util.Log.w(TAG, "loadState: file does not exist for slot=" + slot);
            return false;
        }
        android.util.Log.d(TAG, "loadState: calling nativeLoadState...");
        int result = EmulatorBridge.nativeLoadState(file.getAbsolutePath());
        android.util.Log.d(TAG, "loadState: nativeLoadState returned " + result);
        return result == 0;
    }

    public static void deleteState(Context context, String gameDiscId, int slot) {
        File file = getSaveFile(context, gameDiscId, slot);
        if (file.exists()) file.delete();
        File thumb = getThumbnailFile(context, gameDiscId, slot);
        if (thumb.exists()) thumb.delete();
    }

    public static Bitmap loadThumbnail(Context context, String gameDiscId, int slot) {
        // First, check if we have a cached PNG thumbnail (faster, Glide-compatible)
        File pngFile = getThumbnailFile(context, gameDiscId, slot);
        if (pngFile.exists()) {
            return BitmapFactory.decodeFile(pngFile.getAbsolutePath());
        }

        // Fall back to extracting from save state binary
        File file = getSaveFile(context, gameDiscId, slot);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(8); // skip magic (4) + version (4)
            byte[] sizeBuf = new byte[4];
            if (fis.read(sizeBuf) != 4) return null;
            int coreSize = (sizeBuf[0] & 0xFF)
                         | ((sizeBuf[1] & 0xFF) << 8)
                         | ((sizeBuf[2] & 0xFF) << 16)
                         | ((sizeBuf[3] & 0xFF) << 24);
            fis.skip(coreSize);

            if (fis.read(sizeBuf) != 4) return null;
            int sw = (sizeBuf[0] & 0xFF) | ((sizeBuf[1] & 0xFF) << 8)
                   | ((sizeBuf[2] & 0xFF) << 16) | ((sizeBuf[3] & 0xFF) << 24);
            if (fis.read(sizeBuf) != 4) return null;
            int sh = (sizeBuf[0] & 0xFF) | ((sizeBuf[1] & 0xFF) << 8)
                   | ((sizeBuf[2] & 0xFF) << 16) | ((sizeBuf[3] & 0xFF) << 24);

            if (sw <= 0 || sh <= 0 || sw > 2048 || sh > 2048) return null;

            int pixelCount = sw * sh;
            byte[] rgba = new byte[pixelCount * 4];
            if (fis.read(rgba) != pixelCount * 4) return null;

            // Use direct ByteBuffer for better memory efficiency
            Bitmap bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            ByteBuffer buffer = ByteBuffer.wrap(rgba);
            bmp.copyPixelsFromBuffer(buffer);

            // Cache as PNG for future Glide loads
            saveThumbnailAsPng(bmp, pngFile);

            return bmp;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Save a bitmap as a PNG file for Glide-compatible loading.
     * This creates a cached version that can be loaded efficiently.
     */
    public static void saveThumbnailAsPng(Bitmap bitmap, File file) {
        if (bitmap == null || file == null) return;
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get or load thumbnail as a file path for Glide loading.
     * Returns null if no thumbnail exists.
     */
    public static String getThumbnailPath(Context context, String gameDiscId, int slot) {
        File pngFile = getThumbnailFile(context, gameDiscId, slot);
        if (pngFile.exists()) {
            return pngFile.getAbsolutePath();
        }

        // Try to extract and cache
        Bitmap bmp = loadThumbnail(context, gameDiscId, slot);
        if (bmp != null) {
            bmp.recycle();
            if (pngFile.exists()) {
                return pngFile.getAbsolutePath();
            }
        }
        return null;
    }

    public static List<SlotInfo> listSlots(Context context, String gameDiscId) {
        File dir = getSaveDir(context, gameDiscId);
        if (!dir.exists()) return Collections.emptyList();

        File[] files = dir.listFiles((d, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (files == null) return Collections.emptyList();

        List<SlotInfo> slots = new ArrayList<>();
        for (File f : files) {
            SlotInfo info = new SlotInfo();
            info.exists = true;
            info.timestamp = f.lastModified();
            try {
                String name = f.getName();
                String numStr = name.substring(FILE_PREFIX.length(), name.indexOf('.'));
                info.slot = Integer.parseInt(numStr);
            } catch (Exception e) {
                continue;
            }
            slots.add(info);
        }

        Collections.sort(slots, (a, b) -> Integer.compare(a.slot, b.slot));
        return slots;
    }

    /**
     * Remove orphaned ".psst.png" files left by the old (incorrect) native
     * PNG path that wrote slot_0.psst.png instead of slot_0.png.
     * This is a one-time cleanup; calling it repeatedly is cheap.
     */
    public static void cleanupOrphanedPngs(Context context, String gameDiscId) {
        File dir = getSaveDir(context, gameDiscId);
        File[] orphans = dir.listFiles((d, name) -> name.endsWith(".psst.png"));
        if (orphans != null) {
            for (File f : orphans) {
                android.util.Log.d(TAG, "cleanupOrphanedPngs: deleting orphan " + f.getName());
                f.delete();
            }
        }
    }

    public static class SlotInfo {
        public int slot;
        public boolean exists;
        public long timestamp;

        public boolean isEmpty() {
            return !exists;
        }
    }
}