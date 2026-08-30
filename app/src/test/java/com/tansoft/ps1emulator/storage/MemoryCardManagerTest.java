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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MemoryCardManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getCardFile_card1MatchesCoreSharedName() {
        File file = MemoryCardManager.getCardFile(context, "SCUS94423", 0);
        assertTrue(file.getAbsolutePath().contains("memcards"));
        assertEquals("pcsx-card1.mcd", file.getName());
    }

    @Test
    public void getCardFile_card2MatchesCoreSharedName() {
        File file = MemoryCardManager.getCardFile(context, "SLUS01234", 1);
        assertEquals("pcsx-card2.mcd", file.getName());
    }

    @Test
    public void initializeCard_ensuresDirectoryButDoesNotCreateCardFile() {
        // The core (CreateMcd) creates and formats the card on first load, so the
        // Java side must NOT pre-create a blank file here.
        String discId = "NO_PRECREATE";
        MemoryCardManager.initializeCard(context, discId, 0);
        File file = MemoryCardManager.getCardFile(context, discId, 0);
        assertFalse("card file must not be pre-created by initializeCard", file.exists());
        assertTrue("memcards directory must exist", file.getParentFile().exists());
    }

    @Test
    public void cardExists_falseWhenNotCreated() {
        assertFalse(MemoryCardManager.cardExists(context, "NONEXISTENT", 0));
    }

    @Test
    public void deleteCard_removesExistingFile() throws IOException {
        String discId = "DELETE_TEST";
        File file = MemoryCardManager.getCardFile(context, discId, 0);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[1024]);
        }
        assertTrue(MemoryCardManager.cardExists(context, discId, 0));

        boolean deleted = MemoryCardManager.deleteCard(context, discId, 0);
        assertTrue(deleted);
        assertFalse(MemoryCardManager.cardExists(context, discId, 0));
    }
}
