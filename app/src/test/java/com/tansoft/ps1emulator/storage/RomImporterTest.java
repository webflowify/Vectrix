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

public class RomImporterTest {

    // --- isSupportedExtension ---

    @Test
    public void isSupportedExtension_bin() {
        assertTrue(RomImporter.isSupportedExtension("bin"));
    }

    @Test
    public void isSupportedExtension_cue() {
        assertTrue(RomImporter.isSupportedExtension("cue"));
    }

    @Test
    public void isSupportedExtension_iso() {
        assertTrue(RomImporter.isSupportedExtension("iso"));
    }

    @Test
    public void isSupportedExtension_chd() {
        assertTrue(RomImporter.isSupportedExtension("chd"));
    }

    @Test
    public void isSupportedExtension_zip() {
        assertTrue(RomImporter.isSupportedExtension("zip"));
    }

    @Test
    public void isSupportedExtension_7z() {
        assertTrue(RomImporter.isSupportedExtension("7z"));
    }

    @Test
    public void isSupportedExtension_exe() {
        assertTrue(RomImporter.isSupportedExtension("exe"));
    }

    @Test
    public void isSupportedExtension_m3u() {
        assertTrue(RomImporter.isSupportedExtension("m3u"));
    }

    @Test
    public void isSupportedExtension_pbp() {
        assertTrue(RomImporter.isSupportedExtension("pbp"));
    }

    @Test
    public void isSupportedExtension_caseInsensitive() {
        assertTrue(RomImporter.isSupportedExtension("BIN"));
        assertTrue(RomImporter.isSupportedExtension("Iso"));
        assertTrue(RomImporter.isSupportedExtension("CHD"));
    }

    @Test
    public void isSupportedExtension_unsupported() {
        assertFalse(RomImporter.isSupportedExtension("mp3"));
        assertFalse(RomImporter.isSupportedExtension("txt"));
        assertFalse(RomImporter.isSupportedExtension("png"));
        assertFalse(RomImporter.isSupportedExtension("apk"));
    }

    @Test
    public void isSupportedExtension_null() {
        assertFalse(RomImporter.isSupportedExtension(null));
    }

    @Test
    public void isSupportedExtension_empty() {
        assertFalse(RomImporter.isSupportedExtension(""));
    }

    // --- extractDiscIdFromText ---

    @Test
    public void extractDiscIdFromText_scusFormat() {
        String id = RomImporter.extractDiscIdFromText("SCUS-94423 some game data");
        assertEquals("SCUS-94423", id);
    }

    @Test
    public void extractDiscIdFromText_slusFormat() {
        String id = RomImporter.extractDiscIdFromText("SLUS-01234");
        assertEquals("SLUS-01234", id);
    }

    @Test
    public void extractDiscIdFromText_scesFormat() {
        String id = RomImporter.extractDiscIdFromText("SCES-52468");
        assertEquals("SCES-52468", id);
    }

    @Test
    public void extractDiscIdFromText_noPrefix_returnsNull() {
        String id = RomImporter.extractDiscIdFromText("AAAA-12345 not a disc ID");
        assertNull(id);
    }

    @Test
    public void extractDiscIdFromText_emptyString_returnsNull() {
        assertNull(RomImporter.extractDiscIdFromText(""));
    }

    @Test
    public void extractDiscIdFromText_noMatch_returnsNull() {
        assertNull(RomImporter.extractDiscIdFromText("just some random text"));
    }

    @Test
    public void extractDiscIdFromText_underscoreFormat() {
        String id = RomImporter.extractDiscIdFromText("SCUS_94423");
        assertEquals("SCUS-94423", id);
    }

    // --- ROM_MIME_TYPES ---

    @Test
    public void romMimeTypes_notEmpty() {
        assertNotNull(RomImporter.ROM_MIME_TYPES);
        assertTrue(RomImporter.ROM_MIME_TYPES.length > 0);
    }

    // --- SUPPORTED_EXTENSIONS ---

    @Test
    public void supportedExtensions_notEmpty() {
        assertNotNull(RomImporter.SUPPORTED_EXTENSIONS);
        assertTrue(RomImporter.SUPPORTED_EXTENSIONS.length >= 10);
    }

    @Test
    public void supportedExtensionsDisplay_notEmpty() {
        assertNotNull(RomImporter.SUPPORTED_EXTENSIONS_DISPLAY);
        assertTrue(RomImporter.SUPPORTED_EXTENSIONS_DISPLAY.length() > 0);
    }
}
