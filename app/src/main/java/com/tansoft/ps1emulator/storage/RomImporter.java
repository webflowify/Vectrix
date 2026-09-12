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
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.tansoft.ps1emulator.data.AppDatabase;
import com.tansoft.ps1emulator.data.GameDao;
import com.tansoft.ps1emulator.data.GameEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RomImporter {

    private static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    public static final String[] ROM_MIME_TYPES = {
            "application/octet-stream",
            "application/x-cue",
            "application/x-iso9660",
            "application/zip",
            "application/x-7z-compressed"
    };

    public static final String[] SUPPORTED_EXTENSIONS = {
            "bin", "cue", "img", "mdf", "pbp", "toc", "cbn", "m3u", "chd", "iso", "exe",
            "zip", "7z"
    };

    public static final String SUPPORTED_EXTENSIONS_DISPLAY =
            ".bin, .cue, .img, .iso, .mdf, .pbp, .toc, .cbn, .m3u, .chd, .exe, .zip, .7z";

    public static class UnsupportedFileException extends Exception {
        private final String fileName;

        public UnsupportedFileException(String fileName) {
            super("Unsupported file: " + fileName);
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    private RomImporter() {
    }

    public static Intent createRomPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, ROM_MIME_TYPES);
        return intent;
    }

    public static String getFileExtension(Context context, Uri uri) {
        String displayName = null;
        try (var cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}

        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }

        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot > 0 && dot < displayName.length() - 1) {
                return displayName.substring(dot + 1).toLowerCase();
            }
        }

        String path = uri.getPath();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot > 0 && dot < path.length() - 1) {
                return path.substring(dot + 1).toLowerCase();
            }
        }

        return "";
    }

    public static boolean isSupportedExtension(String extension) {
        if (extension == null || extension.isEmpty()) return false;
        String lower = extension.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (ext.equals(lower)) return true;
        }
        return false;
    }

    public static void validateRomFile(Context context, Uri uri) throws UnsupportedFileException {
        String extension = getFileExtension(context, uri);
        if (!isSupportedExtension(extension)) {
            String displayName = null;
            try (var cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
            if (displayName == null) {
                displayName = uri.getLastPathSegment();
            }
            if (displayName == null) {
                displayName = "unknown file";
            }
            throw new UnsupportedFileException(displayName);
        }
    }

    public static List<String> parseCueSheet(Context context, Uri cueUri) {
        List<String> binFiles = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(cueUri)) {
            if (is == null) return binFiles;

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.toUpperCase().startsWith("FILE ")) {
                    int firstQuote = line.indexOf('"');
                    int lastQuote = line.lastIndexOf('"');
                    if (firstQuote >= 0 && lastQuote > firstQuote) {
                        String binFileName = line.substring(firstQuote + 1, lastQuote);
                        String cueBase = cueUri.toString();
                        if (cueBase.contains("/")) {
                            String basePath = cueBase.substring(0, cueBase.lastIndexOf('/') + 1);
                            binFiles.add(basePath + Uri.encode(binFileName));
                        } else {
                            binFiles.add(binFileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return what we have so far
        }

        return binFiles;
    }

    // ── Main import method (returns ImportResult, no exceptions) ──

    public static ImportResult importRomSafe(Context context, Uri uri) {
        return importRomSafe(context, uri, null, null);
    }

    public static ImportResult importRomSafe(Context context, Uri uri,
                                             ArchiveExtractor.ExtractionProgressListener listener) {
        return importRomSafe(context, uri, listener, null);
    }

    public static ImportResult importRomSafe(Context context, Uri uri,
                                             ArchiveExtractor.ExtractionProgressListener listener,
                                             Consumer<String> phaseCallback) {
        try {
            validateRomFile(context, uri);
        } catch (UnsupportedFileException e) {
            return ImportResult.noGameFound(e.getFileName());
        }

        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            return ImportResult.extractionFailed(
                    ArchiveExtractor.getDisplayNameOrUnknown(context, uri),
                    "Permission denied: " + e.getMessage());
        }

        try {
            if (ArchiveExtractor.isArchiveUri(context, uri)) {
                return importFromArchive(context, uri, listener, phaseCallback);
            }

            return importDirectFile(context, uri);
        } catch (Exception e) {
            return ImportResult.extractionFailed(
                    ArchiveExtractor.getDisplayNameOrUnknown(context, uri),
                    "Import failed: " + e.getMessage());
        }
    }

    // ── Archive import flow ──

    private static ImportResult importFromArchive(Context context, Uri uri,
                                                  ArchiveExtractor.ExtractionProgressListener listener,
                                                  Consumer<String> phaseCallback) {
        String archiveName = ArchiveExtractor.getDisplayNameOrUnknown(context, uri);

        ArchiveExtractor.ExtractionValidation validation =
                ArchiveExtractor.validateBeforeExtraction(context, uri);
        if (!validation.valid) {
            return ImportResult.extractionFailed(archiveName, validation.errorMessage);
        }

        File extractedDir;
        try {
            extractedDir = ArchiveExtractor.extractToCache(context, uri, listener);
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("password-protected")) {
                return ImportResult.extractionFailed(archiveName,
                        "This archive is password-protected. Please provide an unprotected archive.");
            }
            if (msg != null && msg.contains("Corrupted")) {
                return ImportResult.corrupted(archiveName);
            }
            return ImportResult.extractionFailed(archiveName, msg != null ? msg : "Unknown extraction error");
        } catch (Exception e) {
            return ImportResult.extractionFailed(archiveName,
                    "Unexpected error during extraction: " + e.getMessage());
        }

        GameDiscoveryResult discovery;
        if (phaseCallback != null) phaseCallback.accept("Processing game files...");
        discovery = ArchiveExtractor.discoverGameFiles(extractedDir);

        if (!discovery.isPlayable()) {
            return ImportResult.noGameFound(archiveName);
        }

        String archiveUriStr = uri.toString();

        GameDao dao = AppDatabase.getInstance(context).gameDao();
        GameEntity existing = dao.getByPath(archiveUriStr);
        if (existing != null) {
            existing.lastPlayedDate = System.currentTimeMillis();
            existing.playCount++;
            dao.update(existing);
            return ImportResult.duplicate(existing);
        }

        if (phaseCallback != null) phaseCallback.accept("Preparing game...");
        String title = extractGameTitle(context, uri);

        GameEntity game = new GameEntity();
        game.title = title;
        game.archivePath = archiveUriStr;
        game.romPath = discovery.primaryFile.getAbsolutePath();

        List<String> warnings = new ArrayList<>(discovery.warnings);

        if (discovery.status == GameDiscoveryResult.Status.FOUND_CUE_BIN) {
            game.cueSheetPath = discovery.primaryFile.getAbsolutePath();
            if (discovery.binFiles != null && !discovery.binFiles.isEmpty()) {
                File firstBin = discovery.binFiles.get(0);
                if (firstBin.exists()) {
                    game.romPath = firstBin.getAbsolutePath();
                }
            }
            try {
                ArchiveExtractor.rewriteCueSheet(discovery.primaryFile, extractedDir);
            } catch (Exception ignored) {}

            for (File binFile : discovery.binFiles) {
                if (!binFile.exists()) {
                    warnings.add("Referenced BIN file missing: " + binFile.getName());
                }
            }
        }

        if (discovery.status == GameDiscoveryResult.Status.FOUND_MULTI_DISC) {
            String m3uPath = discovery.primaryFile.getAbsolutePath();
            game.cueSheetPath = m3uPath;
        }

        if (phaseCallback != null) phaseCallback.accept("Identifying game...");
        File romFileForDiscId = new File(game.romPath);
        if (romFileForDiscId.exists()) {
            try {
                game.discId = extractDiscIdFromFile(romFileForDiscId);
            } catch (OutOfMemoryError ignored) {
                game.discId = null;
            }
        }

        if (game.discId == null && game.cueSheetPath != null) {
            try {
                game.discId = extractDiscIdFromCueFile(new File(game.cueSheetPath));
            } catch (OutOfMemoryError ignored) {
                game.discId = null;
            }
        }

        game.addedDate = System.currentTimeMillis();
        game.lastPlayedDate = game.addedDate;
        game.playCount = 0;

        long id = dao.insert(game);
        game.id = id;

        if (phaseCallback != null) phaseCallback.accept("Saving...");
        try {
            String thumbnailPath = com.tansoft.ps1emulator.util.ThumbnailExtractor
                    .extractOrGenerate(context, game.title, game.id);
            game.thumbnailPath = thumbnailPath;
            dao.update(game);
        } catch (Exception ignored) {}

        if (!warnings.isEmpty()) {
            return ImportResult.partialImport(game, warnings);
        }
        return ImportResult.success(game);
    }

    // ── Direct file import (non-archive) ──

    private static ImportResult importDirectFile(Context context, Uri uri) {
        File localFile;
        try {
            localFile = copyContentToCache(context, uri);
        } catch (IOException e) {
            return ImportResult.extractionFailed(
                    ArchiveExtractor.getDisplayNameOrUnknown(context, uri),
                    "Failed to copy file: " + e.getMessage());
        }

        String lookupPath = uri.toString();
        GameDao dao = AppDatabase.getInstance(context).gameDao();
        GameEntity existing = dao.getByPath(lookupPath);
        if (existing != null) {
            existing.lastPlayedDate = System.currentTimeMillis();
            existing.playCount++;
            dao.update(existing);
            return ImportResult.duplicate(existing);
        }

        String title = extractGameTitle(context, uri);

        GameEntity game = new GameEntity();
        game.title = title;
        game.romPath = localFile.getAbsolutePath();

        String localPath = localFile.getAbsolutePath().toLowerCase();
        if (localPath.endsWith(".cue")) {
            game.cueSheetPath = localFile.getAbsolutePath();
        }

        try {
            game.discId = extractDiscId(context, uri);
        } catch (OutOfMemoryError ignored) {
            game.discId = null;
        }

        if (game.discId == null && game.cueSheetPath != null) {
            try {
                game.discId = extractDiscIdFromCueFile(localFile);
            } catch (OutOfMemoryError ignored) {
                game.discId = null;
            }
        }

        game.addedDate = System.currentTimeMillis();
        game.lastPlayedDate = game.addedDate;
        game.playCount = 0;

        long id = dao.insert(game);
        game.id = id;

        try {
            String thumbnailPath = com.tansoft.ps1emulator.util.ThumbnailExtractor
                    .extractOrGenerate(context, game.title, game.id);
            game.thumbnailPath = thumbnailPath;
            dao.update(game);
        } catch (Exception ignored) {}

        return ImportResult.success(game);
    }

    // ── Legacy importRom (throws exceptions, kept for backward compat) ──

    public static GameEntity importRom(Context context, Uri uri) throws UnsupportedFileException {
        ImportResult result = importRomSafe(context, uri);
        switch (result.status) {
            case SUCCESS:
            case DUPLICATE:
            case PARTIAL_IMPORT:
                return result.game;
            case NO_GAME_FOUND:
                throw new RuntimeException("No playable game files found");
            case EXTRACTION_FAILED:
                throw new RuntimeException(result.errorMessage);
            case CORRUPTED:
                throw new RuntimeException("Archive appears to be corrupted");
            default:
                throw new RuntimeException("Import failed");
        }
    }

    // ── Title extraction ──

    public static String extractGameTitle(Context context, Uri uri) {
        String displayName = null;

        try (var cursor = context.getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}

        if (displayName == null) {
            displayName = uri.getLastPathSegment();
        }

        if (displayName != null) {
            int dotIndex = displayName.lastIndexOf('.');
            if (dotIndex > 0) {
                displayName = displayName.substring(0, dotIndex);
            }
            displayName = cleanGameTitle(displayName);
        }

        return displayName != null ? displayName : "Unknown Game";
    }

    public static String cleanGameTitle(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown Game";

        String title = raw.replace('_', ' ');

        title = title.replaceAll("\\s*\\([^)]*\\)\\s*", " ");

        title = title.replaceAll("\\s+", " ").trim();

        if (title.isEmpty()) {
            title = raw.replaceAll("\\s*\\([^)]*\\)\\s*", " ").trim();
            if (title.isEmpty()) title = raw.trim();
        }

        return title;
    }

    // ── Disc ID extraction ──

    private static final int DISC_ID_OFFSET_START = 0x8000;
    private static final int DISC_ID_OFFSET_END = 0x9000;
    private static final int DISC_ID_MAX_LENGTH = 16;
    // Set.of() is API 30 on Android and this project has no core-library
    // desugaring, so it would fail class init on API 24-29.
    private static final Set<String> DISC_ID_PREFIXES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "SCUS", "SLUS", "SCES", "SLES", "SCPS", "SLPS", "SCED", "SLED",
            "SCKA", "SLKA", "SCAJ", "SLPM", "PCPX", "PEPX", "ESPM"
        )));

    public static String extractDiscId(Context context, Uri uri) {
        int readSize = DISC_ID_OFFSET_END + DISC_ID_MAX_LENGTH;
        byte[] header = new byte[readSize];

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            int totalRead = 0;
            while (totalRead < readSize) {
                int bytesRead = is.read(header, totalRead, readSize - totalRead);
                if (bytesRead == -1) break;
                totalRead += bytesRead;
            }

            if (totalRead < DISC_ID_OFFSET_START) return null;

            return scanForDiscId(header, totalRead);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractDiscIdFromText(String content) {
        Pattern pattern = Pattern.compile("\\b([A-Z]{4}[_-]?\\d{3,5}(\\.\\d{2})?)\\b");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String candidate = matcher.group(1).replace("_", "-");
            for (String prefix : DISC_ID_PREFIXES) {
                if (candidate.startsWith(prefix)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String extractDiscIdFromFile(File file) {
        int readSize = DISC_ID_OFFSET_END + DISC_ID_MAX_LENGTH;
        byte[] header = new byte[readSize];
        try (FileInputStream fis = new FileInputStream(file)) {
            int totalRead = 0;
            while (totalRead < readSize) {
                int bytesRead = fis.read(header, totalRead, readSize - totalRead);
                if (bytesRead == -1) break;
                totalRead += bytesRead;
            }
            if (totalRead < DISC_ID_OFFSET_START) return null;
            return scanForDiscId(header, totalRead);
        } catch (Exception e) {
            return null;
        }
    }

    private static final int CUE_MAX_LINES = 100;
    private static final int CUE_MAX_BYTES = 8192;

    private static String extractDiscIdFromCueFile(File cueFile) {
        if (cueFile == null || !cueFile.exists()) return null;
        String name = cueFile.getName().toLowerCase();
        if (!name.endsWith(".cue") && !name.endsWith(".m3u")) return null;
        if (cueFile.length() > CUE_MAX_BYTES) return null;

        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(cueFile))) {
            StringBuilder content = new StringBuilder(CUE_MAX_BYTES);
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
                lineCount++;
                if (lineCount >= CUE_MAX_LINES) break;
                if (content.length() >= CUE_MAX_BYTES) break;
            }
            return extractDiscIdFromText(content.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String scanForDiscId(byte[] data, int length) {
        int searchEnd = Math.min(length, DISC_ID_OFFSET_END);
        int searchStart = Math.min(DISC_ID_OFFSET_START, searchEnd);

        for (int i = searchStart; i < searchEnd; i++) {
            for (String prefix : DISC_ID_PREFIXES) {
                if (i + DISC_ID_MAX_LENGTH > length) continue;
                if (matchesAscii(data, i, prefix)) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i; j < length && j < i + DISC_ID_MAX_LENGTH; j++) {
                        char c = (char) data[j];
                        if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                            sb.append(c == '_' ? '-' : c);
                        } else {
                            break;
                        }
                    }
                    String discId = sb.toString().toUpperCase();
                    if (discId.length() >= 8) {
                        return discId;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesAscii(byte[] data, int offset, String prefix) {
        for (int i = 0; i < prefix.length(); i++) {
            if (offset + i >= data.length) return false;
            if ((char) data[offset + i] != prefix.charAt(i)) return false;
        }
        return true;
    }

    // ── File copy ──

    private static File copyContentToCache(Context context, Uri uri) throws IOException {
        String displayName = extractGameTitle(context, uri);
        String extension = "";
        try (var cursor = context.getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String fullName = cursor.getString(nameIndex);
                    if (fullName != null) {
                        int dot = fullName.lastIndexOf('.');
                        if (dot > 0) {
                            extension = fullName.substring(dot);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        File baseCache = context.getCacheDir();
        if (baseCache == null) {
            // On some devices getCacheDir() returns null (e.g. storage full);
            // fall back to the files dir rather than crashing with a NPE.
            baseCache = context.getFilesDir();
        }
        if (baseCache == null) {
            throw new IOException("No writable directory available to import ROM");
        }
        File cacheDir = new File(baseCache, "roms");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Cannot create cache directory: " + cacheDir);
        }

        File localFile = new File(cacheDir, displayName + extension);

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(localFile)) {
            if (is == null) {
                throw new IOException("ContentResolver returned null for " + uri);
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            localFile.delete();
            throw e;
        } catch (RuntimeException e) {
            localFile.delete();
            throw new IOException("Failed to read file: " + e.getMessage(), e);
        }

        return localFile;
    }

    // ── Async import ──

    public static void importRomAsync(Context context, Uri uri,
                                       Consumer<ImportResult> callback) {
        importRomAsync(context, uri, null, null, callback);
    }

    public static void importRomAsync(Context context, Uri uri,
                                       ArchiveExtractor.ExtractionProgressListener listener,
                                       Consumer<String> phaseCallback,
                                       Consumer<ImportResult> callback) {
        IO_EXECUTOR.execute(() -> {
            ImportResult result = importRomSafe(context, uri, listener, phaseCallback);
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(result));
            }
        });
    }
}
