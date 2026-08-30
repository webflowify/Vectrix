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
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SaveImportManager {

    private static final String TAG = "SaveImportManager";
    private static final int MEMORY_CARD_SIZE = 128 * 1024;

    public static class ImportResult {
        public int cardsImported;
        public int statesImported;
        public List<String> warnings;
        public boolean success;

        public ImportResult() {
            warnings = new ArrayList<>();
        }
    }

    public interface ImportProgressListener {
        void onFileImporting(String fileName, int current, int total);
        void onComplete(ImportResult result);
    }

    public interface OverwriteRequestCallback {
        /**
         * Called when all save slots are full and user must choose one to overwrite.
         * Must be called on UI thread. Blocks until user makes a choice.
         *
         * @param fileName    the file being imported
         * @param slotStates  boolean[10] — true if slot is occupied
         * @return slot number (0-9) to overwrite, or -1 to skip
         */
        int requestOverwriteSlot(String fileName, boolean[] slotStates);
    }

    public static void importAsync(Context context, String gameDiscId,
                                   List<Uri> fileUris, ImportProgressListener listener,
                                   OverwriteRequestCallback overwriteCallback) {
        new Thread(() -> {
            ImportResult result = new ImportResult();
            try {
                int total = fileUris.size();
                for (int i = 0; i < total; i++) {
                    Uri uri = fileUris.get(i);
                    String fileName = getFileName(context, uri);
                    listener.onFileImporting(fileName != null ? fileName : "unknown", i + 1, total);

                    if (fileName == null) {
                        result.warnings.add("Could not read file name for URI " + uri);
                        continue;
                    }

                    String lowerName = fileName.toLowerCase();
                    if (lowerName.endsWith(".mcr")) {
                        importMemoryCard(context, gameDiscId, uri, fileName, result);
                    } else if (lowerName.endsWith(".psst")) {
                        importSaveState(context, gameDiscId, uri, fileName, result, overwriteCallback);
                    } else {
                        result.warnings.add("Skipping unsupported file: " + fileName);
                    }
                }
                result.success = true;
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                result.success = false;
                result.warnings.add("Import failed: " + e.getMessage());
            }
            listener.onComplete(result);
        }).start();
    }

    private static void importMemoryCard(Context context, String gameDiscId,
                                         Uri uri, String fileName, ImportResult result) {
        int slot = 0;
        if (fileName.contains("_card_1")) {
            slot = 1;
        }

        File dest = MemoryCardManager.getCardFile(context, gameDiscId, slot);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                result.warnings.add("Cannot read: " + fileName);
                return;
            }

            byte[] buf = new byte[8192];
            int totalRead = 0;
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
                totalRead += read;
            }

            if (totalRead != MEMORY_CARD_SIZE) {
                result.warnings.add("Invalid memory card size for " + fileName
                    + " (expected " + MEMORY_CARD_SIZE + " bytes, got " + totalRead + ")");
            }
            result.cardsImported++;
        } catch (IOException e) {
            Log.e(TAG, "Failed to import memory card: " + fileName, e);
            result.warnings.add("Failed to import: " + fileName);
        }
    }

    private static void importSaveState(Context context, String gameDiscId,
                                        Uri uri, String fileName, ImportResult result,
                                        OverwriteRequestCallback overwriteCallback) {
        File saveDir = SaveStateManager.getSaveDir(context, gameDiscId);
        saveDir.mkdirs();

        int slot = parseSlotNumber(fileName);
        if (slot >= 0 && slot < SaveStateManager.MAX_SLOTS) {
            File existing = SaveStateManager.getSaveFile(context, gameDiscId, slot);
            if (existing.exists()) {
                slot = findFreeSlot(saveDir);
            }
        } else {
            slot = findFreeSlot(saveDir);
        }

        if (slot < 0 && overwriteCallback != null) {
            boolean[] slotStates = new boolean[SaveStateManager.MAX_SLOTS];
            for (int i = 0; i < SaveStateManager.MAX_SLOTS; i++) {
                File f = SaveStateManager.getSaveFile(context, gameDiscId, i);
                slotStates[i] = f.exists();
            }

            slot = overwriteCallback.requestOverwriteSlot(fileName, slotStates);
        }

        if (slot < 0) {
            result.warnings.add("No free save slots left for: " + fileName);
            return;
        }

        File dest = SaveStateManager.getSaveFile(context, gameDiscId, slot);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                result.warnings.add("Cannot read: " + fileName);
                return;
            }

            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            result.statesImported++;
        } catch (IOException e) {
            Log.e(TAG, "Failed to import save state: " + fileName, e);
            result.warnings.add("Failed to import: " + fileName);
        }
    }

    private static int parseSlotNumber(String fileName) {
        if (fileName == null) return -1;
        String lower = fileName.toLowerCase();
        if (!lower.startsWith("slot_") || !lower.endsWith(".psst")) return -1;
        String num = lower.substring("slot_".length(), lower.length() - ".psst".length());
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int findFreeSlot(File saveDir) {
        for (int slot = 0; slot < SaveStateManager.MAX_SLOTS; slot++) {
            File f = new File(saveDir, "slot_" + slot + ".psst");
            if (!f.exists()) return slot;
        }
        return -1;
    }

    private static String getFileName(Context context, Uri uri) {
        String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(
                    android.provider.OpenableColumns.DISPLAY_NAME);
                return cursor.getString(idx);
            }
        }
        return null;
    }
}
