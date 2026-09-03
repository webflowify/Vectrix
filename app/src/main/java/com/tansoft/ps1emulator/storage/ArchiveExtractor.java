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
import android.os.StatFs;
import android.provider.OpenableColumns;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class ArchiveExtractor {

    public interface ExtractionProgressListener {
        void onProgressUpdate(String fileName, int percent, long bytesExtracted, long totalBytes);
    }

    private static final String CACHE_PREFIX = "rom_cache_";
    private static final String CACHE_INDEX_FILE = ".cache_key";
    private static final long MIN_ARCHIVE_SIZE = 100;
    // Set.of() is API 30 on Android and this project has no core-library
    // desugaring, so it would fail class init on API 24-29.
    private static final Set<String> GAME_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "bin", "cue", "iso", "img", "mdf", "pbp", "toc", "cbn", "m3u", "chd", "exe"
            )));
    private static final Set<String> ARCHIVE_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("zip", "7z")));

    // ── Extraction safety limits ──
    // 7z solid blocks are decoded into memory, so an oversized or malicious
    // archive can trigger OutOfMemoryError. Legitimate PS1 games are at most a
    // few hundred MiB uncompressed (a single-layer CD is ~700 MiB), so these
    // caps are far above any real use case while still bounding heap usage.
    // Package-private (non-final) so tests can tighten them.
    static long MAX_TOTAL_UNCOMPRESSED_BYTES = 8L * 1024 * 1024 * 1024; // 8 GiB
    static long MAX_SINGLE_ENTRY_BYTES = 4L * 1024 * 1024 * 1024;       // 4 GiB
    static int MAX_ARCHIVE_ENTRIES = 100_000;

    private ArchiveExtractor() {
    }

    // ── Archive detection ──

    public static boolean isArchive(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".7z");
    }

    public static boolean isArchiveUri(Context context, Uri uri) {
        String path = uri.getPath();
        if (path != null && isArchive(path)) return true;
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            String lower = mimeType.toLowerCase();
            if (lower.equals("application/zip") || lower.equals("application/x-7z-compressed")
                    || lower.equals("application/x-iso9660")) return true;
        }
        String displayName = getDisplayName(context, uri);
        if (displayName != null) return isArchive(displayName);
        return false;
    }

    public static boolean isNestedArchive(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".7z");
    }

    private static String getArchiveType(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            String lower = mimeType.toLowerCase();
            if (lower.equals("application/zip") || lower.equals("application/x-zip-compressed")) return "zip";
            if (lower.equals("application/x-7z-compressed")) return "7z";
        }
        String displayName = getDisplayName(context, uri);
        if (displayName != null) {
            String lower = displayName.toLowerCase();
            if (lower.endsWith(".zip")) return "zip";
            if (lower.endsWith(".7z")) return "7z";
        }
        return null;
    }

    private static String getDisplayName(Context context, Uri uri) {
        try (var cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) return cursor.getString(nameIndex);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    public static String getDisplayNameOrUnknown(Context context, Uri uri) {
        String name = getDisplayName(context, uri);
        return name != null ? name : "unknown file";
    }

    // ── Pre-extraction validation ──

    public static class ExtractionValidation {
        public final boolean valid;
        public final String errorTitle;
        public final String errorMessage;

        private ExtractionValidation(boolean valid, String errorTitle, String errorMessage) {
            this.valid = valid;
            this.errorTitle = errorTitle;
            this.errorMessage = errorMessage;
        }

        public static ExtractionValidation ok() {
            return new ExtractionValidation(true, null, null);
        }

        public static ExtractionValidation failed(String title, String message) {
            return new ExtractionValidation(false, title, message);
        }
    }

    public static ExtractionValidation validateBeforeExtraction(Context context, Uri archiveUri) {
        String displayName = getDisplayNameOrUnknown(context, archiveUri);

        long fileSize = getFileSize(context, archiveUri);
        if (fileSize == 0) {
            return ExtractionValidation.failed(
                    "Import Failed",
                    "\"" + displayName + "\" is empty.");
        }
        if (fileSize > 0 && fileSize < MIN_ARCHIVE_SIZE) {
            return ExtractionValidation.failed(
                    "Import Failed",
                    "\"" + displayName + "\" is too small to contain game data (" + fileSize + " bytes).");
        }

        if (!hasEnoughDiskSpace(context, fileSize)) {
            return ExtractionValidation.failed(
                    "Insufficient Storage",
                    "Not enough disk space to extract \"" + displayName + "\".");
        }

        return ExtractionValidation.ok();
    }

    private static long getFileSize(Context context, Uri uri) {
        try (var cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static boolean hasEnoughDiskSpace(Context context, long archiveSize) {
        try {
            File cacheDir = getSafeCacheDir(context);
            StatFs stat = new StatFs(cacheDir.getPath());
            long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long requiredBytes = archiveSize > 0 ? archiveSize * 3 : 50 * 1024 * 1024;
            return availableBytes >= requiredBytes;
        } catch (Exception e) {
            return true;
        }
    }

    // ── Cache management ──

    public static String hashUri(Uri uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(uri.toString().getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(uri.toString().hashCode());
        }
    }

    public static File getCacheDir(Context context, Uri archiveUri) {
        return new File(getSafeCacheDir(context), CACHE_PREFIX + hashUri(archiveUri));
    }

    public static void clearCache(Context context, Uri archiveUri) {
        File cacheDir = getCacheDir(context, archiveUri);
        android.util.Log.d("ArchiveExtractor", "clearCache: " + cacheDir.getAbsolutePath());
        deleteDir(cacheDir);
    }

    public static boolean isExtracted(Context context, Uri archiveUri) {
        File cacheDir = getCacheDir(context, archiveUri);
        if (!cacheDir.exists()) {
            android.util.Log.d("ArchiveExtractor", "isExtracted: cacheDir not found");
            return false;
        }
        File index = new File(cacheDir, CACHE_INDEX_FILE);
        if (!index.exists()) {
            android.util.Log.d("ArchiveExtractor", "isExtracted: index not found");
            return false;
        }
        GameDiscoveryResult result = discoverGameFiles(cacheDir);
        android.util.Log.d("ArchiveExtractor", "isExtracted: discovery=" + result.status
                + " primary=" + (result.primaryFile != null ? result.primaryFile.getName() : "null")
                + " primarySize=" + (result.primaryFile != null ? result.primaryFile.length() : -1)
                + " binCount=" + (result.binFiles != null ? result.binFiles.size() : 0));
        if (!result.isPlayable() || result.primaryFile == null || !result.primaryFile.exists()) {
            return false;
        }
        if (result.primaryFile.getName().toLowerCase().endsWith(".cue")
                && result.primaryFile.length() > 8192) {
            android.util.Log.w("ArchiveExtractor", "isExtracted: CUE file too large ("
                    + result.primaryFile.length() + " bytes), likely corrupted extraction");
            return false;
        }
        if (result.status == GameDiscoveryResult.Status.FOUND_CUE_BIN
                && result.binFiles != null) {
            for (File bin : result.binFiles) {
                android.util.Log.d("ArchiveExtractor", "isExtracted: checking bin "
                        + bin.getName() + " size=" + bin.length());
                if (bin.exists() && bin.length() == 0) return false;
            }
        }
        if (result.primaryFile.length() == 0) return false;
        return true;
    }

    // ── Extraction ──

    public static File extractToCache(Context context, Uri archiveUri) throws IOException {
        return extractToCache(context, archiveUri, null);
    }

    public static File extractToCache(Context context, Uri archiveUri,
                                      ExtractionProgressListener listener) throws IOException {
        File cacheDir = getCacheDir(context, archiveUri);
        android.util.Log.d("ArchiveExtractor", "extractToCache: archive=" + archiveUri);
        if (isExtracted(context, archiveUri)) {
            android.util.Log.d("ArchiveExtractor", "extractToCache: already extracted");
            if (listener != null) {
                listener.onProgressUpdate("", 100, 0, 0);
            }
            return cacheDir;
        }

        deleteDir(cacheDir);
        if (!cacheDir.mkdirs()) {
            throw new IOException("Cannot create cache directory: " + cacheDir);
        }

        String archiveType = getArchiveType(context, archiveUri);
        android.util.Log.d("ArchiveExtractor", "extractToCache: type=" + archiveType);
        if (archiveType == null) {
            throw new IOException("Cannot determine archive type from URI: " + archiveUri);
        }

        try {
            switch (archiveType) {
                case "zip":
                    extractZip(context, archiveUri, cacheDir, listener);
                    break;
                case "7z":
                    extract7z(context, archiveUri, cacheDir, listener);
                    break;
                default:
                    throw new IOException("Unsupported archive format: " + archiveUri);
            }
        } catch (ZipException e) {
            deleteDir(cacheDir);
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("password")) {
                throw new IOException("Archive is password-protected: " + e.getMessage(), e);
            }
            throw new IOException("Corrupted or invalid archive: " + e.getMessage(), e);
        } catch (NoClassDefFoundError e) {
            deleteDir(cacheDir);
            throw new IOException("7z extraction not supported: LZMA library missing", e);
        } catch (IOException e) {
            deleteDir(cacheDir);
            throw e;
        } catch (RuntimeException e) {
            // Defensive: any RuntimeException from extraction (e.g. from
            // Apache Commons Compress or ContentResolver) must not crash the app.
            deleteDir(cacheDir);
            throw new IOException("Extraction failed: " + e.getMessage(), e);
        }

        new File(cacheDir, CACHE_INDEX_FILE).createNewFile();
        scanAndImportBios(context, cacheDir);

        GameDiscoveryResult postCheck = discoverGameFiles(cacheDir);
        android.util.Log.d("ArchiveExtractor", "extractToCache: postCheck=" + postCheck.status
                + " primary=" + (postCheck.primaryFile != null ? postCheck.primaryFile.getName() : "null")
                + " primarySize=" + (postCheck.primaryFile != null ? postCheck.primaryFile.length() : -1));
        if (!postCheck.isPlayable() || postCheck.primaryFile == null || postCheck.primaryFile.length() == 0) {
            deleteDir(cacheDir);
            throw new IOException("Extraction produced no valid game files");
        }
        if (postCheck.status == GameDiscoveryResult.Status.FOUND_CUE_BIN
                && postCheck.binFiles != null) {
            for (File bin : postCheck.binFiles) {
                if (bin.exists() && bin.length() == 0) {
                    deleteDir(cacheDir);
                    throw new IOException("Extraction produced empty BIN file: " + bin.getName());
                }
            }
        }

        return cacheDir;
    }

    private static void extractZip(Context context, Uri archiveUri, File destDir,
                                   ExtractionProgressListener listener) throws IOException {
        long totalSize = getFileSize(context, archiveUri);
        android.util.Log.d("ArchiveExtractor", "extractZip: totalSize=" + totalSize
                + " dest=" + destDir.getAbsolutePath());
        File tmpFile = copyToTempFile(context, archiveUri);
        android.util.Log.d("ArchiveExtractor", "extractZip: tmpFile=" + tmpFile.getAbsolutePath()
                + " tmpSize=" + tmpFile.length());
        try {
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmpFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            int entryCount = 0;

            byte[] buffer = new byte[65536];
            long bytesExtracted = 0;
            int lastReportedPercent = -1;

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                entryCount++;
                String entryName = entry.getName();
                String simpleName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf('/') + 1)
                        : entryName;

                android.util.Log.d("ArchiveExtractor", "extractZip: entry[" + entryCount + "] "
                        + entryName + " size=" + entry.getSize()
                        + " compressedSize=" + entry.getCompressedSize());

                if (isPathTraversal(entryName)) {
                    throw new IOException("Unsafe path in archive: " + entryName);
                }

                File outFile = new File(destDir, entryName);
                if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                    throw new IOException("Path traversal detected: " + entryName);
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }
                outFile.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        bytesExtracted += read;

                        if (listener != null && totalSize > 0) {
                            int percent = (int) ((bytesExtracted * 100) / totalSize);
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent;
                                listener.onProgressUpdate(
                                        simpleName, percent, bytesExtracted, totalSize);
                            }
                        }
                    }
                }
                android.util.Log.d("ArchiveExtractor", "extractZip: wrote " + outFile.getName()
                        + " fileSize=" + outFile.length());
                if (outFile.length() == 0 && entry.getSize() > 0) {
                    outFile.delete();
                    throw new IOException("Extracted empty file: " + entryName
                            + " (expected " + entry.getSize() + " bytes)");
                }
            }
            zip.close();
            android.util.Log.d("ArchiveExtractor", "extractZip: done, extracted " + entryCount
                    + " entries, " + bytesExtracted + " bytes total");

            if (listener != null) {
                listener.onProgressUpdate("", 100, totalSize > 0 ? totalSize : bytesExtracted,
                        totalSize > 0 ? totalSize : bytesExtracted);
            }
        } finally {
            tmpFile.delete();
        }
    }

    private static void extract7z(Context context, Uri archiveUri, File destDir,
                                  ExtractionProgressListener listener) throws IOException {
        extract7z(context, archiveUri, destDir, listener,
                ArchiveExtractor.MAX_TOTAL_UNCOMPRESSED_BYTES,
                ArchiveExtractor.MAX_SINGLE_ENTRY_BYTES,
                ArchiveExtractor.MAX_ARCHIVE_ENTRIES);
    }

    private static void extract7z(Context context, Uri archiveUri, File destDir,
                                  ExtractionProgressListener listener,
                                  long maxTotalBytes, long maxSingleEntryBytes,
                                  int maxEntries) throws IOException {
        File tmpFile = copyToTempFile(context, archiveUri);
        try {
            // ── Pass 1: enumerate entries and validate declared sizes ──
            // 7z stores uncompressed sizes in its header, so the declared size is
            // trustworthy and lets us reject oversized archives *before* spending
            // heap decoding solid blocks. The first reader is closed before pass 2
            // so the two decoded folders are never held in memory simultaneously.
            long totalSize = 0;
            int entryCount = 0;
            SevenZFile sevenZ = new SevenZFile(tmpFile);
            try {
                SevenZArchiveEntry entry;
                while ((entry = next7zEntry(sevenZ)) != null) {
                    if (++entryCount > maxEntries) {
                        throw new IOException("Archive contains too many entries (>" + maxEntries + ")");
                    }
                    long size = entry.getSize();
                    if (size < 0) {
                        throw new IOException("Archive entry has unknown size: " + entry.getName());
                    }
                    if (size > maxSingleEntryBytes) {
                        throw new IOException("Archive entry is too large to extract safely: "
                                + entry.getName() + " (" + size + " bytes)");
                    }
                    totalSize += size;
                    if (totalSize > maxTotalBytes) {
                        throw new IOException("Archive is too large to extract safely ("
                                + totalSize + " uncompressed bytes)");
                    }
                }
            } finally {
                sevenZ.close();
            }

            // ── Pass 2: stream entries to disk ──
            SevenZFile sevenZReader = new SevenZFile(tmpFile);
            try {
                byte[] buffer = new byte[65536];
                long bytesExtracted = 0;
                int lastReportedPercent = -1;

                for (int i = 0; i < entryCount; i++) {
                    SevenZArchiveEntry currentEntry = next7zEntry(sevenZReader);
                    if (currentEntry == null) break;

                    String entryName = currentEntry.getName();
                    String simpleName = entryName.contains("/")
                            ? entryName.substring(entryName.lastIndexOf('/') + 1)
                            : entryName;

                    android.util.Log.d("ArchiveExtractor", "extract7z: entry "
                            + entryName + " size=" + currentEntry.getSize()
                            + " isDir=" + currentEntry.isDirectory());

                    if (isPathTraversal(entryName)) {
                        throw new IOException("Unsafe path in archive: " + entryName);
                    }

                    File outFile = new File(destDir, entryName);
                    if (!outFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                        throw new IOException("Path traversal detected: " + entryName);
                    }

                    if (currentEntry.isDirectory()) {
                        outFile.mkdirs();
                        continue;
                    }
                    outFile.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(outFile)) {
                        int read;
                        while ((read = sevenZReader.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            bytesExtracted += read;

                            if (bytesExtracted > maxTotalBytes) {
                                throw new IOException("Extracted data exceeded safe size limit");
                            }

                            if (listener != null && totalSize > 0) {
                                int percent = (int) ((bytesExtracted * 100) / totalSize);
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent;
                                    listener.onProgressUpdate(
                                            simpleName, percent, bytesExtracted, totalSize);
                                }
                            }
                        }
                    }
                    android.util.Log.d("ArchiveExtractor", "extract7z: wrote "
                            + outFile.getName() + " fileSize=" + outFile.length());
                    if (outFile.length() == 0 && currentEntry.getSize() > 0) {
                        outFile.delete();
                        throw new IOException("Extracted empty file: " + entryName
                                + " (expected " + currentEntry.getSize() + " bytes)");
                    }
                }

                if (listener != null) {
                    listener.onProgressUpdate("", 100, totalSize, totalSize);
                }
            } finally {
                sevenZReader.close();
            }
        } catch (OutOfMemoryError oom) {
            // A 7z solid block can require a large decoder buffer; translate the
            // hard crash into a recoverable error and make sure the temp file is
            // removed by the surrounding finally block.
            android.util.Log.e("ArchiveExtractor", "extract7z ran out of memory", oom);
            throw new IOException("Not enough memory to extract this archive", oom);
        } finally {
            tmpFile.delete();
        }
    }

    private static SevenZArchiveEntry next7zEntry(SevenZFile sevenZFile) throws IOException {
        try {
            return sevenZFile.getNextEntry();
        } catch (OutOfMemoryError oom) {
            android.util.Log.e("ArchiveExtractor", "OutOfMemoryError while reading 7z entry", oom);
            throw new IOException("Not enough memory to read this archive", oom);
        }
    }

    private static boolean isPathTraversal(String entryName) {
        if (entryName == null) return false;
        String normalized = entryName.replace('\\', '/');
        return normalized.contains("..") || normalized.startsWith("/");
    }

    /**
     * Returns a writable directory suitable for staging archive copies during
     * extraction, preferring the app cache directory.
     *
     * <p>On some Android devices {@link Context#getCacheDir()} can return
     * {@code null} (e.g. when internal storage is unavailable). Passing that
     * {@code null} to {@link File#createTempFile(String, String, File)} throws an
     * {@link IllegalStateException} ("Could not find temporary directory") on
     * Android because the {@code java.io.tmpdir} system property is not set. To
     * avoid that hard crash we fall back through several candidate directories
     * and only fail with a clear {@link IOException} when none are usable.
     */
    private static File getTempDirectory(Context context) throws IOException {
        File dir = resolveFirstWritableDir(
                context.getCacheDir(),
                context.getFilesDir(),
                context.getExternalCacheDir(),
                context.getExternalFilesDir(null));
        if (dir == null) {
            throw new IOException("No writable directory available to copy archive for extraction");
        }
        return dir;
    }

    /**
     * Returns a best-effort writable application directory for cache data.
     * Never returns {@code null}: falls back through several candidate
     * directories and, as a last resort, to a sub-directory of the app's data
     * directory (which always exists for a valid context). Callers that build
     * paths with the result must therefore never hit a {@link NullPointerException}
     * from a {@code null} {@link Context#getCacheDir()}.
     */
    private static File getSafeCacheDir(Context context) {
        File dir = resolveFirstWritableDir(
                context.getCacheDir(),
                context.getFilesDir(),
                context.getExternalCacheDir(),
                context.getExternalFilesDir(null));
        if (dir != null) return dir;
        return new File(context.getApplicationInfo().dataDir, "cache");
    }

    private static File resolveFirstWritableDir(File... candidates) {
        for (File candidate : candidates) {
            if (candidate == null) continue;
            try {
                if (!candidate.exists() && !candidate.mkdirs()) continue;
                if (candidate.isDirectory() && candidate.canWrite()) return candidate;
            } catch (SecurityException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static File copyToTempFile(Context context, Uri uri) throws IOException {
        File tmpDir = getTempDirectory(context);
        File tmp = File.createTempFile("archive_", ".tmp", tmpDir);
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                tmp.delete();
                throw new IOException("Cannot open input stream for " + uri);
            }
            try (OutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } catch (RuntimeException e) {
            // ContentResolver.openInputStream can throw IllegalStateException
            // (e.g. when the URI is no longer accessible or the provider errors).
            // Wrap it in an IOException so the entire call chain can handle it
            // uniformly instead of crashing with an uncaught RuntimeException.
            tmp.delete();
            throw new IOException("Failed to open archive: " + e.getMessage(), e);
        }
        return tmp;
    }

    // ── Game file discovery ──

    public static GameDiscoveryResult discoverGameFiles(File dir) {
        List<File> cueFiles = new ArrayList<>();
        List<File> isoFiles = new ArrayList<>();
        List<File> binFiles = new ArrayList<>();
        List<File> chdFiles = new ArrayList<>();
        List<File> exeFiles = new ArrayList<>();
        List<File> m3uFiles = new ArrayList<>();
        List<File> nestedArchives = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        collectGameFiles(dir, cueFiles, isoFiles, binFiles, chdFiles, exeFiles, m3uFiles, nestedArchives, warnings);

        if (!nestedArchives.isEmpty()) {
            return GameDiscoveryResult.noGameFound(
                    "Archive contains nested archives: " + nestedArchives.get(0).getName());
        }

        if (!m3uFiles.isEmpty()) {
            List<File> allBins = new ArrayList<>();
            for (File m3u : m3uFiles) {
                List<File> referencedBins = parseM3u(m3u, dir);
                allBins.addAll(referencedBins);
            }
            if (!allBins.isEmpty()) {
                return GameDiscoveryResult.multiDiscFound(m3uFiles.get(0), allBins);
            }
        }

        if (!cueFiles.isEmpty()) {
            File cueFile = cueFiles.get(0);
            List<File> referencedBins = findBinFilesForCue(cueFile, dir);
            List<String> cueWarnings = new ArrayList<>(warnings);

            if (referencedBins.isEmpty() && !binFiles.isEmpty()) {
                referencedBins = new ArrayList<>(binFiles);
                cueWarnings.add("CUE file parsing failed, using BIN files found in directory");
            }

            if (referencedBins.isEmpty()) {
                cueWarnings.add("CUE file found but referenced BIN files are missing");
                return GameDiscoveryResult.cueBinFound(cueFile, referencedBins, cueWarnings);
            }

            boolean allExist = true;
            for (File bin : referencedBins) {
                if (!bin.exists()) {
                    allExist = false;
                    cueWarnings.add("Missing BIN file: " + bin.getName());
                }
            }

            if (!allExist) {
                return GameDiscoveryResult.cueBinFound(cueFile, referencedBins, cueWarnings);
            }

            return GameDiscoveryResult.cueBinFound(cueFile, referencedBins, cueWarnings.isEmpty() ? null : cueWarnings);
        }

        if (!isoFiles.isEmpty()) {
            if (isoFiles.size() > 1) {
                isoFiles.sort((a, b) -> Long.compare(b.length(), a.length()));
                warnings.add("Multiple ISO files found, using largest: " + isoFiles.get(0).getName());
            }
            return GameDiscoveryResult.isoFound(isoFiles.get(0));
        }

        if (!chdFiles.isEmpty()) {
            return GameDiscoveryResult.chdFound(chdFiles.get(0));
        }

        if (!binFiles.isEmpty()) {
            if (binFiles.size() > 1) {
                warnings.add("Multiple BIN files found without CUE sheet, using first: " + binFiles.get(0).getName());
            }
            return GameDiscoveryResult.binOnlyFound(binFiles.get(0), warnings.isEmpty() ? null : warnings);
        }

        if (!exeFiles.isEmpty()) {
            return GameDiscoveryResult.executableFound(exeFiles.get(0));
        }

        List<String> nonGameFiles = listNonGameFiles(dir);
        if (!nonGameFiles.isEmpty()) {
            return GameDiscoveryResult.noGameFound(
                    "Archive contains non-game files: " + String.join(", ", nonGameFiles));
        }

        return GameDiscoveryResult.noGameFound("Archive is empty or contains no recognized files");
    }

    private static void collectGameFiles(File dir, List<File> cueFiles, List<File> isoFiles,
                                         List<File> binFiles, List<File> chdFiles,
                                         List<File> exeFiles, List<File> m3uFiles,
                                         List<File> nestedArchives, List<String> warnings) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                collectGameFiles(f, cueFiles, isoFiles, binFiles, chdFiles, exeFiles, m3uFiles, nestedArchives, warnings);
                continue;
            }

            String name = f.getName().toLowerCase();
            if (name.endsWith(".cue")) {
                cueFiles.add(f);
            } else if (name.endsWith(".iso")) {
                isoFiles.add(f);
            } else if (name.endsWith(".bin")) {
                binFiles.add(f);
            } else if (name.endsWith(".chd")) {
                chdFiles.add(f);
            } else if (name.endsWith(".exe")) {
                exeFiles.add(f);
            } else if (name.endsWith(".m3u")) {
                m3uFiles.add(f);
            } else if (name.endsWith(".img") || name.endsWith(".mdf")
                    || name.endsWith(".pbp") || name.endsWith(".toc")
                    || name.endsWith(".cbn")) {
                isoFiles.add(f);
            } else if (isNestedArchive(f)) {
                nestedArchives.add(f);
            } else if (!name.equals(CACHE_INDEX_FILE) && !name.endsWith(".tmp")) {
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : name;
                if (!GAME_EXTENSIONS.contains(ext.substring(1))) {
                    warnings.add("Non-game file: " + f.getName());
                }
            }
        }
    }

    private static final long CUE_MAX_FILE_SIZE = 1024 * 1024;

    private static List<File> findBinFilesForCue(File cueFile, File rootDir) {
        List<File> bins = new ArrayList<>();
        if (cueFile.length() > CUE_MAX_FILE_SIZE) return bins;
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(cueFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.toUpperCase().startsWith("FILE ")) {
                    int firstQuote = line.indexOf('"');
                    int lastQuote = line.lastIndexOf('"');
                    if (firstQuote >= 0 && lastQuote > firstQuote) {
                        String binName = line.substring(firstQuote + 1, lastQuote);
                        File resolved = resolveBinFromCue(cueFile, binName, rootDir);
                        bins.add(resolved);
                    }
                }
            }
        } catch (Exception ignored) {}
        return bins;
    }

    private static File resolveBinFromCue(File cueFile, String binName, File rootDir) {
        File resolved = new File(cueFile.getParentFile(), binName);
        if (resolved.exists()) return resolved;

        resolved = new File(rootDir, binName);
        if (resolved.exists()) return resolved;

        String nameOnly = new File(binName).getName();
        resolved = new File(cueFile.getParentFile(), nameOnly);
        if (resolved.exists()) return resolved;

        resolved = new File(rootDir, nameOnly);
        if (resolved.exists()) return resolved;

        return new File(cueFile.getParentFile(), binName);
    }

    private static List<File> parseM3u(File m3uFile, File rootDir) {
        List<File> bins = new ArrayList<>();
        if (m3uFile.length() > CUE_MAX_FILE_SIZE) return bins;
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(m3uFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                File resolved = resolveBinFromCue(m3uFile, line, rootDir);
                if (resolved.exists()) {
                    bins.add(resolved);
                }
            }
        } catch (Exception ignored) {}
        return bins;
    }

    private static List<String> listNonGameFiles(File dir) {
        List<String> nonGame = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return nonGame;

        for (File f : files) {
            if (f.isDirectory()) {
                nonGame.addAll(listNonGameFiles(f));
                continue;
            }
            String name = f.getName().toLowerCase();
            if (name.equals(CACHE_INDEX_FILE) || name.endsWith(".tmp")) continue;
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            if (!GAME_EXTENSIONS.contains(ext) && !ARCHIVE_EXTENSIONS.contains(ext)) {
                nonGame.add(f.getName());
            }
        }
        return nonGame;
    }

    // ── Legacy findPrimaryGameFile (kept for backward compatibility) ──

    public static File findPrimaryGameFile(File dir) {
        GameDiscoveryResult result = discoverGameFiles(dir);
        if (result.isPlayable() && result.primaryFile != null) {
            if (result.status == GameDiscoveryResult.Status.FOUND_CUE_BIN
                    && result.binFiles != null && !result.binFiles.isEmpty()) {
                for (File binFile : result.binFiles) {
                    if (binFile.exists() && binFile.length() > 0) {
                        return binFile;
                    }
                }
                if (result.primaryFile.getName().toLowerCase().endsWith(".cue")
                        && result.primaryFile.length() > 8192) {
                    return null;
                }
                return result.primaryFile;
            }
            if (result.primaryFile.exists() && result.primaryFile.length() > 0) {
                return result.primaryFile;
            }
        }
        return null;
    }

    // ── CUE sheet rewriting ──

    public static void rewriteCueSheet(File cueFile, File cacheDir) throws IOException {
        if (cueFile.length() > CUE_MAX_FILE_SIZE) return;
        File tmpFile = new File(cacheDir, cueFile.getName() + ".tmp");
        boolean rewritten = false;

        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(cueFile));
             java.io.PrintWriter writer = new java.io.PrintWriter(tmpFile, "UTF-8")) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase().startsWith("FILE ")) {
                    int firstQuote = trimmed.indexOf('"');
                    int lastQuote = trimmed.lastIndexOf('"');
                    if (firstQuote >= 0 && lastQuote > firstQuote) {
                        String referencedFile = trimmed.substring(firstQuote + 1, lastQuote);
                        File resolved = resolveFile(cueFile, referencedFile, cacheDir);
                        if (resolved != null && resolved.exists()) {
                            String relativePath = makeRelative(cacheDir, resolved);
                            if (relativePath != null) {
                                String newLine = line.substring(0, firstQuote)
                                        + "\""
                                        + relativePath
                                        + "\""
                                        + line.substring(lastQuote + 1);
                                writer.println(newLine);
                                rewritten = true;
                                continue;
                            }
                        }
                    }
                }
                writer.println(line);
            }
        }

        if (rewritten) {
            cueFile.delete();
            tmpFile.renameTo(cueFile);
        } else {
            tmpFile.delete();
        }
    }

    private static File resolveFile(File cueFile, String referencedPath, File cacheDir) {
        File resolved = new File(cueFile.getParentFile(), referencedPath);
        if (resolved.exists()) return resolved;

        resolved = new File(cacheDir, referencedPath);
        if (resolved.exists()) return resolved;

        String nameOnly = new File(referencedPath).getName();
        resolved = new File(cueFile.getParentFile(), nameOnly);
        if (resolved.exists()) return resolved;

        resolved = new File(cacheDir, nameOnly);
        if (resolved.exists()) return resolved;

        return null;
    }

    private static String makeRelative(File baseDir, File file) {
        String base = baseDir.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(base)) {
            String rel = path.substring(base.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            return rel;
        }
        return file.getName();
    }

    // ── BIOS scan ──

    public static void scanAndImportBios(Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanAndImportBios(context, f);
                continue;
            }
            if (BiosManager.isLikelyBios(f) && BiosManager.getImportedBiosUri(context) == null) {
                BiosManager.importBiosFromFile(context, f);
                return;
            }
        }
    }

    // ── Cleanup ──

    public static boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }

    public static void cleanupCache(Context context) {
        File cacheDir = getSafeCacheDir(context);
        File[] romCaches = cacheDir.listFiles((d, name) -> name.startsWith(CACHE_PREFIX));
        if (romCaches == null) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = 7 * 24 * 60 * 60 * 1000L;

        for (File dir : romCaches) {
            if (dir.isDirectory()) {
                long lastModified = dir.lastModified();
                if (now - lastModified > maxAgeMs) {
                    deleteDir(dir);
                }
            }
        }
    }
}
