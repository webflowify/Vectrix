package com.tansoft.ps1emulator.ads;

import android.content.Context;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdsConfigTest {

    private String str(int resId) {
        Context ctx = RuntimeEnvironment.getApplication();
        return ctx.getResources().getString(resId);
    }

    @Test
    public void enableAds_isTrueByDefault() {
        assertTrue(AdsConfig.ENABLE_ADS);
    }

    @Test
    public void adUnitIds_matchStringResources() {
        assertEquals(str(com.tansoft.ps1emulator.R.string.admob_app_id),         AdsConfig.getAdmobAppId());
        assertEquals(str(com.tansoft.ps1emulator.R.string.ad_unit_banner),       AdsConfig.getAdUnitBanner());
        assertEquals(str(com.tansoft.ps1emulator.R.string.ad_unit_interstitial), AdsConfig.getAdUnitInterstitial());
        assertEquals(str(com.tansoft.ps1emulator.R.string.ad_unit_rewarded),     AdsConfig.getAdUnitRewarded());
        assertEquals(str(com.tansoft.ps1emulator.R.string.ad_unit_native),       AdsConfig.getAdUnitNative());
        assertEquals(str(com.tansoft.ps1emulator.R.string.ad_unit_app_open),     AdsConfig.getAdUnitAppOpen());
    }

    @Test
    public void adUnitIds_areNotEmpty() {
        assertNotNull(AdsConfig.getAdmobAppId());
        assertFalse(AdsConfig.getAdmobAppId().isEmpty());
        assertNotNull(AdsConfig.getAdUnitBanner());
        assertFalse(AdsConfig.getAdUnitBanner().isEmpty());
        assertNotNull(AdsConfig.getAdUnitInterstitial());
        assertFalse(AdsConfig.getAdUnitInterstitial().isEmpty());
        assertNotNull(AdsConfig.getAdUnitRewarded());
        assertFalse(AdsConfig.getAdUnitRewarded().isEmpty());
        assertNotNull(AdsConfig.getAdUnitNative());
        assertFalse(AdsConfig.getAdUnitNative().isEmpty());
        assertNotNull(AdsConfig.getAdUnitAppOpen());
        assertFalse(AdsConfig.getAdUnitAppOpen().isEmpty());
    }

    @Test
    public void frequencyCaps_areReasonable() {
        assertTrue(AdsConfig.MAX_REWARDED_PER_DAY > 0);
        assertTrue(AdsConfig.MAX_REWARDED_PER_SESSION > 0);
        assertTrue(AdsConfig.MAX_APP_OPEN_PER_DAY > 0);
        assertTrue(AdsConfig.APP_OPEN_COOLDOWN_MS >= 60_000);
    }

    @Test
    public void featureGates_areReasonable() {
        assertTrue(AdsConfig.FREE_SAVE_SLOTS >= 1);
        assertTrue(AdsConfig.FREE_SAVE_SLOTS < 10);
    }

    @Test
    public void adUnitIds_sharePublisherId() {
        String appId = AdsConfig.getAdmobAppId();
        String prefix = "ca-app-pub-";
        int start = appId.indexOf(prefix);
        int end = appId.indexOf("~");
        assertTrue("App ID should start with " + prefix, start != -1);
        assertTrue("App ID should contain ~ delimiter", end > start);
        String publisherId = appId.substring(start + prefix.length(), end);
        assertFalse("Publisher ID should not be empty", publisherId.isEmpty());

        assertTrue(AdsConfig.getAdmobAppId().contains(publisherId));
        assertTrue(AdsConfig.getAdUnitBanner().contains(publisherId));
        assertTrue(AdsConfig.getAdUnitInterstitial().contains(publisherId));
        assertTrue(AdsConfig.getAdUnitRewarded().contains(publisherId));
        assertTrue(AdsConfig.getAdUnitNative().contains(publisherId));
        assertTrue(AdsConfig.getAdUnitAppOpen().contains(publisherId));
    }

    @Test
    public void timedUnlockDuration_is24Hours() {
        assertEquals(86_400_000L, AdsConfig.TIMED_UNLOCK_DURATION_MS);
    }

    @Test
    public void nativeAdMinGames_isReasonable() {
        assertTrue(AdsConfig.NATIVE_AD_MIN_GAMES >= 1);
        assertTrue(AdsConfig.NATIVE_AD_MIN_GAMES <= 20);
    }
}
