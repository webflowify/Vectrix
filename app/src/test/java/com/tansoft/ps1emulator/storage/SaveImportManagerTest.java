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
import java.lang.reflect.Method;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SaveImportManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // --- parseSlotNumber (private, test via reflection) ---

    @Test
    public void parseSlotNumber_validFile() throws Exception {
        Method method = SaveImportManager.class.getDeclaredMethod("parseSlotNumber", String.class);
        method.setAccessible(true);

        assertEquals(0, method.invoke(null, "slot_0.psst"));
        assertEquals(5, method.invoke(null, "slot_5.psst"));
        assertEquals(9, method.invoke(null, "slot_9.psst"));
    }

    @Test
    public void parseSlotNumber_upperCase() throws Exception {
        Method method = SaveImportManager.class.getDeclaredMethod("parseSlotNumber", String.class);
        method.setAccessible(true);

        assertEquals(3, method.invoke(null, "SLOT_3.PSST"));
    }

    @Test
    public void parseSlotNumber_invalidFormat() throws Exception {
        Method method = SaveImportManager.class.getDeclaredMethod("parseSlotNumber", String.class);
        method.setAccessible(true);

        assertEquals(-1, method.invoke(null, "game.psst"));
        assertEquals(-1, method.invoke(null, "slot_.psst"));
        assertEquals(-1, method.invoke(null, "slot_abc.psst"));
    }

    @Test
    public void parseSlotNumber_null() throws Exception {
        Method method = SaveImportManager.class.getDeclaredMethod("parseSlotNumber", String.class);
        method.setAccessible(true);

        assertEquals(-1, method.invoke(null, (String) null));
    }

    // --- findFreeSlot ---

    @Test
    public void findFreeSlot_emptyDir() throws Exception {
        File dir = SaveStateManager.getSaveDir(context, "FREE_SLOT_TEST");
        dir.mkdirs();

        Method method = SaveImportManager.class.getDeclaredMethod("findFreeSlot", File.class);
        method.setAccessible(true);

        int slot = (int) method.invoke(null, dir);
        assertEquals(0, slot);
    }

    @Test
    public void findFreeSlot_partialUsed() throws Exception {
        String discId = "PARTIAL_TEST";
        File dir = SaveStateManager.getSaveDir(context, discId);
        dir.mkdirs();

        // Create slots 0, 1, 2
        new File(dir, "slot_0.psst").createNewFile();
        new File(dir, "slot_1.psst").createNewFile();
        new File(dir, "slot_2.psst").createNewFile();

        Method method = SaveImportManager.class.getDeclaredMethod("findFreeSlot", File.class);
        method.setAccessible(true);

        int slot = (int) method.invoke(null, dir);
        assertEquals(3, slot);
    }

    @Test
    public void findFreeSlot_allUsed() throws Exception {
        String discId = "FULL_TEST";
        File dir = SaveStateManager.getSaveDir(context, discId);
        dir.mkdirs();

        for (int i = 0; i < SaveStateManager.MAX_SLOTS; i++) {
            new File(dir, "slot_" + i + ".psst").createNewFile();
        }

        Method method = SaveImportManager.class.getDeclaredMethod("findFreeSlot", File.class);
        method.setAccessible(true);

        int slot = (int) method.invoke(null, dir);
        assertEquals(-1, slot);
    }

    // --- ImportResult defaults ---

    @Test
    public void importResult_defaults() {
        SaveImportManager.ImportResult result = new SaveImportManager.ImportResult();
        assertEquals(0, result.cardsImported);
        assertEquals(0, result.statesImported);
        assertFalse(result.success);
        assertNotNull(result.warnings);
        assertTrue(result.warnings.isEmpty());
    }
}
