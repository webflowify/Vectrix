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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class GameDiscoveryResultTest {

    @Test
    public void cueBinFound_statusAndFiles() {
        File cue = new File("game.cue");
        File bin = new File("game.bin");
        GameDiscoveryResult result = GameDiscoveryResult.cueBinFound(cue,
                Collections.singletonList(bin), null);
        assertEquals(GameDiscoveryResult.Status.FOUND_CUE_BIN, result.status);
        assertSame(cue, result.primaryFile);
        assertSame(cue, result.cueFile);
        assertEquals(1, result.binFiles.size());
        assertSame(bin, result.binFiles.get(0));
        assertTrue(result.isPlayable());
    }

    @Test
    public void cueBinFound_withWarnings() {
        File cue = new File("game.cue");
        GameDiscoveryResult result = GameDiscoveryResult.cueBinFound(cue, null,
                Arrays.asList("Missing BIN", "Bad path"));
        assertEquals(2, result.warnings.size());
        assertTrue(result.isPlayable());
    }

    @Test
    public void isoFound() {
        File iso = new File("game.iso");
        GameDiscoveryResult result = GameDiscoveryResult.isoFound(iso);
        assertEquals(GameDiscoveryResult.Status.FOUND_ISO, result.status);
        assertSame(iso, result.primaryFile);
        assertNull(result.cueFile);
        assertTrue(result.binFiles.isEmpty());
        assertTrue(result.isPlayable());
    }

    @Test
    public void chdFound() {
        File chd = new File("game.chd");
        GameDiscoveryResult result = GameDiscoveryResult.chdFound(chd);
        assertEquals(GameDiscoveryResult.Status.FOUND_CHD, result.status);
        assertSame(chd, result.primaryFile);
        assertTrue(result.isPlayable());
    }

    @Test
    public void binOnlyFound() {
        File bin = new File("game.bin");
        GameDiscoveryResult result = GameDiscoveryResult.binOnlyFound(bin,
                Collections.singletonList("No CUE"));
        assertEquals(GameDiscoveryResult.Status.FOUND_BIN_ONLY, result.status);
        assertSame(bin, result.primaryFile);
        assertEquals(1, result.binFiles.size());
        assertTrue(result.isPlayable());
    }

    @Test
    public void executableFound() {
        File exe = new File("game.exe");
        GameDiscoveryResult result = GameDiscoveryResult.executableFound(exe);
        assertEquals(GameDiscoveryResult.Status.FOUND_EXECUTABLE, result.status);
        assertSame(exe, result.primaryFile);
        assertTrue(result.isPlayable());
    }

    @Test
    public void multiDiscFound() {
        File m3u = new File("discs.m3u");
        File bin1 = new File("disc1.bin");
        File bin2 = new File("disc2.bin");
        GameDiscoveryResult result = GameDiscoveryResult.multiDiscFound(m3u,
                Arrays.asList(bin1, bin2));
        assertEquals(GameDiscoveryResult.Status.FOUND_MULTI_DISC, result.status);
        assertSame(m3u, result.primaryFile);
        assertEquals(2, result.binFiles.size());
        assertTrue(result.isPlayable());
    }

    @Test
    public void noGameFound_notPlayable() {
        GameDiscoveryResult result = GameDiscoveryResult.noGameFound("Empty archive");
        assertEquals(GameDiscoveryResult.Status.NO_GAME_FOUND, result.status);
        assertNull(result.primaryFile);
        assertFalse(result.isPlayable());
        assertEquals("Empty archive", result.errorDetail);
    }

    @Test
    public void corrupted_notPlayable() {
        GameDiscoveryResult result = GameDiscoveryResult.corrupted("CRC mismatch");
        assertEquals(GameDiscoveryResult.Status.CORRUPTED, result.status);
        assertFalse(result.isPlayable());
    }

    @Test
    public void binFiles_areUnmodifiable() {
        File bin = new File("game.bin");
        GameDiscoveryResult result = GameDiscoveryResult.binOnlyFound(bin, null);
        try {
            result.binFiles.add(new File("extra.bin"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    public void warnings_areUnmodifiable() {
        GameDiscoveryResult result = GameDiscoveryResult.cueBinFound(
                new File("a.cue"), null, Collections.singletonList("w"));
        try {
            result.warnings.add("new");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
