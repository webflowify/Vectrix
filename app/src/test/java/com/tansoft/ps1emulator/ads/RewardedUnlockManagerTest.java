package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardedUnlockManagerTest {

    private RewardedUnlockManager manager;

    @Before
    public void setUp() {
        manager = RewardedUnlockManager.getInstance();
        manager.clearSession();
    }

    @Test
    public void saveSlot_freeBelowLimit() {
        assertTrue(manager.isFeatureFree("save_slot", 0));
        assertTrue(manager.isFeatureFree("save_slot", 1));
        assertTrue(manager.isFeatureFree("save_slot", 2));
    }

    @Test
    public void saveSlot_notFreeAtLimit() {
        assertFalse(manager.isFeatureFree("save_slot", 3));
        assertFalse(manager.isFeatureFree("save_slot", 9));
    }

    @Test
    public void unknownFeature_isNotFree() {
        assertFalse(manager.isFeatureFree("fast_forward", 1));
        assertFalse(manager.isFeatureFree("fast_forward", 16));
        assertFalse(manager.isFeatureFree("slow_motion", 0));
    }

    @Test
    public void sessionRewardedCount_enforcesLimit() {
        assertTrue(manager.canShowSessionRewarded());
        for (int i = 0; i < AdsConfig.MAX_REWARDED_PER_SESSION; i++) {
            manager.incrementSessionRewarded();
        }
        assertFalse(manager.canShowSessionRewarded());
    }

    @Test
    public void nullFeatureName_returnsFalse() {
        assertFalse(manager.isFeatureFree(null, 0));
    }

    @Test
    public void negativeValue_belowFreeSaveSlots() {
        assertTrue(manager.isFeatureFree("save_slot", -1));
    }

    @Test
    public void clearSessionTwice_noCrash() {
        manager.clearSession();
        manager.clearSession();
        assertTrue(manager.canShowSessionRewarded());
    }
}
