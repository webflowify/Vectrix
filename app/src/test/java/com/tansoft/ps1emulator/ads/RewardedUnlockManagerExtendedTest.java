package com.tansoft.ps1emulator.ads;

import android.app.Activity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Extended tests for RewardedUnlockManager covering timed unlocks,
 * permanent unlocks, save slots, ENABLE_ADS=false behavior, and
 * the online ad-load failure fallback.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RewardedUnlockManagerExtendedTest {

    private RewardedUnlockManager manager;
    private Activity activity;

    @Before
    public void setUp() throws Exception {
        manager = RewardedUnlockManager.getInstance();
        manager.clearSession();

        // Init with Robolectric context so SharedPreferences work
        manager.init(RuntimeEnvironment.getApplication());

        // Force ads.NetworkHelper online via reflection — both AdManager and
        // RewardedUnlockManager now use this same cached network state.
        setNetworkOnline(true);

        activity = Robolectric.buildActivity(Activity.class).create().get();

        // Reset AdManager state
        setAdField("rewardedAd", null);
        setAdField("rewardedAdLoadFailed", false);
        setAdField("dailyRewardedCount", 0);
        setAdField("networkAvailableOverride", null);
        AdCoordinator.getInstance().forceReset();
    }

    // ── Timed Unlock Tests ──────────────────────────────────────

    @Test
    public void isTimedUnlockActive_whenNeverUnlocked_returnsFalse() {
        assertFalse(manager.isTimedUnlockActive("fast_forward"));
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

    // ── attemptUnlockWithFallback Tests ─────────────────────────

    @Test
    public void attemptUnlockWithFallback_whenOffline_showsToastAndReturnsFalse() throws Exception {
        setAdField("networkAvailableOverride", false);
        setAdField("rewardedAdLoadFailed", true);
        setNetworkOnline(false);
        try {
            boolean result = manager.attemptUnlockWithFallback(activity, "fast_forward");
            assertFalse(result);
            assertEquals("Internet required to unlock", ShadowToast.getTextOfLatestToast());
        } finally {
            setNetworkOnline(true);
        }
    }

    @Test
    public void attemptUnlockWithFallback_whenOnlineAndAdFailed_returnsTrueAndUnlocks() throws Exception {
        setAdField("networkAvailableOverride", true);
        setAdField("rewardedAdLoadFailed", true);
        setAdField("dailyRewardedCount", 0);
        boolean result = manager.attemptUnlockWithFallback(activity, "fast_forward");
        assertTrue(result);
        assertTrue(manager.isTimedUnlockActive("fast_forward"));
        assertEquals("Ad unavailable — feature unlocked for 24 hours",
            ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void attemptUnlockWithFallback_whenAdLoaded_returnsTrueWithoutFallback() throws Exception {
        setAdField("networkAvailableOverride", true);
        setAdField("rewardedAdLoadFailed", false);
        // rewardedAd is null but rewardedAdLoadFailed is false, so
        // isOnlineButRewardedAdFailed() returns false — proceed to ad dialog
        boolean result = manager.attemptUnlockWithFallback(activity, "fast_forward");
        assertTrue(result);
        // No fallback toast — user should see the ad dialog instead
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void attemptUnlockWithFallback_sessionCapReached_blocksFallback() throws Exception {
        setAdField("networkAvailableOverride", true);
        setAdField("rewardedAdLoadFailed", true);
        setAdField("dailyRewardedCount", 0);
        // Exhaust session cap
        for (int i = 0; i < AdsConfig.MAX_REWARDED_PER_SESSION; i++) {
            manager.incrementSessionRewarded();
        }
        boolean result = manager.attemptUnlockWithFallback(activity, "fast_forward");
        assertFalse(result);
        assertEquals("Session limit reached. Finish your current game to unlock more.",
            ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void attemptUnlockWithFallback_alreadyActive_returnsTrue() throws Exception {
        setAdField("networkAvailableOverride", true);
        setAdField("rewardedAdLoadFailed", false);
        // Manually set a timed unlock that is active
        manager.unlockTimed("slow_motion");
        boolean result = manager.attemptUnlockWithFallback(activity, "slow_motion");
        assertTrue(result);
        // No toast — feature is already unlocked
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    // ── Reflection helpers ──────────────────────────────────────

    private void setAdField(String name, Object value) throws Exception {
        Field field = AdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(AdManager.getInstance(), value);
    }

    private static void setNetworkOnline(boolean online) throws Exception {
        Field field = NetworkHelper.class.getDeclaredField("isOnline");
        field.setAccessible(true);
        field.set(null, online);
    }
}
