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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BiosManagerTest {

    // --- isLikelyBios ---

    @Test
    public void isLikelyBios_nullFile() {
        assertFalse(BiosManager.isLikelyBios(null));
    }

    @Test
    public void isLikelyBios_notAFile() {
        assertFalse(BiosManager.isLikelyBios(new File("/nonexistent/dir")));
    }

    @Test
    public void isLikelyBios_wrongSize() throws IOException {
        File tmp = Files.createTempFile("test", ".bin").toFile();
        try {
            // Write 100 bytes (not 512KB)
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(new byte[100]);
            }
            assertFalse(BiosManager.isLikelyBios(tmp));
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void isLikelyBios_correctSize_biosName() throws IOException {
        File tmp = File.createTempFile("scph1001", ".bin");
        try {
            // Write exactly 512KB (524288 bytes)
            byte[] data = new byte[524288];
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
            }
            assertTrue(BiosManager.isLikelyBios(tmp));
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void isLikelyBios_correctSize_biosInName() throws IOException {
        File tmp = File.createTempFile("my_bios_dump", ".rom");
        try {
            byte[] data = new byte[524288];
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
            }
            assertTrue(BiosManager.isLikelyBios(tmp));
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void isLikelyBios_correctSize_psxInName() throws IOException {
        File tmp = File.createTempFile("psx_bios", ".bin");
        try {
            byte[] data = new byte[524288];
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
            }
            assertTrue(BiosManager.isLikelyBios(tmp));
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void isLikelyBios_correctSize_unknownName() throws IOException {
        File tmp = File.createTempFile("random_data", ".dat");
        try {
            byte[] data = new byte[524288];
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
            }
            assertFalse(BiosManager.isLikelyBios(tmp));
        } finally {
            tmp.delete();
        }
    }

    // --- computeSha256(File) ---

    @Test
    public void computeSha256_deterministic() throws IOException {
        File tmp = File.createTempFile("hash_test", ".bin");
        try {
            byte[] data = "Hello PS1 Emulator".getBytes();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
            }
            String hash1 = BiosManager.computeSha256(tmp);
            String hash2 = BiosManager.computeSha256(tmp);
            assertNotNull(hash1);
            assertEquals(hash1, hash2);
            assertEquals(64, hash1.length()); // SHA-256 hex = 64 chars
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void computeSha256_differentInputs_differentHashes() throws IOException {
        File tmp1 = File.createTempFile("hash1", ".bin");
        File tmp2 = File.createTempFile("hash2", ".bin");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp1)) {
                fos.write("data A".getBytes());
            }
            try (FileOutputStream fos = new FileOutputStream(tmp2)) {
                fos.write("data B".getBytes());
            }
            String hash1 = BiosManager.computeSha256(tmp1);
            String hash2 = BiosManager.computeSha256(tmp2);
            assertNotEquals(hash1, hash2);
        } finally {
            tmp1.delete();
            tmp2.delete();
        }
    }

    @Test
    public void computeSha256_nonexistentFile_returnsNull() {
        File fake = new File("/nonexistent/file.bin");
        assertNull(BiosManager.computeSha256(fake));
    }

    // --- createBiosPickerIntent ---

    @Test
    public void createBiosPickerIntent_notNull() {
        assertNotNull(BiosManager.createBiosPickerIntent());
    }
}
