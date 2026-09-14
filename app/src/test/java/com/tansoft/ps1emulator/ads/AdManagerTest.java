package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdManagerTest {

    private AdManager manager;

    @Before
    public void setUp() throws Exception {
        manager = AdManager.getInstance();
        // Provide a real context so NetworkHelper.isAvailable() works
        setField("appContext", ApplicationProvider.getApplicationContext());
        // Reset internal state via reflection for test isolation
        setField("totalOpenCount", 0);
        setField("dailyInterstitialCount", 0);
        setField("dailyRewardedCount", 0);
        setField("lastInterstitialTime", 0L);
        setField("lastRewardedTime", 0L);
        setField("interstitialAd", null);
        setField("rewardedAd", null);
        setField("networkAvailableOverride", null);

        // Reset EngagementTracker state
        EngagementTracker tracker = EngagementTracker.getInstance();
        setTrackerField("clicksSinceLastAd", 0);
        setTrackerField("totalClicks", 0);

        // Reset AdCoordinator state
        AdCoordinator.getInstance().forceReset();
    }

    @Test
    public void isAdsEnabled_matchesConfig() {
        assertEquals(AdsConfig.ENABLE_ADS, manager.isAdsEnabled());
    }

    @Test
    public void canShowInterstitial_whenAdNotLoaded_returnsFalse() throws Exception {
        setField("interstitialAd", null);
        assertFalse(manager.canShowInterstitial());
    }

    @Test
    public void canShowInterstitial_whenNotEnoughClicks_returnsTrue() throws Exception {
        setField("dailyInterstitialCount", 0);
        setTrackerField("clicksSinceLastAd", 0);
        setField("interstitialAd", mock(InterstitialAd.class));
        setField("networkAvailableOverride", true);
        assertTrue(manager.canShowInterstitial());
    }

    @Test
    public void canShowInterstitial_atDailyCap_returnsTrue() throws Exception {
        setField("dailyInterstitialCount", 99);
        setTrackerField("clicksSinceLastAd", 0);
        setField("interstitialAd", mock(InterstitialAd.class));
        setField("networkAvailableOverride", true);
        assertTrue(manager.canShowInterstitial());
    }

    @Test
    public void canShowInterstitial_whenAdLoaded_returnsTrue() throws Exception {
        setField("interstitialAd", mock(InterstitialAd.class));
        setField("dailyInterstitialCount", 0);
        setTrackerField("clicksSinceLastAd", 0);
        setField("networkAvailableOverride", true);
        assertTrue(manager.canShowInterstitial());
    }

    @Test
    public void canShowRewarded_whenAdNotLoaded_returnsFalse() throws Exception {
        setField("rewardedAd", null);
        assertFalse(manager.canShowRewarded());
    }

    @Test
    public void canShowRewarded_atDailyCap_returnsFalse() throws Exception {
        setField("dailyRewardedCount", AdsConfig.MAX_REWARDED_PER_DAY);
        assertFalse(manager.canShowRewarded());
    }

    @Test
    public void canShowRewarded_withinDailyLimit_returnsFalse_noAd() throws Exception {
        setField("dailyRewardedCount", 0);
        // Still false because rewardedAd is null
        assertFalse(manager.canShowRewarded());
    }

    @Test
    public void incrementOpenCount_increments() throws Exception {
        setField("totalOpenCount", 0);
        manager.incrementOpenCount();
        assertEquals(1, manager.getOpenCount());
        manager.incrementOpenCount();
        assertEquals(2, manager.getOpenCount());
    }

    @Test
    public void resetDailyCounts_clearsCounters() throws Exception {
        setField("dailyInterstitialCount", 3);
        setField("dailyRewardedCount", 2);
        manager.resetDailyCounts();
        assertEquals(0, getField("dailyInterstitialCount"));
        assertEquals(0, getField("dailyRewardedCount"));
    }

    @Test
    public void singleton_returnsSameInstance() {
        AdManager a = AdManager.getInstance();
        AdManager b = AdManager.getInstance();
        assertSame(a, b);
    }

    @Test
    public void clicksSinceLastAd_preservedOnAdShown() throws Exception {
        EngagementTracker tracker = EngagementTracker.getInstance();
        setTrackerField("clicksSinceLastAd", 3);
        tracker.recordAdShown();
        assertEquals(3, tracker.getClicksSinceLastAd());
    }

    @Test
    public void clicksSinceLastAd_incrementsOnRecordClick() throws Exception {
        EngagementTracker tracker = EngagementTracker.getInstance();
        setTrackerField("clicksSinceLastAd", 0);
        tracker.recordClick("ff");
        assertEquals(1, tracker.getClicksSinceLastAd());
        tracker.recordClick("sm");
        assertEquals(2, tracker.getClicksSinceLastAd());
    }

    @Test
    public void hasEnoughClicksForAd_alwaysReturnsTrue() throws Exception {
        EngagementTracker tracker = EngagementTracker.getInstance();
        setTrackerField("clicksSinceLastAd", 0);
        assertTrue(tracker.hasEnoughClicksForAd());
        setTrackerField("clicksSinceLastAd", 99);
        assertTrue(tracker.hasEnoughClicksForAd());
    }

    @Test
    public void showInterstitialThen_noAd_runsAfterImmediately() throws Exception {
        setField("interstitialAd", null);
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.showInterstitialThen(null, () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    public void showInterstitialThen_adLoaded_runsAfterOnDismiss() throws Exception {
        InterstitialAd mockAd = mock(InterstitialAd.class);
        setField("interstitialAd", mockAd);
        setField("networkAvailableOverride", true);
        final FullScreenContentCallback[] callbackHolder = new FullScreenContentCallback[1];
        doAnswer(invocation -> {
            callbackHolder[0] = invocation.getArgument(0);
            return null;
        }).when(mockAd).setFullScreenContentCallback(any());
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        manager.showInterstitialThen(null, () -> ran.set(true));
        assertFalse(ran.get());
        // Null out appContext so preloadInterstitial() inside the callback returns early
        setField("appContext", null);
        callbackHolder[0].onAdDismissedFullScreenContent();
        assertTrue(ran.get());
    }

    // ── Online Ad-Load Failure Fallback ────────────────────────

    @Test
    public void isOnlineButRewardedAdFailed_whenOnlineAndAdFailed_returnsTrue() throws Exception {
        setField("rewardedAd", null);
        setField("rewardedAdLoadFailed", true);
        setField("dailyRewardedCount", 0);
        setField("networkAvailableOverride", true);
        assertTrue(manager.isOnlineButRewardedAdFailed());
    }

    @Test
    public void isOnlineButRewardedAdFailed_whenAdLoaded_returnsFalse() throws Exception {
        setField("rewardedAd", mock(com.google.android.gms.ads.rewarded.RewardedAd.class));
        setField("rewardedAdLoadFailed", true);
        setField("dailyRewardedCount", 0);
        setField("networkAvailableOverride", true);
        assertFalse(manager.isOnlineButRewardedAdFailed());
    }

    @Test
    public void isOnlineButRewardedAdFailed_whenOffline_returnsFalse() throws Exception {
        setField("rewardedAd", null);
        setField("rewardedAdLoadFailed", true);
        setField("dailyRewardedCount", 0);
        setField("networkAvailableOverride", false);
        assertFalse(manager.isOnlineButRewardedAdFailed());
    }

    @Test
    public void isOnlineButRewardedAdFailed_atDailyCap_returnsFalse() throws Exception {
        setField("rewardedAd", null);
        setField("rewardedAdLoadFailed", true);
        setField("dailyRewardedCount", AdsConfig.MAX_REWARDED_PER_DAY);
        setField("networkAvailableOverride", true);
        assertFalse(manager.isOnlineButRewardedAdFailed());
    }

    @Test
    public void isOnlineButRewardedAdFailed_whenNoLoadAttempt_returnsFalse() throws Exception {
        setField("rewardedAd", null);
        setField("rewardedAdLoadFailed", false);
        setField("dailyRewardedCount", 0);
        setField("networkAvailableOverride", true);
        assertFalse(manager.isOnlineButRewardedAdFailed());
    }

    // ── Reflection helpers ──────────────────────────────────────

    private void setField(String name, Object value) throws Exception {
        Field field = AdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private Object getField(String name) throws Exception {
        Field field = AdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setTrackerField(String name, Object value) throws Exception {
        Field field = EngagementTracker.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(EngagementTracker.getInstance(), value);
    }

}
