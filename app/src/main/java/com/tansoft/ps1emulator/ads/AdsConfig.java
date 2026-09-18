package com.tansoft.ps1emulator.ads;

import android.content.res.Resources;
import android.util.Log;

import com.tansoft.ps1emulator.PS1EmulatorApp;
import com.tansoft.ps1emulator.R;

public final class AdsConfig {

    private AdsConfig() {}

    private static final String TAG = "AdsConfig";

    // ── Master Switch ──────────────────────────────────────────
    public static final boolean ENABLE_ADS = true;

    // ── AdMob IDs (single source of truth: res/values/strings.xml) ──
    // Resolved lazily from compile-time string resource IDs so that AGP's
    // resource shrinker (shrinkResources true) never strips or clears them.
    private static String admobAppId;
    private static String adUnitBanner;
    private static String adUnitInterstitial;
    private static String adUnitRewarded;
    private static String adUnitNative;
    private static String adUnitAppOpen;

    public static String getAdmobAppId() {
        if (admobAppId == null) admobAppId = str(R.string.admob_app_id);
        return admobAppId;
    }

    public static String getAdUnitBanner() {
        if (adUnitBanner == null) adUnitBanner = str(R.string.ad_unit_banner);
        return adUnitBanner;
    }

    public static String getAdUnitInterstitial() {
        if (adUnitInterstitial == null) adUnitInterstitial = str(R.string.ad_unit_interstitial);
        return adUnitInterstitial;
    }

    public static String getAdUnitRewarded() {
        if (adUnitRewarded == null) adUnitRewarded = str(R.string.ad_unit_rewarded);
        return adUnitRewarded;
    }

    public static String getAdUnitNative() {
        if (adUnitNative == null) adUnitNative = str(R.string.ad_unit_native);
        return adUnitNative;
    }

    public static String getAdUnitAppOpen() {
        if (adUnitAppOpen == null) adUnitAppOpen = str(R.string.ad_unit_app_open);
        return adUnitAppOpen;
    }

    private static String str(int resId) {
        try {
            PS1EmulatorApp app = PS1EmulatorApp.getInstance();
            if (app != null) {
                return app.getString(resId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve ad ID resource: " + resId, e);
        }
        return "";
    }

    // ── Test Device ────────────────────────────────────────────
    // Resolved from res/values/strings.xml if defined (e.g. in debug variant).
    // Harmless in release builds (AdMob serves real ads to all non-test devices).
    private static String testDeviceId;

    public static String getTestDeviceId() {
        if (testDeviceId != null) return testDeviceId;
        try {
            PS1EmulatorApp app = PS1EmulatorApp.getInstance();
            if (app != null) {
                Resources res = app.getResources();
                int id = res.getIdentifier("test_device_id", "string", app.getPackageName());
                testDeviceId = (id != 0) ? res.getString(id) : "";
            } else {
                testDeviceId = "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve test_device_id", e);
            testDeviceId = "";
        }
        return testDeviceId;
    }

    // ── Frequency & Throttling ─────────────────────────────────
    public static final int MAX_REWARDED_PER_DAY = 10;
    public static final int MAX_REWARDED_PER_SESSION = 5;
    public static final int MAX_APP_OPEN_PER_DAY = 3;
    public static final long APP_OPEN_COOLDOWN_MS = 600_000;
    public static final long APP_OPEN_AFTER_INTERSTITIAL_GAP_MS = 120_000;

    // ── Feature Gates ──────────────────────────────────────────
    public static final int FREE_SAVE_SLOTS = 3;

    // ── Timed Unlock Duration ──────────────────────────────────
    public static final long TIMED_UNLOCK_DURATION_MS = 86_400_000;

    // ── Native Ad Threshold ────────────────────────────────────
    public static final int NATIVE_AD_MIN_GAMES = 5;
}
