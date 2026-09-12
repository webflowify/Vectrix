package com.tansoft.ps1emulator.ads;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * Global gatekeeper that ensures only one full-screen ad (app-open or interstitial)
 * is active at a time, and enforces a minimum gap between any two full-screen ads.
 *
 * <p>Both {@link AppOpenAdManager} and {@link AdManager} consult this coordinator
 * before showing and dismiss full-screen content to prevent:
 * <ul>
 *   <li>App-open ad overlapping an interstitial (or vice versa)</li>
 *   <li>Back-to-back full-screen ads of any type with no user-visible gap</li>
 *   <li>Stuck ad state when an activity is destroyed mid-display</li>
 * </ul>
 */
public final class AdCoordinator {

    private static final String TAG = "AdCoordinator";

    /** Minimum time that must elapse between the end of one full-screen ad
     *  and the start of the next, regardless of ad type. */
    public static final long MIN_GAP_BETWEEN_ADS_MS = 5_000;

    private static volatile AdCoordinator instance;

    /** True while a full-screen ad is currently on screen. */
    private volatile boolean isFullScreenAdShowing = false;

    /** Wall-clock time when the last full-screen ad ended. */
    private volatile long lastAdEndTime = 0;

    /** Activity whose context is currently being used for the active ad,
     *  or null if no ad is showing.  Weak reference to avoid leaking. */
    @Nullable
    private WeakReference<Activity> currentShowingActivity;

    private AdCoordinator() {}

    public static synchronized AdCoordinator getInstance() {
        if (instance == null) {
            instance = new AdCoordinator();
        }
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────

    /**
     * Returns true if a new full-screen ad may be shown.
     *
     * <p>Blocks when:
     * <ul>
     *   <li>Another full-screen ad is currently displayed</li>
     *   <li>A full-screen ad ended less than {@value #MIN_GAP_BETWEEN_ADS_MS} ago</li>
     *   <li>The activity that started the previous ad has since been destroyed
     *       (stale weak-reference guard)</li>
     * </ul>
     */
    public boolean canShow() {
        if (isStaleActivityShowing()) {
            Log.w(TAG, "Stale ad state detected (activity gone) — clearing coordinator");
            forceReset();
        }
        if (isFullScreenAdShowing) {
            Log.w(TAG, "Blocked — another full-screen ad is currently showing");
            return false;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastAdEndTime;
        if (elapsed < MIN_GAP_BETWEEN_ADS_MS) {
            Log.w(TAG, "Blocked — minimum gap not yet elapsed ("
                + elapsed + "ms < " + MIN_GAP_BETWEEN_ADS_MS + "ms)");
            return false;
        }
        return true;
    }

    /**
     * Records that a full-screen ad has started.
     *
     * <p>Must be called <em>after</em> {@link #canShow()} returns true and
     * <em>before</em> the ad is actually presented.
     */
    public void onAdStart(@NonNull Activity activity) {
        isFullScreenAdShowing = true;
        currentShowingActivity = new WeakReference<>(activity);
        String activityName = activity != null ? activity.getClass().getSimpleName() : "null";
        Log.d(TAG, "Full-screen ad started on " + activityName);
    }

    /**
     * Records that the currently-displayed full-screen ad has ended
     * (dismissed or failed to show).
     */
    public void onAdEnd() {
        isFullScreenAdShowing = false;
        lastAdEndTime = System.currentTimeMillis();
        currentShowingActivity = null;
        Log.d(TAG, "Full-screen ad ended — next ad allowed after "
            + MIN_GAP_BETWEEN_ADS_MS + "ms");
    }

    /**
     * Returns the activity that is currently displaying a full-screen ad,
     * or null if none.
     */
    @Nullable
    public Activity getCurrentShowingActivity() {
        WeakReference<Activity> ref = currentShowingActivity;
        return (ref != null) ? ref.get() : null;
    }

    /**
     * Returns true if the coordinator thinks an ad is showing but the
     * tracked activity has been garbage-collected or destroyed — meaning
     * the ad was already dismissed by the system and the state is stale.
     */
    private boolean isStaleActivityShowing() {
        if (!isFullScreenAdShowing) return false;
        WeakReference<Activity> ref = currentShowingActivity;
        if (ref == null) return true;
        Activity activity = ref.get();
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }

    /**
     * Force-resets the showing flag.
     *
     * <p>Called as a safety net when the hosting activity is destroyed
     * while an ad is on screen, preventing the coordinator from staying
     * permanently stuck.
     */
    public void forceReset() {
        if (isFullScreenAdShowing) {
            Log.w(TAG, "Force-resetting full-screen ad state (activity destroyed)");
        }
        isFullScreenAdShowing = false;
        currentShowingActivity = null;
    }

    /**
     * Returns true if a full-screen ad is currently being displayed.
     */
    public boolean isShowing() {
        return isFullScreenAdShowing;
    }
}
