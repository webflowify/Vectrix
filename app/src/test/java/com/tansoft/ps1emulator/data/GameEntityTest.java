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
package com.tansoft.ps1emulator.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class GameEntityTest {

    @Test
    public void equals_sameObject() {
        GameEntity a = createGame(1, "Test");
        assertEquals(a, a);
    }

    @Test
    public void equals_equalFields() {
        GameEntity a = createGame(1, "Test");
        GameEntity b = createGame(1, "Test");
        assertEquals(a, b);
    }

    @Test
    public void equals_differentId() {
        GameEntity a = createGame(1, "Test");
        GameEntity b = createGame(2, "Test");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentTitle() {
        GameEntity a = createGame(1, "Game A");
        GameEntity b = createGame(1, "Game B");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentPlayCount() {
        GameEntity a = createGame(1, "Test");
        GameEntity b = createGame(1, "Test");
        b.playCount = 5;
        assertNotEquals(a, b);
    }

    @Test
    public void equals_null() {
        GameEntity a = createGame(1, "Test");
        assertNotEquals(a, null);
    }

    @Test
    public void equals_differentClass() {
        GameEntity a = createGame(1, "Test");
        assertNotEquals(a, "not a game");
    }

    @Test
    public void hashCode_consistent() {
        GameEntity a = createGame(1, "Test");
        int h1 = a.hashCode();
        int h2 = a.hashCode();
        assertEquals(h1, h2);
    }

    @Test
    public void hashCode_equalObjects_sameHash() {
        GameEntity a = createGame(1, "Test");
        GameEntity b = createGame(1, "Test");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCode_differentObjects_likelyDifferentHash() {
        GameEntity a = createGame(1, "Game A");
        GameEntity b = createGame(2, "Game B");
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    private GameEntity createGame(long id, String title) {
        GameEntity g = new GameEntity();
        g.id = id;
        g.title = title;
        g.romPath = "/path/to/rom";
        g.discId = "SCUS-94423";
        g.addedDate = 1000L;
        g.lastPlayedDate = 2000L;
        g.playCount = 1;
        g.totalPlayTimeMs = 30000L;
        return g;
    }
}
