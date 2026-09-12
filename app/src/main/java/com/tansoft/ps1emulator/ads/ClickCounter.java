package com.tansoft.ps1emulator.ads;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Counts meaningful user interactions (UI clicks) across the app and gates
 * interstitial ad display behind a per-ad click threshold.
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>Zero GC pressure on the emulation thread:</b> all state is held in
 *       an {@link java.util.concurrent.atomic.AtomicInteger} and
 *       {@link SharedPreferences}; no allocations occur in {@link #recordClick()}.</li>
 *   <li><b>Survives process death:</b> the counter is persisted to
 *       {@link SharedPreferences} on every increment so a cold restart does not
 *       silently reset progress toward the next ad.</li>
 *   <li><b>Resets after each ad:</b> once an interstitial is shown (or fails to
 *       show), the counter returns to zero and the user must accumulate the
 *       threshold again.</li>
 *   <li><b>AdMob policy compliant:</b> only UI-level interactions are counted;
 *       in-game controller / virtual-gamepad inputs are excluded so ads can
 *       never be triggered by gameplay activity. Interstitials continue to
 *       appear only at the existing natural break-points (activity transitions,
 *       menu actions) and are never injected during active emulation.</li>
 * </ul>
 */
public final class ClickCounter {

    private static final String PREFS_NAME = "click_counter_prefs";
    private static final String KEY_COUNT = "click_count";
    public static final int CLICKS_PER_AD = 5;

    private static ClickCounter instance;

    private final SharedPreferences prefs;
    private final java.util.concurrent.atomic.AtomicInteger sessionClicks;

    private ClickCounter(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.sessionClicks = new java.util.concurrent.atomic.AtomicInteger(
                prefs.getInt(KEY_COUNT, 0));
    }

    /**
     * Returns the singleton, creating it on first call.
     *
     * <p>Typical initialisation site: {@code PS1EmulatorApp.onCreate()}.
     * After that, {@code context} may be {@code null} and the previously
     * constructed instance is returned unchanged.
     */
    public static synchronized ClickCounter getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new ClickCounter(context);
        }
        return instance;
    }

    /**
     * Records a single meaningful UI interaction.
     *
     * <p>Call this from every {@link android.view.View.OnClickListener}
     * that represents a deliberate app action (buttons, menu items, etc.).
     * Do <em>not</em> call it for in-game controller input — those are game
     * events, not app interactions, and counting them would both violate
     * AdMob policy and produce a terrible user experience.
     */
    public void recordClick() {
        int updated = sessionClicks.incrementAndGet();
        prefs.edit().putInt(KEY_COUNT, updated).apply();
    }

    /**
     * @return {@code true} if the user has accumulated enough clicks since
     *         the last ad to qualify for the next interstitial.
     */
    public boolean canShowAd() {
        return sessionClicks.get() >= CLICKS_PER_AD;
    }

    /**
     * Resets the click counter to zero.
     *
     * <p>Called after an interstitial is shown (or fails to show) so the
     * user must accumulate {@link #CLICKS_PER_AD} fresh interactions before
     * the next ad can appear.
     */
    public void onAdShown() {
        sessionClicks.set(0);
        prefs.edit().putInt(KEY_COUNT, 0).apply();
    }

    /**
     * @return the number of additional clicks required before the next
     *         interstitial can be shown (0 if an ad is already eligible).
     */
    public int getRemainingClicks() {
        int remaining = CLICKS_PER_AD - sessionClicks.get();
        return remaining > 0 ? remaining : 0;
    }

    /**
     * @return total clicks accumulated since the last ad was shown.
     */
    public int getSessionClicks() {
        return sessionClicks.get();
    }

    /**
     * Clears all state. Primarily intended for testing.
     */
    public void reset() {
        sessionClicks.set(0);
        prefs.edit().putInt(KEY_COUNT, 0).apply();
    }
}
