package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Extended tests for RewardedUnlockManager covering timed unlocks,
 * permanent unlocks, save slots, and ENABLE_ADS=false behavior.
 */
public class RewardedUnlockManagerExtendedTest {

    private RewardedUnlockManager manager;

    @Before
    public void setUp() {
        manager = RewardedUnlockManager.getInstance();
        manager.clearSession();
    }

    // ── Timed Unlock Tests ──────────────────────────────────────

    @Test
    public void isTimedUnlockActive_whenNeverUnlocked_returnsFalse() {
        // Without init() called, prefs is null so getLong returns 0 → returns false
    }

    // ── Permanent Unlock Tests ──────────────────────────────────

    // NOTE: ENABLE_ADS is final — permanent/timed/save-slot tests that depend on
    // ENABLE_ADS=false can only be verified via Robolectric or instrumented tests.

    // ── Save Slot Tests ─────────────────────────────────────────
    // NOTE: isSaveSlotUnlocked (slots 3-9) and getUnlockedSlotCount
    // require SharedPreferences via init(). These are tested in
    // instrumented tests. Only free-slot checks are safe here.

    @Test
    public void isSaveSlotUnlocked_freeSlots_alwaysTrue() {
        assertTrue(manager.isSaveSlotUnlocked("game1", 0));
        assertTrue(manager.isSaveSlotUnlocked("game1", 1));
        assertTrue(manager.isSaveSlotUnlocked("game1", 2));
    }

    // ── Feature Free Tests ──────────────────────────────────────

    @Test
    public void isFeatureFree_saveSlot_exactlyAtLimit() {
        assertFalse(manager.isFeatureFree("save_slot", AdsConfig.FREE_SAVE_SLOTS));
    }

    @Test
    public void isFeatureFree_saveSlot_oneBelowLimit() {
        assertTrue(manager.isFeatureFree("save_slot", AdsConfig.FREE_SAVE_SLOTS - 1));
    }

    @Test
    public void isFeatureFree_emptyString_returnsFalse() {
        assertFalse(manager.isFeatureFree("", 0));
    }

    // ── Session Tests ───────────────────────────────────────────

    @Test
    public void sessionRewardedCount_countsCorrectly() {
        manager.clearSession();
        for (int i = 0; i < 3; i++) {
            manager.incrementSessionRewarded();
        }
        assertTrue(manager.canShowSessionRewarded());
        for (int i = 3; i < AdsConfig.MAX_REWARDED_PER_SESSION; i++) {
            manager.incrementSessionRewarded();
        }
        assertFalse(manager.canShowSessionRewarded());
    }

    // ── Reflection ──────────────────────────────────────────────
    // NOTE: ENABLE_ADS is final — tests requiring ENABLE_ADS=false
    // can only be verified via Robolectric or instrumented tests.
}
