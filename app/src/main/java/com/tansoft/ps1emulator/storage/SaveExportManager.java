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

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SaveExportManager {

    private static final String TAG = "SaveExportManager";

    public static class ExportResult {
        public int filesExported;
        public List<String> warnings;
        public boolean success;

        public ExportResult() {
            warnings = new ArrayList<>();
        }
    }

    public interface ExportProgressListener {
        void onFileExporting(String fileName, int current, int total);
        void onComplete(ExportResult result);
    }

    public static void exportAsync(Context context, String gameDiscId, String gameTitle,
                                   Uri exportDirUri, ExportProgressListener listener) {
        new Thread(() -> {
            ExportResult result = new ExportResult();
            try {
                Log.d(TAG, "Starting export for game: " + gameTitle + ", discId: " + gameDiscId);
                
                DocumentFile exportDir = DocumentFile.fromTreeUri(context, exportDirUri);
                if (exportDir == null || !exportDir.canWrite()) {
                    Log.e(TAG, "Cannot write to export directory: " + exportDirUri);
                    result.success = false;
                    result.warnings.add("Cannot write to selected directory");
                    listener.onComplete(result);
                    return;
                }
                Log.d(TAG, "Export directory is writable: " + exportDir.getUri());

                DocumentFile gameDir = exportDir.createDirectory(sanitizeFileName(gameTitle));
                if (gameDir == null) {
                    Log.e(TAG, "Failed to create game folder for: " + gameTitle);
                    result.success = false;
                    result.warnings.add("Failed to create game folder");
                    listener.onComplete(result);
                    return;
                }
                Log.d(TAG, "Created game folder: " + gameDir.getName());

                List<FileEntry> files = new ArrayList<>();

                File memcardDir = new File(context.getFilesDir(), "memcards");
                Log.d(TAG, "Checking memcard directory: " + memcardDir.getAbsolutePath());
                for (int slot = 0; slot <= 1; slot++) {
                    File cardFile = new File(memcardDir, gameDiscId + "_card_" + slot + ".mcr");
                    Log.d(TAG, "Checking card file: " + cardFile.getAbsolutePath() + " (exists: " + cardFile.exists() + ")");
                    if (cardFile.exists()) {
                        files.add(new FileEntry(cardFile, "saves/" + cardFile.getName()));
                    }
                }

                File saveDir = SaveStateManager.getSaveDir(context, gameDiscId);
                Log.d(TAG, "Checking save state directory: " + saveDir.getAbsolutePath() + " (exists: " + saveDir.exists() + ")");
                if (saveDir.exists()) {
                    File[] stateFiles = saveDir.listFiles();
                    if (stateFiles != null) {
                        Log.d(TAG, "Found " + stateFiles.length + " files in save state directory");
                        for (File f : stateFiles) {
                            Log.d(TAG, "Checking state file: " + f.getName());
                            if (f.getName().startsWith("slot_")) {
                                files.add(new FileEntry(f, "states/" + f.getName()));
                            }
                        }
                    } else {
                        Log.d(TAG, "Save state directory listFiles() returned null");
                    }
                }

                Log.d(TAG, "Total save files found: " + files.size());
                if (files.isEmpty()) {
                    result.success = true;
                    result.warnings.add("No save files found for this game");
                    listener.onComplete(result);
                    return;
                }

                int total = files.size();
                for (int i = 0; i < total; i++) {
                    FileEntry entry = files.get(i);
                    listener.onFileExporting(entry.file.getName(), i + 1, total);

                    String[] pathParts = entry.relativePath.split("/");
                    DocumentFile currentDir = gameDir;
                    for (int p = 0; p < pathParts.length - 1; p++) {
                        DocumentFile subDir = currentDir.findFile(pathParts[p]);
                        if (subDir == null) {
                            subDir = currentDir.createDirectory(pathParts[p]);
                        }
                        currentDir = subDir;
                    }

                    String fileName = pathParts[pathParts.length - 1];
                    Log.d(TAG, "Creating file: " + fileName + " in " + currentDir.getName());
                    DocumentFile outFile = currentDir.createFile("application/octet-stream", fileName);
                    if (outFile != null) {
                        copyFile(entry.file, context, outFile.getUri());
                        Log.d(TAG, "Successfully exported: " + fileName);
                        result.filesExported++;
                    } else {
                        Log.e(TAG, "Failed to create output file: " + fileName);
                        result.warnings.add("Failed to create: " + entry.relativePath);
                    }
                }

                result.success = true;
            } catch (Exception e) {
                Log.e(TAG, "Export failed", e);
                result.success = false;
                result.warnings.add("Export failed: " + e.getMessage());
            }
            listener.onComplete(result);
        }).start();
    }

    private static void copyFile(File src, Context context, Uri destUri) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = context.getContentResolver().openOutputStream(destUri)) {
            if (out == null) throw new IOException("Cannot open output stream");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\- .]", "_").trim();
    }

    private static class FileEntry {
        File file;
        String relativePath;

        FileEntry(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }
}
