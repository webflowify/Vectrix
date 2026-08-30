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

import static org.junit.Assert.*;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ArchiveExtractorTest {

    // --- isArchive ---

    @Test
    public void isArchive_zip() {
        assertTrue(ArchiveExtractor.isArchive("game.zip"));
    }

    @Test
    public void isArchive_7z() {
        assertTrue(ArchiveExtractor.isArchive("game.7z"));
    }

    @Test
    public void isArchive_upperCase() {
        assertTrue(ArchiveExtractor.isArchive("GAME.ZIP"));
        assertTrue(ArchiveExtractor.isArchive("GAME.7Z"));
    }

    @Test
    public void isArchive_notArchive() {
        assertFalse(ArchiveExtractor.isArchive("game.iso"));
        assertFalse(ArchiveExtractor.isArchive("game.bin"));
        assertFalse(ArchiveExtractor.isArchive("game.chd"));
    }

    @Test
    public void isArchive_null() {
        assertFalse(ArchiveExtractor.isArchive(null));
    }

    @Test
    public void isArchive_empty() {
        assertFalse(ArchiveExtractor.isArchive(""));
    }

    // --- isNestedArchive ---

    @Test
    public void isNestedArchive_zip() throws Exception {
        File nested = File.createTempFile("nested", ".zip");
        try {
            assertTrue(ArchiveExtractor.isNestedArchive(nested));
        } finally {
            nested.delete();
        }
    }

    @Test
    public void isNestedArchive_7z() throws Exception {
        File nested = File.createTempFile("nested", ".7z");
        try {
            assertTrue(ArchiveExtractor.isNestedArchive(nested));
        } finally {
            nested.delete();
        }
    }

    @Test
    public void isNestedArchive_notArchive() {
        assertFalse(ArchiveExtractor.isNestedArchive(new File("game.iso")));
    }

    @Test
    public void isNestedArchive_null() {
        assertFalse(ArchiveExtractor.isNestedArchive(null));
    }

    // --- deleteDir ---

    @Test
    public void deleteDir_null_returnsFalse() {
        assertFalse(ArchiveExtractor.deleteDir(null));
    }

    @Test
    public void deleteDir_nonexistent_returnsFalse() {
        assertFalse(ArchiveExtractor.deleteDir(new File("/nonexistent/dir")));
    }

    @Test
    public void deleteDir_emptyDir_returnsTrue() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_delete_empty");
        dir.mkdirs();
        try {
            assertTrue(ArchiveExtractor.deleteDir(dir));
            assertFalse(dir.exists());
        } finally {
            if (dir.exists()) dir.delete();
        }
    }

    @Test
    public void deleteDir_dirWithFiles_returnsTrue() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_delete_files");
        dir.mkdirs();
        new File(dir, "file1.txt").mkdirs();
        new File(dir, "file2.txt").mkdirs();
        try {
            assertTrue(ArchiveExtractor.deleteDir(dir));
            assertFalse(dir.exists());
        } finally {
            if (dir.exists()) dir.delete();
        }
    }

    // --- hashUri ---

    @Test
    public void hashUri_deterministic() {
        String hash1 = ArchiveExtractor.hashUri(android.net.Uri.parse("content://test/file"));
        String hash2 = ArchiveExtractor.hashUri(android.net.Uri.parse("content://test/file"));
        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(32, hash1.length());
    }

    @Test
    public void hashUri_differentUris_differentHashes() {
        String hash1 = ArchiveExtractor.hashUri(android.net.Uri.parse("content://test/a"));
        String hash2 = ArchiveExtractor.hashUri(android.net.Uri.parse("content://test/b"));
        assertNotEquals(hash1, hash2);
    }

    // --- GAME_EXTENSIONS constant ---

    @Test
    public void discoverGameFiles_emptyDir_noGameFound() {
        File emptyDir = new File(System.getProperty("java.io.tmpdir"), "test_empty_discover");
        emptyDir.mkdirs();
        try {
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(emptyDir);
            assertEquals(GameDiscoveryResult.Status.NO_GAME_FOUND, result.status);
            assertFalse(result.isPlayable());
        } finally {
            emptyDir.delete();
        }
    }

    @Test
    public void discoverGameFiles_isoFile_found() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_iso_discover");
        dir.mkdirs();
        File isoFile = new File(dir, "game.iso");
        try {
            isoFile.createNewFile();
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(dir);
            assertEquals(GameDiscoveryResult.Status.FOUND_ISO, result.status);
            assertTrue(result.isPlayable());
            assertEquals("game.iso", result.primaryFile.getName());
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            dir.delete();
        }
    }

    @Test
    public void discoverGameFiles_chdFile_found() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_chd_discover");
        dir.mkdirs();
        File chdFile = new File(dir, "game.chd");
        try {
            chdFile.createNewFile();
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(dir);
            assertEquals(GameDiscoveryResult.Status.FOUND_CHD, result.status);
            assertTrue(result.isPlayable());
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            dir.delete();
        }
    }

    @Test
    public void discoverGameFiles_exeFile_found() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_exe_discover");
        dir.mkdirs();
        File exeFile = new File(dir, "boot.exe");
        try {
            exeFile.createNewFile();
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(dir);
            assertEquals(GameDiscoveryResult.Status.FOUND_EXECUTABLE, result.status);
            assertTrue(result.isPlayable());
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            dir.delete();
        }
    }

    @Test
    public void discoverGameFiles_nestedArchive_notPlayable() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_nested_discover");
        dir.mkdirs();
        File nested = new File(dir, "inner.zip");
        try {
            nested.createNewFile();
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(dir);
            assertEquals(GameDiscoveryResult.Status.NO_GAME_FOUND, result.status);
            assertFalse(result.isPlayable());
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            dir.delete();
        }
    }

    @Test
    public void discoverGameFiles_cueAndBin_found() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "test_cuebin_discover");
        dir.mkdirs();
        File cue = new File(dir, "game.cue");
        File bin = new File(dir, "game.bin");
        try {
            cue.createNewFile();
            bin.createNewFile();
            GameDiscoveryResult result = ArchiveExtractor.discoverGameFiles(dir);
            assertEquals(GameDiscoveryResult.Status.FOUND_CUE_BIN, result.status);
            assertTrue(result.isPlayable());
            assertNotNull(result.cueFile);
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            dir.delete();
        }
    }

    // ── 7z extraction (regression + OOM-safe guards) ──

    @After
    public void resetExtractionLimits() {
        // Restore the production defaults in case a test tightened them.
        ArchiveExtractor.MAX_TOTAL_UNCOMPRESSED_BYTES = 8L * 1024 * 1024 * 1024;
        ArchiveExtractor.MAX_SINGLE_ENTRY_BYTES = 4L * 1024 * 1024 * 1024;
        ArchiveExtractor.MAX_ARCHIVE_ENTRIES = 100_000;
    }

    private File build7z(Map<String, byte[]> entries) throws IOException {
        File f = File.createTempFile("test_archive_", ".7z");
        try (SevenZOutputFile out = new SevenZOutputFile(f)) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                SevenZArchiveEntry e = new SevenZArchiveEntry();
                e.setName(en.getKey());
                out.putArchiveEntry(e);
                out.write(en.getValue());
                out.closeArchiveEntry();
            }
        }
        return f;
    }

    private Uri registerArchive(File archive, String displayName) throws IOException {
        Uri uri = Uri.parse("content://com.tansoft.test/" + displayName);
        // openInputStream() is served via the shadow; display name / size fall back
        // to the URI's last path segment in production code, which is enough here.
        ShadowContentResolver shadow = shadowOf(RuntimeEnvironment.getApplication().getContentResolver());
        shadow.registerInputStream(uri, new FileInputStream(archive));
        return uri;
    }

    private static byte[] readFile(File f) throws IOException {
        byte[] out = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0, n;
            while ((n = in.read(out, off, out.length - off)) > 0) off += n;
        }
        return out;
    }

    @Test
    public void extract7z_normalArchive_extractsFiles() throws Exception {
        byte[] binData = new byte[4096];
        for (int i = 0; i < binData.length; i++) binData[i] = (byte) i;
        byte[] cueData = "FILE \"game.bin\" BINARY\n".getBytes("UTF-8");

        File archive = build7z(new HashMap<String, byte[]>() {{
            put("game.bin", binData);
            put("game.cue", cueData);
        }});

        Context context = RuntimeEnvironment.getApplication();
        Uri uri = registerArchive(archive, "game.7z");

        File cacheDir = ArchiveExtractor.extractToCache(context, uri);
        File binFile = new File(cacheDir, "game.bin");
        File cueFile = new File(cacheDir, "game.cue");

        assertTrue("game.bin should exist", binFile.exists());
        assertTrue("game.cue should exist", cueFile.exists());
        assertArrayEquals("game.bin content must match", binData, readFile(binFile));
        assertEquals("game.cue content must match",
                "FILE \"game.bin\" BINARY\n", new String(readFile(cueFile), "UTF-8"));
    }

    @Test
    public void extract7z_oversizedArchive_rejected() throws Exception {
        // Tighten the limits so a tiny archive exceeds the per-entry cap.
        ArchiveExtractor.MAX_TOTAL_UNCOMPRESSED_BYTES = 1024;
        ArchiveExtractor.MAX_SINGLE_ENTRY_BYTES = 512;

        byte[] data = new byte[2048];
        File archive = build7z(new HashMap<String, byte[]>() {{
            put("big.bin", data);
        }});

        Context context = RuntimeEnvironment.getApplication();
        Uri uri = registerArchive(archive, "big.7z");

        try {
            ArchiveExtractor.extractToCache(context, uri);
            fail("Expected IOException for oversized archive");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("too large")
                    || expected.getMessage().toLowerCase().contains("safely"));
        }
    }

    @Test
    public void extract7z_tooManyEntries_rejected() throws Exception {
        ArchiveExtractor.MAX_ARCHIVE_ENTRIES = 2;

        File archive = build7z(new HashMap<String, byte[]>() {{
            put("a.bin", new byte[1]);
            put("b.bin", new byte[1]);
            put("c.bin", new byte[1]);
        }});

        Context context = RuntimeEnvironment.getApplication();
        Uri uri = registerArchive(archive, "many.7z");

        try {
            ArchiveExtractor.extractToCache(context, uri);
            fail("Expected IOException for too many entries");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("too many"));
        }
    }

    private File buildZip(Map<String, byte[]> entries) throws IOException {
        File f = File.createTempFile("test_archive_", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(en.getKey()));
                out.write(en.getValue());
                out.closeEntry();
            }
        }
        return f;
    }

    @Test
    public void extractZip_normalArchive_extractsFiles() throws Exception {
        byte[] binData = new byte[4096];
        for (int i = 0; i < binData.length; i++) binData[i] = (byte) i;
        byte[] cueData = "FILE \"game.bin\" BINARY\n".getBytes("UTF-8");

        File archive = buildZip(new HashMap<String, byte[]>() {{
            put("game.bin", binData);
            put("game.cue", cueData);
        }});

        Context context = RuntimeEnvironment.getApplication();
        Uri uri = registerArchive(archive, "game.zip");

        File cacheDir = ArchiveExtractor.extractToCache(context, uri);
        File binFile = new File(cacheDir, "game.bin");
        File cueFile = new File(cacheDir, "game.cue");

        assertTrue("game.bin should exist", binFile.exists());
        assertTrue("game.cue should exist", cueFile.exists());
        assertArrayEquals("game.bin content must match", binData, readFile(binFile));
        assertEquals("game.cue content must match",
                "FILE \"game.bin\" BINARY\n", new String(readFile(cueFile), "UTF-8"));
    }

    @Test
    public void getCacheDir_neverReturnsNullEvenWhenCacheDirUnavailable() throws Exception {
        // getSafeCacheDir must fall back to filesDir / dataDir so callers can
        // always build a cache path without hitting NullPointerException when
        // Context.getCacheDir() returns null on some devices.
        File dir = ArchiveExtractor.getCacheDir(
                RuntimeEnvironment.getApplication(),
                android.net.Uri.parse("content://test/regression"));
        assertNotNull(dir);
        assertFalse(dir.getAbsolutePath().isEmpty());
    }
}
