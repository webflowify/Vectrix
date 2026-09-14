package com.tansoft.ps1emulator.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.tansoft.ps1emulator.storage.SaveStateManager;

import java.io.File;

public class RewardedUnlockManager {

    static final String PREFS_NAME = "rewarded_unlocks";
    static final String GRANDFATHER_MIGRATION_KEY = "grandfather_migration_done_v1";
    private static RewardedUnlockManager instance;
    private SharedPreferences prefs;
    private Context context;

    private int sessionRewardedCount = 0;

    private RewardedUnlockManager() {}

    public static synchronized RewardedUnlockManager getInstance() {
        if (instance == null) {
            instance = new RewardedUnlockManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * One-time migration: for users who had save data in premium slots (3–9)
     * before monetization was introduced, auto-unlock those slots permanently.
     *
     * Scans the app's savestates/ directory for all gameDiscIds, checks each
     * premium slot for an existing .psst file, and writes the unlock flag if
     * the slot is occupied but not yet marked as unlocked.
     *
     * Safe to call repeatedly — short-circuits after the first successful run
     * via a migration-done flag in SharedPreferences.
     *
     * Runs on the calling thread. Call from a background thread or after init().
     */
    public void grandfatherExistingSaveSlots(Context context) {
        if (prefs == null || prefs.getBoolean(GRANDFATHER_MIGRATION_KEY, false)) {
            return;
        }

        File savestatesRoot = new File(context.getFilesDir(), "savestates");
        if (!savestatesRoot.exists() || !savestatesRoot.isDirectory()) {
            prefs.edit().putBoolean(GRANDFATHER_MIGRATION_KEY, true).apply();
            return;
        }

        File[] gameDirs = savestatesRoot.listFiles(File::isDirectory);
        if (gameDirs == null) {
            prefs.edit().putBoolean(GRANDFATHER_MIGRATION_KEY, true).apply();
            return;
        }

        boolean anyUnlocked = false;
        for (File gameDir : gameDirs) {
            String gameDiscId = gameDir.getName();
            if (gameDiscId.isEmpty()) continue;

            for (int slot = AdsConfig.FREE_SAVE_SLOTS; slot < SaveStateManager.MAX_SLOTS; slot++) {
                if (!prefs.getBoolean("slot_" + gameDiscId + "_" + slot, false)) {
                    File saveFile = SaveStateManager.getSaveFile(context, gameDiscId, slot);
                    if (saveFile.exists()) {
                        prefs.edit()
                            .putBoolean("slot_" + gameDiscId + "_" + slot, true)
                            .apply();
                        anyUnlocked = true;
                    }
                }
            }
        }

        prefs.edit().putBoolean(GRANDFATHER_MIGRATION_KEY, true).apply();
        android.util.Log.d("RewardedUnlockManager",
            "grandfatherExistingSaveSlots: migration complete, anyUnlocked=" + anyUnlocked);
    }

    // ── Offline Blocking ────────────────────────────────────────

    /**
     * Check if user can attempt unlock (must be online to watch ad).
     * Returns false if offline — shows toast and blocks unlock.
     */
    public boolean canAttemptUnlock() {
        if (!NetworkHelper.isOnline()) {
            return false;
        }
        return true;
    }

    /**
     * Attempt to unlock a feature. Blocks if offline.
     * Returns true if unlock can proceed, false if blocked.
     */
    public boolean attemptUnlock(Activity activity, String feature) {
        if (!canAttemptUnlock()) {
            Toast.makeText(activity,
                "Internet required to unlock",
                Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Attempt to unlock a timed feature with ad-load failure fallback.
     *
     * If the device is online but the rewarded ad failed to load, this method
     * grants the 24-hour timed unlock immediately and shows an informational
     * toast, rather than blocking the user. This prevents ad SDK delivery
     * issues from locking users out of features they attempted to unlock.
     *
     * Returns true if the unlock succeeded (either via fallback or ready for
     * ad dialog), false if blocked (offline or daily cap reached).
     */
    public boolean attemptUnlockWithFallback(Activity activity, String feature) {
        if (!AdsConfig.ENABLE_ADS) return true;
        if (isTimedUnlockActive(feature)) return true;
        if (!canAttemptUnlock()) {
            Toast.makeText(activity,
                "Internet required to unlock",
                Toast.LENGTH_SHORT).show();
            return false;
        }
        if (AdManager.getInstance().isOnlineButRewardedAdFailed()) {
            if (!canShowSessionRewarded()) {
                Toast.makeText(activity,
                    "Session limit reached. Finish your current game to unlock more.",
                    Toast.LENGTH_SHORT).show();
                return false;
            }
            incrementSessionRewarded();
            unlockTimed(feature);
            Toast.makeText(activity,
                "Ad unavailable — feature unlocked for 24 hours",
                Toast.LENGTH_LONG).show();
            return true;
        }
        return true;
    }

    // ── Free Tier Checks ──────────────────────────────────────

    /**
     * Check if a feature is free (no ad required).
     * If ENABLE_ADS is false, everything is free.
     * Save slots 0-2 are always free. Slots 3-9 require a rewarded ad (permanent unlock).
     */
    public boolean isFeatureFree(String feature, int currentValue) {
        if (!AdsConfig.ENABLE_ADS) return true;
        if (feature == null) return false;

        switch (feature) {
            case "save_slot":
                return currentValue < AdsConfig.FREE_SAVE_SLOTS; // 3 free
            default:
                return false;  // All other features require ad
        }
    }

    // ── Session-Scoped Rewards Cap ─────────────────────────────

    public boolean canShowSessionRewarded() {
        if (!AdsConfig.ENABLE_ADS) return false;
        return sessionRewardedCount < AdsConfig.MAX_REWARDED_PER_SESSION;
    }

    public void incrementSessionRewarded() {
        sessionRewardedCount++;
    }

    public void clearSession() {
        sessionRewardedCount = 0;
    }

    // ── Timed Unlocks (24-hour expiry) ─────────────────────────

    /**
     * Check if a feature is currently unlocked (within 24-hour window).
     * Used for 16x FF and extended rewind — watch ad, unlock for 24h.
     */
    public boolean isTimedUnlockActive(String feature) {
        if (!AdsConfig.ENABLE_ADS) return true;
        long unlockTime = prefs.getLong("timed_" + feature, 0);
        if (unlockTime == 0) return false;
        long elapsed = System.currentTimeMillis() - unlockTime;
        return elapsed < AdsConfig.TIMED_UNLOCK_DURATION_MS;  // 24 hours
    }

    /**
     * Unlock a feature for 24 hours.
     */
    public void unlockTimed(String feature) {
        prefs.edit()
            .putLong("timed_" + feature, System.currentTimeMillis())
            .apply();
    }

    /**
     * Get remaining unlock time in milliseconds.
     * Returns 0 if not unlocked or expired.
     */
    public long getTimedUnlockRemaining(String feature) {
        if (!AdsConfig.ENABLE_ADS) return Long.MAX_VALUE;
        long unlockTime = prefs.getLong("timed_" + feature, 0);
        if (unlockTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - unlockTime;
        long remaining = AdsConfig.TIMED_UNLOCK_DURATION_MS - elapsed;
        return Math.max(0, remaining);
    }

    // ── Permanent Unlocks (persisted) ──────────────────────────

    /**
     * Check if a feature is permanently unlocked (survives app restarts).
     * Used for save slots — watch ad once per slot, keep forever.
     */
    public boolean isPermanentlyUnlocked(String feature) {
        if (!AdsConfig.ENABLE_ADS) return true;
        return prefs.getBoolean("perm_" + feature, false);
    }

    /**
     * Permanently unlock a feature (watched ad once, keep forever).
     */
    public void unlockPermanent(String feature) {
        prefs.edit()
            .putBoolean("perm_" + feature, true)
            .apply();
    }

    /**
     * Check if a specific save slot is permanently unlocked for a game.
     */
    public boolean isSaveSlotUnlocked(String gameDiscId, int slotIndex) {
        if (!AdsConfig.ENABLE_ADS) return true;
        if (slotIndex < AdsConfig.FREE_SAVE_SLOTS) return true;
        return prefs.getBoolean("slot_" + gameDiscId + "_" + slotIndex, false);
    }

    /**
     * Permanently unlock a save slot for a game.
     */
    public void unlockSaveSlot(String gameDiscId, int slotIndex) {
        prefs.edit()
            .putBoolean("slot_" + gameDiscId + "_" + slotIndex, true)
            .apply();
    }

    /**
     * Get count of permanently unlocked slots for a game.
     */
    public int getUnlockedSlotCount(String gameDiscId) {
        int count = AdsConfig.FREE_SAVE_SLOTS;
        for (int i = AdsConfig.FREE_SAVE_SLOTS; i < 10; i++) {
            if (isSaveSlotUnlocked(gameDiscId, i)) {
                count++;
            }
        }
        return count;
    }
}
