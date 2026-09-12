package com.tansoft.ps1emulator.ads;

import android.os.SystemClock;

/**
 * Tracks user engagement for analytics purposes.
 *
 * Interstitial ads are no longer gated by click counts or session timers
 * — they show whenever a loaded ad is available.
 */
public class EngagementTracker {

    private static EngagementTracker instance;

    // ── Session metrics ────────────────────────────────────────
    private int totalClicks = 0;
    private int clicksSinceLastAd = 0;
    private long lastClickTime = 0;
    private long lastAdShowTime = 0;
    private long sessionStartTime = 0;
    private boolean sessionStarted = false;

    // ── Click-type counters (analytics) ────────────────────────
    private int ffClicks = 0;
    private int smClicks = 0;
    private int rewindClicks = 0;
    private int saveLoadClicks = 0;
    private int menuClicks = 0;
    private int gameCardClicks = 0;

    private EngagementTracker() {
        lastClickTime = SystemClock.elapsedRealtime();
    }

    public static synchronized EngagementTracker getInstance() {
        if (instance == null) {
            instance = new EngagementTracker();
        }
        return instance;
    }

    // ── Tracking methods (call from button handlers) ───────────

    public void startSession() {
        sessionStartTime = SystemClock.elapsedRealtime();
        sessionStarted = true;
        totalClicks = 0;
        clicksSinceLastAd = 0;
        ffClicks = 0;
        smClicks = 0;
        rewindClicks = 0;
        saveLoadClicks = 0;
        menuClicks = 0;
        lastClickTime = sessionStartTime;
        lastAdShowTime = 0;
    }

    public void endSession() {
        // Metrics preserved for analytics if needed
    }

    public void recordClick(String type) {
        totalClicks++;
        clicksSinceLastAd++;
        lastClickTime = SystemClock.elapsedRealtime();

        switch (type) {
            case "ff": ffClicks++; break;
            case "sm": smClicks++; break;
            case "rewind": rewindClicks++; break;
            case "save_load": saveLoadClicks++; break;
            case "menu": menuClicks++; break;
        }
    }

    public void recordAdShown() {
        lastAdShowTime = SystemClock.elapsedRealtime();
    }

    // ── Interstitial ad readiness ──────────────────────────────

    /**
     * Always returns true — interstitial ads are no longer gated behind
     * click counts or session timers.
     */
    public boolean hasEnoughClicksForAd() {
        return true;
    }

    /**
     * Number of clicks since the last interstitial ad was shown.
     */
    public int getClicksSinceLastAd() {
        return clicksSinceLastAd;
    }

    /**
     * Time since last ad was shown.
     */
    public long getTimeSinceLastAdMs() {
        if (lastAdShowTime == 0) return Long.MAX_VALUE;
        return SystemClock.elapsedRealtime() - lastAdShowTime;
    }

    // ── Getters ────────────────────────────────────────────────

    public int getTotalClicks() { return totalClicks; }
    public int getFfClicks() { return ffClicks; }
    public int getSmClicks() { return smClicks; }
    public int getRewindClicks() { return rewindClicks; }
    public int getSaveLoadClicks() { return saveLoadClicks; }

    public long getSessionDurationMs() {
        if (sessionStartTime == 0) return 0;
        return SystemClock.elapsedRealtime() - sessionStartTime;
    }

    /**
     * Summary string for debugging.
     */
    public String getDebugSummary() {
        return String.format(
            "Engagement{clicks=%d, sinceAd=%d, session=%ds}",
            totalClicks,
            clicksSinceLastAd,
            getSessionDurationMs() / 1000
        );
    }
}
