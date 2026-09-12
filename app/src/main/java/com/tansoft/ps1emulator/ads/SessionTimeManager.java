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

import java.lang.ref.WeakReference;

public class SessionTimeManager implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "SessionTimeManager";
    private static final long SESSION_TICK_MS = 1_000;
    private static final long DEFAULT_SESSION_DURATION_MS = 900_000; // 15 minutes

    private static volatile SessionTimeManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAppInForeground) {
                accumulatedSessionMs += SESSION_TICK_MS;
                mainHandler.postDelayed(this, SESSION_TICK_MS);
            }
        }
    };

    private boolean isAppInForeground = false;
    private long accumulatedSessionMs = 0;
    private long sessionDurationMs = DEFAULT_SESSION_DURATION_MS;
    private WeakReference<Application> applicationRef;

    private SessionTimeManager() {}

    public static synchronized SessionTimeManager getInstance() {
        if (instance == null) {
            instance = new SessionTimeManager();
        }
        return instance;
    }

    /**
     * Register lifecycle callbacks on the Application to track foreground/background transitions.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public void init(@NonNull Context context) {
        if (applicationRef != null) {
            Application existing = applicationRef.get();
            if (existing != null) return; // already initialized
        }
        if (context instanceof Application) {
            Application app = (Application) context;
            applicationRef = new WeakReference<>(app);
            app.registerActivityLifecycleCallbacks(this);
            Log.d(TAG, "Lifecycle callbacks registered");
        }
    }

    /**
     * Set the session duration required before ads become eligible.
     */
    public void setSessionDurationMs(long durationMs) {
        this.sessionDurationMs = durationMs;
    }

    /**
     * Reset accumulated session time. Called after an ad is shown so the
     * user must spend another full session duration before the next ad.
     */
    public void resetSession() {
        accumulatedSessionMs = 0;
        Log.d(TAG, "Session reset — next ad eligible after " + (sessionDurationMs / 1000) + "s of foreground time");
    }

    /**
     * Returns true if the user has accumulated enough foreground session time
     * to be eligible for an ad.
     */
    public boolean hasReachedSessionThreshold() {
        return accumulatedSessionMs >= sessionDurationMs;
    }

    /**
     * Returns accumulated foreground session time in milliseconds.
     */
    public long getAccumulatedSessionMs() {
        return accumulatedSessionMs;
    }

    /**
     * Returns remaining session time before ads become eligible, in milliseconds.
     * Returns 0 if already eligible.
     */
    public long getRemainingSessionMs() {
        long remaining = sessionDurationMs - accumulatedSessionMs;
        return remaining > 0 ? remaining : 0;
    }

    // ── Application.ActivityLifecycleCallbacks ──────────────────

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (!isAppInForeground) {
            isAppInForeground = true;
            Log.d(TAG, "App entered foreground — session timer started");
            mainHandler.removeCallbacks(tickRunnable);
            mainHandler.postDelayed(tickRunnable, SESSION_TICK_MS);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // Only treat as background when ALL activities are stopped
        if (isAppInForeground) {
            // Check if there are other visible activities (multi-window, pip, etc.)
            // Simple heuristic: if this activity is finishing/destroyed, count it
            if (activity.isFinishing() || activity.isDestroyed()) {
                // Might still be other activities — check via a delayed post
                mainHandler.postDelayed(() -> {
                    if (isAppInForeground && !isAnyActivityVisible(activity)) {
                        onAppBackgrounded();
                    }
                }, 200);
            }
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (!isAppInForeground) {
            onAppForegrounded();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // Don't treat pause as background — user might be in multi-window
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (isAppInForeground && activity.isDestroyed()) {
            mainHandler.postDelayed(() -> {
                if (isAppInForeground && !isAnyActivityVisible(activity)) {
                    onAppBackgrounded();
                }
            }, 200);
        }
    }

    private void onAppForegrounded() {
        isAppInForeground = true;
        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.postDelayed(tickRunnable, SESSION_TICK_MS);
        Log.d(TAG, "App foregrounded — session timer active, accumulated=" + accumulatedSessionMs + "ms");
    }

    private void onAppBackgrounded() {
        isAppInForeground = false;
        mainHandler.removeCallbacks(tickRunnable);
        Log.d(TAG, "App backgrounded — session timer paused, total accumulated=" + accumulatedSessionMs + "ms");
    }

    private boolean isAnyActivityVisible(Activity exclude) {
        Application app = applicationRef != null ? applicationRef.get() : null;
        if (app == null) return false;
        // Fallback: if we can't determine visibility, assume still in foreground
        // to avoid false background detections
        return true;
    }

    /**
     * Clean up — unregister callbacks. Call from Application.onTerminate() for testing.
     */
    public void shutdown() {
        isAppInForeground = false;
        mainHandler.removeCallbacks(tickRunnable);
        Application app = applicationRef != null ? applicationRef.get() : null;
        if (app != null) {
            app.unregisterActivityLifecycleCallbacks(this);
        }
    }
}
