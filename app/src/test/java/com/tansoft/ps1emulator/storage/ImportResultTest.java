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

import com.tansoft.ps1emulator.data.GameEntity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ImportResultTest {

    @Test
    public void success_statusIsSuccess() {
        GameEntity game = new GameEntity();
        game.title = "Test Game";
        ImportResult result = ImportResult.success(game);
        assertEquals(ImportResult.Status.SUCCESS, result.status);
        assertSame(game, result.game);
        assertTrue(result.warnings.isEmpty());
        assertNull(result.errorTitle);
        assertNull(result.errorMessage);
    }

    @Test
    public void success_isPlayable() {
        ImportResult result = ImportResult.success(new GameEntity());
        assertTrue(result.isPlayable());
    }

    @Test
    public void duplicate_statusIsDuplicate() {
        GameEntity game = new GameEntity();
        ImportResult result = ImportResult.duplicate(game);
        assertEquals(ImportResult.Status.DUPLICATE, result.status);
        assertSame(game, result.game);
        assertTrue(result.isPlayable());
    }

    @Test
    public void noGameFound_statusIsNoGameFound() {
        ImportResult result = ImportResult.noGameFound("test.zip");
        assertEquals(ImportResult.Status.NO_GAME_FOUND, result.status);
        assertNull(result.game);
        assertFalse(result.isPlayable());
        assertNotNull(result.errorTitle);
        assertNotNull(result.errorMessage);
        assertTrue(result.errorMessage.contains("test.zip"));
    }

    @Test
    public void partialImport_withWarnings() {
        GameEntity game = new GameEntity();
        game.title = "Partial";
        ImportResult result = ImportResult.partialImport(game,
                Arrays.asList("Missing BIN", "Bad CUE"));
        assertEquals(ImportResult.Status.PARTIAL_IMPORT, result.status);
        assertSame(game, result.game);
        assertEquals(2, result.warnings.size());
        assertTrue(result.isPlayable());
        assertTrue(result.errorMessage.contains("Missing BIN"));
    }

    @Test
    public void extractionFailed_notPlayable() {
        ImportResult result = ImportResult.extractionFailed("bad.7z", "IO error");
        assertEquals(ImportResult.Status.EXTRACTION_FAILED, result.status);
        assertFalse(result.isPlayable());
        assertEquals("Extraction Failed", result.errorTitle);
    }

    @Test
    public void corrupted_notPlayable() {
        ImportResult result = ImportResult.corrupted("broken.zip");
        assertEquals(ImportResult.Status.CORRUPTED, result.status);
        assertFalse(result.isPlayable());
        assertEquals("Archive Corrupted", result.errorTitle);
    }

    @Test
    public void warnings_areUnmodifiable() {
        ImportResult result = ImportResult.partialImport(new GameEntity(),
                Collections.singletonList("warn"));
        try {
            result.warnings.add("new");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
