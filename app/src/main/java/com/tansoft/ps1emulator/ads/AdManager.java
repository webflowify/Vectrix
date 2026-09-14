package com.tansoft.ps1emulator.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import com.tansoft.ps1emulator.util.NetworkHelper;
import com.tansoft.ps1emulator.ads.AdCoordinator;
import com.tansoft.ps1emulator.ads.EngagementTracker;
import com.tansoft.ps1emulator.ads.ClickCounter;

public class AdManager {

    private static final String TAG = "AdManager";
    private static final String PREFS_NAME = "ad_manager_daily";
    private static final String KEY_REWARDED_COUNT = "rewarded_count";
    private static final String KEY_REWARDED_DATE = "rewarded_date";

    private static AdManager instance;
    private Context appContext;
    private SharedPreferences prefs;

    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean rewardedAdLoadFailed = false;
    private long lastInterstitialTime = 0;
    private long lastRewardedTime = 0;
    private int dailyInterstitialCount = 0;
    private int dailyRewardedCount = 0;
    private int totalOpenCount = 0;

    @VisibleForTesting
    Boolean networkAvailableOverride = null;

    private AdManager() {}

    public static synchronized AdManager getInstance() {
        if (instance == null) {
            instance = new AdManager();
        }
        return instance;
    }

    public void init(@NonNull Context context) {
        init(context, null);
    }

    public void init(@NonNull Context context, @Nullable Runnable onInitialized) {
        if (!AdsConfig.ENABLE_ADS) return;
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadDailyRewardedCount();

        if (AdsConfig.getTestDeviceId() != null && !AdsConfig.getTestDeviceId().isEmpty()) {
            RequestConfiguration config = new RequestConfiguration.Builder()
                    .setTestDeviceIds(java.util.Collections.singletonList(AdsConfig.getTestDeviceId()))
                    .build();
            MobileAds.setRequestConfiguration(config);
            Log.d(TAG, "Test device configured: " + AdsConfig.getTestDeviceId());
        }

        MobileAds.initialize(appContext, status -> {
            Log.d(TAG, "AdMob initialized");
            if (!NetworkHelper.isAvailable(appContext)) {
                Log.w(TAG, "Device is offline — ads will not load, rewarded features locked");
            }
            preloadInterstitial();
            preloadRewarded();
            if (onInitialized != null) onInitialized.run();
        });
    }

    // ── Interstitial ──────────────────────────────────────────

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    private void preloadInterstitial() {
        if (!AdsConfig.ENABLE_ADS || appContext == null) {
            Log.w(TAG, "preloadInterstitial: skipped — enabled=" + AdsConfig.ENABLE_ADS + " context=" + (appContext != null));
            return;
        }
        Log.d(TAG, "preloadInterstitial: loading ad unit " + AdsConfig.getAdUnitInterstitial());
        AdRequest request = buildAdRequest();
        InterstitialAd.load(appContext, AdsConfig.getAdUnitInterstitial(), request,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    interstitialAd = ad;
                    Log.d(TAG, "preloadInterstitial: ad loaded successfully");
                }
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    interstitialAd = null;
                    Log.w(TAG, "preloadInterstitial: FAILED — code=" + error.getCode() + " message=" + error.getMessage());
                }
            });
    }

    public boolean canShowInterstitial() {
        if (!AdsConfig.ENABLE_ADS) return false;
        Boolean override = networkAvailableOverride;
        if (override != null ? !override : !NetworkHelper.isAvailable(appContext)) return false;
        if (interstitialAd == null) return false;
        if (!AdCoordinator.getInstance().canShow()) return false;
        return true;
    }

    public void showInterstitialIfReady(@NonNull Activity activity) {
        if (!canShowInterstitial()) {
            ClickCounter counter = ClickCounter.getInstance(appContext);
            Log.d(TAG, "showInterstitialIfReady: blocked — "
                + "enabled=" + AdsConfig.ENABLE_ADS
                + " network=" + NetworkHelper.isAvailable(appContext)
                + " adLoaded=" + (interstitialAd != null)
                + " clicks=" + (counter != null ? counter.getSessionClicks() : "N/A"));
            return;
        }
        Log.d(TAG, "showInterstitialIfReady: showing ad");
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdCoordinator.getInstance().onAdEnd();
                lastInterstitialTime = System.currentTimeMillis();
                interstitialAd = null;
                ClickCounter counter = ClickCounter.getInstance(appContext);
                if (counter != null) counter.onAdShown();
                preloadInterstitial();
            }
            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                AdCoordinator.getInstance().onAdEnd();
                interstitialAd = null;
                ClickCounter counter = ClickCounter.getInstance(appContext);
                if (counter != null) counter.onAdShown();
                preloadInterstitial();
            }
        });
        AdCoordinator.getInstance().onAdStart(activity);
        interstitialAd.show(activity);
    }

    /**
     * Shows the interstitial if ready, then runs {@code after} on the main thread
     * once the ad is dismissed or fails to show. If no ad is available,
     * {@code after} is invoked immediately.
     *
     * Use this when the caller must wait for the ad to close before proceeding
     * (e.g. launching a new activity — the current activity must stay in the
     * foreground for the full-screen ad to render).
     */
    public void showInterstitialThen(@NonNull Activity activity, @NonNull Runnable after) {
        if (!canShowInterstitial()) {
            after.run();
            return;
        }
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdCoordinator.getInstance().onAdEnd();
                lastInterstitialTime = System.currentTimeMillis();
                interstitialAd = null;
                ClickCounter counter = ClickCounter.getInstance(appContext);
                if (counter != null) counter.onAdShown();
                preloadInterstitial();
                after.run();
            }
            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                AdCoordinator.getInstance().onAdEnd();
                interstitialAd = null;
                ClickCounter.getInstance(appContext).onAdShown();
                preloadInterstitial();
                after.run();
            }
        });
        AdCoordinator.getInstance().onAdStart(activity);
        interstitialAd.show(activity);
    }

    public long getLastInterstitialTime() { return lastInterstitialTime; }

    // ── Rewarded ──────────────────────────────────────────────

    private void loadDailyRewardedCount() {
        if (prefs == null) return;
        long storedDate = prefs.getLong(KEY_REWARDED_DATE, 0);
        long today = startOfDay(System.currentTimeMillis());
        if (storedDate == today) {
            dailyRewardedCount = prefs.getInt(KEY_REWARDED_COUNT, 0);
        } else {
            dailyRewardedCount = 0;
            prefs.edit()
                .putInt(KEY_REWARDED_COUNT, 0)
                .putLong(KEY_REWARDED_DATE, today)
                .apply();
        }
    }

    private void persistDailyRewardedCount() {
        if (prefs == null) return;
        prefs.edit()
            .putInt(KEY_REWARDED_COUNT, dailyRewardedCount)
            .putLong(KEY_REWARDED_DATE, startOfDay(System.currentTimeMillis()))
            .apply();
    }

    private static long startOfDay(long millis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void preloadRewarded() {
        if (!AdsConfig.ENABLE_ADS || appContext == null) return;
        rewardedAdLoadFailed = false;
        AdRequest request = buildAdRequest();
        RewardedAd.load(appContext, AdsConfig.getAdUnitRewarded(), request,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    rewardedAd = ad;
                    rewardedAdLoadFailed = false;
                }
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    rewardedAd = null;
                    rewardedAdLoadFailed = true;
                    Log.w(TAG, "Rewarded failed to load: " + error.getMessage());
                }
            });
    }

    public boolean canShowRewarded() {
        if (!AdsConfig.ENABLE_ADS) return false;
        Boolean override = networkAvailableOverride;
        if (override != null ? !override : !NetworkHelper.isAvailable(appContext)) return false;
        if (rewardedAd == null) return false;
        if (dailyRewardedCount >= AdsConfig.MAX_REWARDED_PER_DAY) return false;
        return true;
    }

    public void showRewarded(@NonNull Activity activity,
                             @Nullable Runnable onRewarded,
                             @Nullable Runnable onDismissed) {
        if (!canShowRewarded()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }
        rewardedAdLoadFailed = false;
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdCoordinator.getInstance().onAdEnd();
                dailyRewardedCount++;
                persistDailyRewardedCount();
                lastRewardedTime = System.currentTimeMillis();
                rewardedAd = null;
                preloadRewarded();
                if (onDismissed != null) onDismissed.run();
            }
            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                AdCoordinator.getInstance().onAdEnd();
                rewardedAd = null;
                preloadRewarded();
                if (onDismissed != null) onDismissed.run();
            }
        });
        AdCoordinator.getInstance().onAdStart(activity);
        rewardedAd.show(activity, rewardItem -> {
            if (onRewarded != null) onRewarded.run();
        });
    }

    public RewardedAd getRewardedAd() { return rewardedAd; }

    // ── Online Ad-Load Failure Fallback ────────────────────────

    /**
     * Returns true when the device is online but the rewarded ad failed to load.
     * Used by the fallback path: if the user is connected but the ad SDK didn't
     * deliver an ad, we grant the timed unlock for free rather than blocking.
     */
    public boolean isOnlineButRewardedAdFailed() {
        if (!AdsConfig.ENABLE_ADS) return false;
        if (rewardedAd != null) return false;
        if (!isNetworkAvailable()) return false;
        if (dailyRewardedCount >= AdsConfig.MAX_REWARDED_PER_DAY) return false;
        return rewardedAdLoadFailed;
    }

    private boolean isNetworkAvailable() {
        Boolean override = networkAvailableOverride;
        if (override != null) return override;
        return NetworkHelper.isAvailable(appContext);
    }

    // ── Banner ────────────────────────────────────────────────

    public void loadBanner(@NonNull AdView adView) {
        if (!AdsConfig.ENABLE_ADS) {
            adView.setVisibility(android.view.View.GONE);
            return;
        }
        AdRequest request = buildAdRequest();
        adView.loadAd(request);
    }

    // ── Lifecycle ─────────────────────────────────────────────

    public void incrementOpenCount() { totalOpenCount++; }
    public int getOpenCount() { return totalOpenCount; }

    public void resetDailyCounts() {
        dailyInterstitialCount = 0;
        dailyRewardedCount = 0;
        if (prefs != null) {
            prefs.edit()
                .putInt(KEY_REWARDED_COUNT, 0)
                .putLong(KEY_REWARDED_DATE, 0)
                .apply();
        }
    }

    public boolean isAdsEnabled() { return AdsConfig.ENABLE_ADS; }
}
