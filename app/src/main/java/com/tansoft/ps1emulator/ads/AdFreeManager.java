package com.tansoft.ps1emulator.ads;

import android.content.Context;
import android.content.SharedPreferences;

public class AdFreeManager {

    private static final String TAG = "AdFreeManager";
    private static final String PREFS_NAME = "ad_free_manager";
    private static final String KEY_AD_FREE_UNTIL = "ad_free_until";

    private static AdFreeManager instance;
    private SharedPreferences prefs;
    private Context context;

    private AdFreeManager() {}

    public static synchronized AdFreeManager getInstance() {
        if (instance == null) {
            instance = new AdFreeManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Grant temporary ad-free access for the given duration.
     * If already ad-free with a longer remaining time, keeps the longer duration.
     */
    public void grantAdFree(long durationMs) {
        if (prefs == null) return;
        long currentExpiry = prefs.getLong(KEY_AD_FREE_UNTIL, 0);
        long newExpiry = System.currentTimeMillis() + durationMs;
        if (newExpiry > currentExpiry) {
            prefs.edit().putLong(KEY_AD_FREE_UNTIL, newExpiry).apply();
            android.util.Log.d(TAG, "grantAdFree: granted for " + (durationMs / 1000) + "s, expires at " + newExpiry);
        }
    }

    /**
     * Check if the user currently has ad-free access.
     */
    public boolean isAdFreeActive() {
        if (prefs == null) return false;
        long expiry = prefs.getLong(KEY_AD_FREE_UNTIL, 0);
        if (expiry == 0) return false;
        boolean active = System.currentTimeMillis() < expiry;
        if (!active) {
            prefs.edit().remove(KEY_AD_FREE_UNTIL).apply();
        }
        return active;
    }

    /**
     * Get remaining ad-free time in milliseconds. Returns 0 if not active.
     */
    public long getAdFreeRemainingMs() {
        if (prefs == null) return 0;
        long expiry = prefs.getLong(KEY_AD_FREE_UNTIL, 0);
        if (expiry == 0) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Clear ad-free status (e.g., for testing or manual reset).
     */
    public void clearAdFree() {
        if (prefs == null) return;
        prefs.edit().remove(KEY_AD_FREE_UNTIL).apply();
    }
}
