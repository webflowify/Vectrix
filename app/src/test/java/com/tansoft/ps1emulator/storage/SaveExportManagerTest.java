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

import java.lang.reflect.Method;

public class SaveExportManagerTest {

    // --- sanitizeFileName (private, test via reflection) ---

    @Test
    public void sanitizeFileName_normalName() throws Exception {
        Method method = SaveExportManager.class.getDeclaredMethod("sanitizeFileName", String.class);
        method.setAccessible(true);

        assertEquals("Test Game", method.invoke(null, "Test Game"));
    }

    @Test
    public void sanitizeFileName_specialChars() throws Exception {
        Method method = SaveExportManager.class.getDeclaredMethod("sanitizeFileName", String.class);
        method.setAccessible(true);

        assertEquals("Test_Game_v1.0", method.invoke(null, "Test/Game:v1.0"));
    }

    @Test
    public void sanitizeFileName_spaces() throws Exception {
        Method method = SaveExportManager.class.getDeclaredMethod("sanitizeFileName", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "  spaces  ");
        assertEquals("spaces", result);
    }

    @Test
    public void sanitizeFileName_unicodeChars() throws Exception {
        Method method = SaveExportManager.class.getDeclaredMethod("sanitizeFileName", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "Game: \u00e9\u00e8\u00ea");
        assertTrue(result.startsWith("Game_"));
    }

    // --- ExportResult defaults ---

    @Test
    public void exportResult_defaults() {
        SaveExportManager.ExportResult result = new SaveExportManager.ExportResult();
        assertEquals(0, result.filesExported);
        assertFalse(result.success);
        assertNotNull(result.warnings);
        assertTrue(result.warnings.isEmpty());
    }

    @Test
    public void exportResult_warningsListIsModifiable() {
        SaveExportManager.ExportResult result = new SaveExportManager.ExportResult();
        result.warnings.add("test warning");
        assertEquals(1, result.warnings.size());
    }
}
