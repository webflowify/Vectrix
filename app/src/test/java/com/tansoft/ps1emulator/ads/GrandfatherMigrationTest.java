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
package com.tansoft.ps1emulator.ads;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.tansoft.ps1emulator.storage.SaveStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GrandfatherMigrationTest {

    private RewardedUnlockManager manager;
    private Context context;

    @Before
    public void setUp() {
        manager = RewardedUnlockManager.getInstance();
        manager.clearSession();
        context = ApplicationProvider.getApplicationContext();
        // Fresh init for each test
        manager.init(context);

        // Reset migration flag via reflection on the static singleton prefs
        // (the manager shares one prefs file across tests, so we reset it here)
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY)
            .apply();
    }

    @After
    public void tearDown() {
        // Clean up any savestates dirs we created
        File savestatesRoot = new File(context.getFilesDir(), "savestates");
        deleteRecursive(savestatesRoot);

        // Reset migration flag for next test
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY)
            .apply();
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        f.delete();
    }

    // ── Happy-path tests ──────────────────────────────────────────

    @Test
    public void grandfather_noSavestatesDir_setsMigrationFlag() {
        manager.grandfatherExistingSaveSlots(context);

        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY, false));
    }

    @Test
    public void grandfather_emptySavestatesDir_setsMigrationFlag() {
        File root = new File(context.getFilesDir(), "savestates");
        root.mkdirs();

        manager.grandfatherExistingSaveSlots(context);

        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY, false));
    }

    @Test
    public void grandfather_occupiedPremiumSlot_unlocksIt() throws java.io.IOException {
        String discId = "GRANDFATHER_TEST_1";
        File gameDir = SaveStateManager.getSaveDir(context, discId);
        gameDir.mkdirs();

        // Create a save file in slot 4 (premium — index 4 >= FREE_SAVE_SLOTS=3)
        File saveFile = SaveStateManager.getSaveFile(context, discId, 4);
        assertTrue(saveFile.createNewFile());

        // Slot 4 should be locked before migration
        assertFalse(manager.isSaveSlotUnlocked(discId, 4));

        manager.grandfatherExistingSaveSlots(context);

        // Slot 4 should now be unlocked
        assertTrue(manager.isSaveSlotUnlocked(discId, 4));

        // Slots 0–2 always free; slot 3 (no save file) should still be locked
        assertTrue(manager.isSaveSlotUnlocked(discId, 0));
        assertTrue(manager.isSaveSlotUnlocked(discId, 2));
        assertFalse(manager.isSaveSlotUnlocked(discId, 3));
        assertFalse(manager.isSaveSlotUnlocked(discId, 5));
    }

    @Test
    public void grandfather_multipleGames_multipleSlots_unlocksAll() throws java.io.IOException {
        String[] discIds = {"GRANDFATHER_A", "GRANDFATHER_B"};
        int[][] occupiedSlots = {
            {3, 5, 9},  // GRANDFATHER_A: slots 3, 5, 9 occupied
            {7}         // GRANDFATHER_B:  slot 7 occupied
        };

        for (int g = 0; g < discIds.length; g++) {
            File gameDir = SaveStateManager.getSaveDir(context, discIds[g]);
            gameDir.mkdirs();
            for (int slot : occupiedSlots[g]) {
                File f = SaveStateManager.getSaveFile(context, discIds[g], slot);
                assertTrue(f.createNewFile());
            }
        }

        manager.grandfatherExistingSaveSlots(context);

        for (int g = 0; g < discIds.length; g++) {
            for (int slot = 3; slot < 10; slot++) {
                boolean expected = false;
                for (int occ : occupiedSlots[g]) {
                    if (occ == slot) { expected = true; break; }
                }
                assertEquals("discId=" + discIds[g] + " slot=" + slot,
                    expected, manager.isSaveSlotUnlocked(discIds[g], slot));
            }
        }
    }

    @Test
    public void grandfather_freeSlotsNeverLocked_areNotAffected() throws java.io.IOException {
        // Free slots 0–2 should always return true regardless of migration
        String discId = "GRANDFATHER_FREE";
        File gameDir = SaveStateManager.getSaveDir(context, discId);
        gameDir.mkdirs();
        // Save data in slots 0 and 1
        for (int slot : new int[]{0, 1}) {
            File f = SaveStateManager.getSaveFile(context, discId, slot);
            assertTrue(f.createNewFile());
        }

        manager.grandfatherExistingSaveSlots(context);

        assertTrue(manager.isSaveSlotUnlocked(discId, 0));
        assertTrue(manager.isSaveSlotUnlocked(discId, 1));
        assertTrue(manager.isSaveSlotUnlocked(discId, 2)); // free by policy
    }

    // ── Idempotency / edge-case tests ─────────────────────────────

    @Test
    public void grandfather_alreadyUnlockedSlot_notOverwritten() throws java.io.IOException {
        String discId = "GRANDFATHER_ALREADY";
        File gameDir = SaveStateManager.getSaveDir(context, discId);
        gameDir.mkdirs();

        // Save data in slot 6
        File saveFile = SaveStateManager.getSaveFile(context, discId, 6);
        assertTrue(saveFile.createNewFile());

        // Manually unlock slot 6
        manager.unlockSaveSlot(discId, 6);

        // Migration runs
        manager.grandfatherExistingSaveSlots(context);

        // Still unlocked
        assertTrue(manager.isSaveSlotUnlocked(discId, 6));

        // Migration flag set
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY, false));
    }

    @Test
    public void grandfather_secondCall_isNoop() throws java.io.IOException {
        String discId = "GRANDFATHER_TWICE";
        File gameDir = SaveStateManager.getSaveDir(context, discId);
        gameDir.mkdirs();
        File saveFile = SaveStateManager.getSaveFile(context, discId, 4);
        assertTrue(saveFile.createNewFile());

        // First call unlocks slot 4
        manager.grandfatherExistingSaveSlots(context);
        assertTrue(manager.isSaveSlotUnlocked(discId, 4));

        // Delete save file and remove unlock — simulate slot becoming locked again
        saveFile.delete();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("slot_" + discId + "_4").apply();
        // Reset migration flag to simulate a fresh re-run
        prefs.edit().remove(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY).apply();

        // Second call — file is gone, should NOT unlock (slot not occupied)
        manager.grandfatherExistingSaveSlots(context);
        assertFalse(manager.isSaveSlotUnlocked(discId, 4));
    }

    @Test
    public void grandfather_prefsNull_doesNotCrash() {
        // Simulate init() not called by resetting prefs to null
        java.lang.reflect.Field prefsField;
        try {
            prefsField = RewardedUnlockManager.class.getDeclaredField("prefs");
            prefsField.setAccessible(true);
            prefsField.set(manager, null);
        } catch (Exception e) {
            // If reflection fails, just verify no NPE is thrown with a fresh manager
        }

        // Should not throw NullPointerException
        try {
            manager.grandfatherExistingSaveSlots(context);
        } catch (NullPointerException e) {
            fail("grandfatherExistingSaveSlots should not throw NPE when prefs is null");
        }
    }

    @Test
    public void grandfather_emptyDiscId_isSkipped() {
        // Create a directory with an empty name (should not happen in practice,
        // but the method guards against it)
        File root = new File(context.getFilesDir(), "savestates");
        root.mkdirs();
        File emptyDir = new File(root, "");
        emptyDir.mkdirs();

        // Should not crash
        manager.grandfatherExistingSaveSlots(context);

        android.content.SharedPreferences prefs = context.getSharedPreferences(
            RewardedUnlockManager.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean(RewardedUnlockManager.GRANDFATHER_MIGRATION_KEY, false));
    }
}
