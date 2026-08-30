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
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SaveStateManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getSaveDir_returnsCorrectPath() {
        File dir = SaveStateManager.getSaveDir(context, "SCUS94423");
        assertTrue(dir.getAbsolutePath().contains("savestates"));
        assertTrue(dir.getAbsolutePath().contains("SCUS94423"));
    }

    @Test
    public void getSaveFile_returnsCorrectFileName() {
        File file = SaveStateManager.getSaveFile(context, "SCUS94423", 3);
        assertEquals("slot_3.psst", file.getName());
    }

    @Test
    public void getThumbnailFile_returnsCorrectFileName() {
        File file = SaveStateManager.getThumbnailFile(context, "SCUS94423", 3);
        assertEquals("slot_3.png", file.getName());
    }

    @Test
    public void listSlots_emptyWhenNoDir() {
        // Use a fresh game ID that won't have any saves
        List<SaveStateManager.SlotInfo> slots =
                SaveStateManager.listSlots(context, "EMPTY_GAME_ID");
        assertNotNull(slots);
        assertTrue(slots.isEmpty());
    }

    @Test
    public void listSlots_findsSaveFiles() {
        String discId = "LIST_TEST";
        File dir = SaveStateManager.getSaveDir(context, discId);
        dir.mkdirs();

        // Create some save files
        for (int i = 0; i < 3; i++) {
            new File(dir, "slot_" + i + ".psst").mkdirs();
        }

        List<SaveStateManager.SlotInfo> slots = SaveStateManager.listSlots(context, discId);
        assertEquals(3, slots.size());
    }

    @Test
    public void deleteState_removesFiles() {
        String discId = "DELETE_STATE";
        File dir = SaveStateManager.getSaveDir(context, discId);
        dir.mkdirs();
        new File(dir, "slot_0.psst").mkdirs();
        new File(dir, "slot_0.png").mkdirs();

        SaveStateManager.deleteState(context, discId, 0);
        assertFalse(new File(dir, "slot_0.psst").exists());
        assertFalse(new File(dir, "slot_0.png").exists());
    }

    @Test
    public void maxSlots_isTen() {
        assertEquals(10, SaveStateManager.MAX_SLOTS);
    }

    @Test
    public void slotInfo_isEmpty_byDefault() {
        SaveStateManager.SlotInfo info = new SaveStateManager.SlotInfo();
        info.exists = false;
        assertTrue(info.isEmpty());
    }

    @Test
    public void slotInfo_isNotEmpty_whenExists() {
        SaveStateManager.SlotInfo info = new SaveStateManager.SlotInfo();
        info.exists = true;
        assertFalse(info.isEmpty());
    }
}
