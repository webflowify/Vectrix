package com.tansoft.ps1emulator.ads;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.tansoft.ps1emulator.ads.AdCoordinator;
import com.tansoft.ps1emulator.ui.emulation.EmulationActivity;
import com.tansoft.ps1emulator.util.NetworkHelper;

import java.lang.ref.WeakReference;

/**
 * Manages App Open ads with retry logic, lifecycle awareness, and robust error handling.
 *
 * <p>Supports both cold-start splash display (via {@link #showWhenReady}) and
 * warm-start foreground display (via {@link Application.ActivityLifecycleCallbacks}),
 * while strictly protecting active gameplay in {@link EmulationActivity}.
 */
public class AppOpenAdManager implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppOpenAdManager";
    private static final long SHOW_TIMEOUT_MS = 4_000;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_BASE_DELAY_MS = 2_000;
    private static final long FOUR_HOURS_MS = 4 * 3600_000L;

    private static AppOpenAdManager instance;

    private AppOpenAd appOpenAd;
    private long loadTime = 0;
    private long lastShowTime = 0;
    private int dailyCount = 0;
    private int totalOpenCount = 0;
    private boolean isLoading = false;
    private boolean isShowingAd = false;
    private int retryCount = 0;
    private Context appContext;
    private boolean sdkInitialized = false;
    private boolean pendingLoadAfterInit = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WeakReference<Activity> currentActivity;
    private int numStartedActivities = 0;

    // Tracks whether a foreground transition (0→1) was detected but ad display
    // is deferred until onActivityResumed, because AppOpenAd.show() requires
    // a fully-resumed Activity context.
    private boolean foregroundDetectedPendingResume = false;

    private WeakReference<Activity> pendingActivity;
    private Runnable pendingOnDone;
    private boolean pendingShowActive = false;
    private boolean pendingHandled = false;
    private final Runnable timeoutRunnable = () -> completePendingShow(false);

    private AppOpenAdManager() {}

    public static synchronized AppOpenAdManager getInstance() {
        if (instance == null) {
            instance = new AppOpenAdManager();
        }
        return instance;
    }

    /**
     * Initialize with application context and register lifecycle callbacks.
     */
    public void init(@NonNull Context context) {
        appContext = context.getApplicationContext();
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(this);
            Log.d(TAG, "ActivityLifecycleCallbacks registered for warm start");
        }
    }

    /**
     * Mark SDK as initialized. Must be called after {@code MobileAds.initialize()} completes.
     */
    public void markSdkInitialized() {
        mainHandler.post(() -> {
            sdkInitialized = true;
            Log.d(TAG, "SDK marked as initialized");
            if (pendingLoadAfterInit) {
                pendingLoadAfterInit = false;
                loadAd(appContext);
            }
        });
    }

    // ── Availability & Throttling ──────────────────────────────

    /**
     * Checks if a cached ad exists and is less than 4 hours old per AdMob guidelines.
     */
    public boolean isAdAvailable() {
        return appOpenAd != null && (System.currentTimeMillis() - loadTime < FOUR_HOURS_MS);
    }

    /**
     * Determines whether an ad can be shown based on availability, daily cap, and cooldown.
     */
    public boolean canShow() {
        if (!AdsConfig.ENABLE_ADS) return false;
        if (!isAdAvailable()) return false;
        if (dailyCount >= AdsConfig.MAX_APP_OPEN_PER_DAY) return false;

        long now = System.currentTimeMillis();
        if (now - lastShowTime < AdsConfig.APP_OPEN_COOLDOWN_MS) return false;

        long lastInterstitial = AdManager.getInstance().getLastInterstitialTime();
        if (lastInterstitial > 0 && now - lastInterstitial < AdsConfig.APP_OPEN_AFTER_INTERSTITIAL_GAP_MS) {
            return false;
        }

        if (!AdCoordinator.getInstance().canShow()) {
            return false;
        }

        return true;
    }

    // ── Loading ────────────────────────────────────────────────

    public void loadAd(@NonNull Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> loadAd(context));
            return;
        }

        if (!AdsConfig.ENABLE_ADS) return;
        if (isLoading || isAdAvailable()) return;

        Context loadContext = (appContext != null) ? appContext : context.getApplicationContext();
        if (loadContext == null || !NetworkHelper.isAvailable(loadContext)) {
            Log.w(TAG, "loadAd: offline, skipping");
            if (pendingShowActive) {
                completePendingShow(false);
            }
            return;
        }

        if (!sdkInitialized) {
            Log.d(TAG, "loadAd: SDK not yet initialized, queued for post-init");
            pendingLoadAfterInit = true;
            // Proceed to request or let AdMob SDK handle queueing
        }

        isLoading = true;
        AdRequest request = buildAdRequest();
        Log.d(TAG, "loadAd: requesting app open ad (attempt " + (retryCount + 1) + "/" + (MAX_RETRY_COUNT + 1) + ")");

        AppOpenAd.load(loadContext, AdsConfig.getAdUnitAppOpen(), request,
            new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull AppOpenAd ad) {
                    mainHandler.post(() -> {
                        appOpenAd = ad;
                        loadTime = System.currentTimeMillis();
                        isLoading = false;
                        retryCount = 0;
                        Log.d(TAG, "App Open ad loaded successfully");
                        if (pendingShowActive) {
                            completePendingShow(true);
                        }
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    mainHandler.post(() -> {
                        appOpenAd = null;
                        isLoading = false;
                        Log.w(TAG, "App Open ad failed to load (code=" + error.getCode()
                            + ", message=" + error.getMessage() + ")");

                        // For cold-start splash, immediately proceed so the user is not blocked
                        if (pendingShowActive) {
                            completePendingShow(false);
                        }

                        // Schedule background retry for later opens
                        if (retryCount < MAX_RETRY_COUNT) {
                            retryCount++;
                            long delay = RETRY_BASE_DELAY_MS * (long) Math.pow(2, retryCount - 1);
                            Log.d(TAG, "Retrying ad load in " + delay + "ms (attempt "
                                    + (retryCount + 1) + "/" + (MAX_RETRY_COUNT + 1) + ")");
                            mainHandler.postDelayed(() -> loadAd(loadContext), delay);
                        } else {
                            Log.w(TAG, "App Open ad failed after " + (MAX_RETRY_COUNT + 1) + " attempts");
                            retryCount = 0;
                        }
                    });
                }
            });
    }

    private AdRequest buildAdRequest() {
        return new AdRequest.Builder().build();
    }

    // ── Cold Start (Splash) Show ───────────────────────────────

    /**
     * Show the app-open ad when ready, with a timeout.
     *
     * <p>If the ad is loaded it shows immediately. If still loading it waits
     * up to {@value #SHOW_TIMEOUT_MS} ms. If the ad fails, device is offline,
     * or the timeout expires, {@code onDone} is called immediately to proceed.
     * {@code onDone} is guaranteed to be called exactly once.
     */
    public void showWhenReady(@NonNull Activity activity,
                              @NonNull Runnable onDone) {
        if (!AdsConfig.ENABLE_ADS || activity.isFinishing() || activity.isDestroyed()) {
            onDone.run();
            return;
        }

        totalOpenCount++;

        // Fast path: Offline and no cached ad -> don't wait on splash
        if (!NetworkHelper.isAvailable(activity) && !isAdAvailable()) {
            Log.d(TAG, "showWhenReady: offline and no cached ad, proceeding");
            onDone.run();
            return;
        }

        // If ad is ready, show immediately
        if (isAdAvailable()) {
            pendingShowActive = false;
            showAdWithCallback(activity, onDone);
            return;
        }

        // Ad is still loading, wait with timeout
        if (pendingShowActive) {
            mainHandler.removeCallbacks(timeoutRunnable);
            Runnable prevOnDone = pendingOnDone;
            pendingShowActive = false;
            pendingHandled = true;
            if (prevOnDone != null) prevOnDone.run();
        }

        pendingActivity = new WeakReference<>(activity);
        pendingOnDone = onDone;
        pendingShowActive = true;
        pendingHandled = false;

        loadAd(activity);

        mainHandler.removeCallbacks(timeoutRunnable);
        mainHandler.postDelayed(timeoutRunnable, SHOW_TIMEOUT_MS);
    }

    private void completePendingShow(boolean adLoaded) {
        if (pendingHandled) return;
        pendingHandled = true;
        pendingShowActive = false;
        mainHandler.removeCallbacks(timeoutRunnable);

        Activity activity = (pendingActivity != null) ? pendingActivity.get() : null;
        Runnable onDone = pendingOnDone;
        pendingOnDone = null;
        pendingActivity = null;

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onDone != null) onDone.run();
            return;
        }

        if (adLoaded && isAdAvailable()) {
            showAdWithCallback(activity, onDone);
        } else if (onDone != null) {
            onDone.run();
        }
    }

    // ── Internal Display & Callbacks ───────────────────────────

    private void showAdWithCallback(@NonNull Activity activity, @Nullable Runnable onDismissed) {
        if (!AdCoordinator.getInstance().canShow()) {
            Log.w(TAG, "showAdWithCallback: blocked by AdCoordinator");
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (isShowingAd) {
            Log.w(TAG, "showAdWithCallback: ad already showing");
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (!isAdAvailable()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            if (onDismissed != null) onDismissed.run();
            return;
        }

        isShowingAd = true;
        AdCoordinator.getInstance().onAdStart(activity);
        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                AdCoordinator.getInstance().onAdEnd();
                isShowingAd = false;
                appOpenAd = null;
                lastShowTime = System.currentTimeMillis();
                dailyCount++;
                retryCount = 0;
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    loadAd(activity);
                }
                if (onDismissed != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    onDismissed.run();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                AdCoordinator.getInstance().onAdEnd();
                Log.w(TAG, "App Open ad failed to show: " + error.getMessage());
                isShowingAd = false;
                appOpenAd = null;
                retryCount = 0;
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    loadAd(activity);
                }
                if (onDismissed != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    onDismissed.run();
                }
            }
        });

        appOpenAd.show(activity);
    }

    /**
     * Show app open ad if conditions are met.
     *
     * @param activity The activity to show the ad on
     * @param isEmulationActivity If true, NEVER show (gameplay protection)
     */
    public void showIfReady(@NonNull Activity activity, boolean isEmulationActivity) {
        if (isEmulationActivity) return;
        if (!canShow()) {
            totalOpenCount++;
            return;
        }

        totalOpenCount++;
        showAdWithCallback(activity, null);
    }

    // ── Warm Start (Foreground) Lifecycle ──────────────────────

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
        numStartedActivities++;
        if (numStartedActivities == 1) {
            // App came to foreground — defer ad display until the activity is
            // fully resumed because AppOpenAd.show() requires a resumed Activity.
            Log.d(TAG, "App brought to foreground, deferring warm-start ad until onActivityResumed");
            foregroundDetectedPendingResume = true;
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
        if (foregroundDetectedPendingResume) {
            foregroundDetectedPendingResume = false;
            onAppForegrounded(activity);
        }
    }

    private void onAppForegrounded(@NonNull Activity activity) {
        if (activity instanceof EmulationActivity) {
            Log.d(TAG, "Skipping warm-start app open ad for EmulationActivity (gameplay protection)");
            return;
        }

        if (activity.getClass().getSimpleName().equals("MainActivity")) {
            return;
        }

        Context ctx = (appContext != null) ? appContext : activity.getApplicationContext();
        if (ctx != null && AdsConfig.ENABLE_ADS && !isAdAvailable() && !isLoading
                && NetworkHelper.isAvailable(ctx)) {
            Log.d(TAG, "Foregrounded with no valid ad cache — pre-loading for next opportunity");
            loadAd(ctx);
        }

        if (canShow() && !isShowingAd) {
            Log.d(TAG, "Showing warm-start app open ad on " + activity.getClass().getSimpleName());
            showAdWithCallback(activity, null);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        numStartedActivities--;
        if (numStartedActivities <= 0) {
            numStartedActivities = 0;
            Log.d(TAG, "App sent to background");
            foregroundDetectedPendingResume = false;

            if (!isShowingAd) {
                Context ctx = (appContext != null) ? appContext : activity.getApplicationContext();
                if (ctx != null && AdsConfig.ENABLE_ADS && !isAdAvailable() && !isLoading
                        && NetworkHelper.isAvailable(ctx)) {
                    Log.d(TAG, "Backgrounding with no valid ad cache — pre-loading for next foreground");
                    loadAd(ctx);
                }
            }
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override
    public void onActivityPaused(@NonNull Activity activity) {}
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity != null && currentActivity.get() == activity) {
            currentActivity = null;
        }
        if (isShowingAd || AdCoordinator.getInstance().getCurrentShowingActivity() == activity) {
            Log.w(TAG, "Activity destroyed while a full-screen ad is showing — resetting coordinator");
            isShowingAd = false;
            AdCoordinator.getInstance().forceReset();
        }
    }

    // ── Accessors ──────────────────────────────────────────────

    public void incrementOpenCount() { totalOpenCount++; }
    public int getOpenCount() { return totalOpenCount; }
    public void resetDailyCount() { dailyCount = 0; }
    public int getDailyCount() { return dailyCount; }
    public long getLastShowTime() { return lastShowTime; }
    public boolean isShowingAd() { return isShowingAd; }
}
