package com.tansoft.ps1emulator.ads;

import android.content.res.Resources;
import android.util.Log;

import com.tansoft.ps1emulator.PS1EmulatorApp;

public final class AdsConfig {

    private AdsConfig() {}

    private static final String TAG = "AdsConfig";

    // ── Master Switch ──────────────────────────────────────────
    public static final boolean ENABLE_ADS = true;

    // ── AdMob IDs (single source of truth: res/values/strings.xml) ──
    // Resolved lazily from string resources via the Application context.
    // PS1EmulatorApp.onCreate() sets the instance before any ad code runs.
    private static String admobAppId;
    private static String adUnitBanner;
    private static String adUnitInterstitial;
    private static String adUnitRewarded;
    private static String adUnitNative;
    private static String adUnitAppOpen;

    public static String getAdmobAppId()        { return resolve(admobAppId,        "admob_app_id",        () -> admobAppId        = str("admob_app_id")); }
    public static String getAdUnitBanner()      { return resolve(adUnitBanner,      "ad_unit_banner",      () -> adUnitBanner      = str("ad_unit_banner")); }
    public static String getAdUnitInterstitial(){ return resolve(adUnitInterstitial,"ad_unit_interstitial",() -> adUnitInterstitial= str("ad_unit_interstitial")); }
    public static String getAdUnitRewarded()    { return resolve(adUnitRewarded,    "ad_unit_rewarded",    () -> adUnitRewarded    = str("ad_unit_rewarded")); }
    public static String getAdUnitNative()      { return resolve(adUnitNative,      "ad_unit_native",      () -> adUnitNative      = str("ad_unit_native")); }
    public static String getAdUnitAppOpen()     { return resolve(adUnitAppOpen,     "ad_unit_app_open",    () -> adUnitAppOpen     = str("ad_unit_app_open")); }

    private static String resolve(String cached, String name, java.util.concurrent.Callable<String> loader) {
        if (cached != null) return cached;
        try {
            return loader.call();
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve ad ID: " + name, e);
            return "";
        }
    }

    private static String str(String resName) {
        Resources res = PS1EmulatorApp.getInstance().getResources();
        int id = res.getIdentifier(resName, "string", PS1EmulatorApp.getInstance().getPackageName());
        return id != 0 ? res.getString(id) : "";
    }

    // ── Test Device ────────────────────────────────────────────
    // Resolved from res/values/strings.xml. Harmless in release builds
    // (AdMob serves real ads to all non-test devices regardless).
    private static String testDeviceId;

    public static String getTestDeviceId() {
        return resolve(testDeviceId, "test_device_id", () -> testDeviceId = str("test_device_id"));
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
