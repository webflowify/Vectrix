/*
 * PS1 Emulator for Android
 * Copyright (C) 2024-2026 MST. ROKIEA SULTANA
 * 2 No. College Gate Bylane, Mymensingh - 2207, Bangladesh (BD)
*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tansoft.ps1emulator.core;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Trace;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.tansoft.ps1emulator.input.InputDispatcher;
import com.tansoft.ps1emulator.storage.MemoryCardManager;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import androidx.preference.PreferenceManager;

public class EmulatorService extends Service {

    private static final String TAG = "EmulatorService";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "emulator_service_channel";

    private static String loadedRomPath = "";

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends android.os.Binder {
        public EmulatorService getService() {
            return EmulatorService.this;
        }
    }

    private HandlerThread emulationThread;
    private Handler emulationHandler;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile boolean emulationPaused = false;
    private volatile boolean userPaused = false;
    private volatile boolean fastForwardActive = false;
    private volatile boolean audioWasEnabledBeforeFF = true;
    private volatile boolean rewindActive = false;
    private volatile boolean slowMotionActive = false;

    private EmulationCallback callback;

    private String gameDiscId;

    private static final boolean DEBUG = false;

    /** Switch from coarse to fine-grained parking this long before the deadline. */
    private static final long PACING_SLACK_NANOS = 1_000_000L;   // 1 ms
    /** Park duration used during the final approach to the deadline. */
    private static final long PACING_FINE_STEP_NANOS = 20_000L;  // 20 us

    private static final String ATTRIBUTION_TAG = "EmulatorServiceAudio";

    public interface EmulationCallback {
        void onFrameCompleted();
        void onEmulationStopped();
    }

    public void setCallback(EmulationCallback cb) {
        this.callback = cb;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(withAttribution(newBase));
    }

    /**
     * Returns {@code base} tagged with {@link #ATTRIBUTION_TAG} for AppOps
     * accounting, or {@code base} unchanged when tagging is unavailable.
     *
     * <p>{@link Context#createAttributionContext(String)} was added in API 30.
     * minSdk is 24, so calling it unconditionally threw {@link NoSuchMethodError}
     * on Android 7.0-10 — inside {@code attachBaseContext}, i.e. before
     * {@code onCreate}, which killed the process on every game launch. The
     * attribution tag is a diagnostics aid only; dropping it on old platforms
     * costs nothing functionally.
     */
    private static Context withAttribution(Context base) {
        if (base == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return base;
        }
        try {
            return base.createAttributionContext(ATTRIBUTION_TAG);
        } catch (Throwable t) {
            // Some OEM/AOSP-derived builds and test runtimes report API >= 30 but
            // do not implement attribution. Never let diagnostics abort startup.
            android.util.Log.w(TAG, "createAttributionContext unavailable: " + t);
            return base;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Android 12+ (API 31) throws ForegroundServiceStartNotAllowedException
        // from startForeground() when the app is not currently allowed to start a
        // foreground service. That happens if this service is ever brought up
        // while backgrounded (e.g. a system restart we could not fully prevent,
        // or a foreground-state race during launch). A foreground service that
        // cannot promote itself to the foreground is useless here, so stop
        // gracefully instead of letting the exception crash the process.
        //
        // RuntimeException covers ForegroundServiceStartNotAllowedException
        // (an AndroidRuntimeException) and SecurityException; we deliberately
        // avoid naming the API 31+ class to stay verifier-safe on minSdk (24).
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException e) {
            android.util.Log.e(TAG, "startForeground not allowed; stopping service", e);
            stopSelf();
            return;
        }

        emulationThread = new HandlerThread("EmulationThread");
        emulationThread.start();
        emulationHandler = new Handler(emulationThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_NOT_STICKY (not START_STICKY): if the host process dies while
        // the app is in the background, the system must NOT re-create this
        // service from the background. Re-creating it would run onCreate() ->
        // startForeground() while backgrounded, which throws
        // ForegroundServiceStartNotAllowedException on API 31+ and crashes the
        // process. The service is meaningless without the foreground
        // EmulationActivity, so on process death the user simply relaunches
        // from the activity, which — being foreground — can start the FGS
        // legally.
        return START_NOT_STICKY;
    }

    public void startEmulation(String biosPath, String romPath) {
        gameDiscId = deriveDiscId(romPath);

        MemoryCardManager.initializeCard(this, gameDiscId, 0);
        MemoryCardManager.initializeCard(this, gameDiscId, 1);

        String card1Path = MemoryCardManager.getCardFile(this, gameDiscId, 0).getAbsolutePath();
        String card2Path = MemoryCardManager.getCardFile(this, gameDiscId, 1).getAbsolutePath();

        emulationHandler.post(() -> {
            android.util.Log.d("EmulatorService", "startEmulation: biosPath=" + biosPath + " romPath=" + romPath);

            Trace.beginSection("EmulatorService: nativeInit");
            int initResult = EmulatorBridge.nativeInit(biosPath, card1Path, card2Path);
            Trace.endSection();

            android.util.Log.d("EmulatorService", "nativeInit returned: " + initResult);

            if (initResult != 0) {
                android.util.Log.e("EmulatorService", "nativeInit FAILED — stopping service");
                stopSelf();
                return;
            }

            Trace.beginSection("EmulatorService: nativeLoadGame");
            int loadResult = EmulatorBridge.nativeLoadGame(romPath);
            Trace.endSection();

            android.util.Log.d("EmulatorService", "nativeLoadGame returned: " + loadResult);

            if (loadResult != 0) {
                android.util.Log.e("EmulatorService", "nativeLoadGame FAILED (code=" + loadResult
                        + ") romPath=" + romPath + " — stopping service");
                if (callback != null) {
                    callback.onEmulationStopped();
                }
                Trace.beginSection("EmulatorService: nativeShutdown");
                EmulatorBridge.nativeShutdown();
                Trace.endSection();
                stopSelf();
                return;
            }

            android.util.Log.i("EmulatorService", "Game loaded — starting emulation loop");
            loadedRomPath = romPath;
            running = true;

            // Clear framebuffer to prevent stale data from previous sessions
            EmulatorBridge.clearFramebufferBuffer();

            ByteBuffer audioBuffer = EmulatorBridge.getAudioBuffer();

            // Query target FPS from the core (NTSC=60, PAL=50)
            float targetFps = EmulatorBridge.nativeGetFps();
            if (targetFps <= 0) targetFps = 60.0f;
            long targetFrameNanos = (long) (1_000_000_000L / targetFps);
            android.util.Log.i("EmulatorService", "Frame pacing: target " + targetFps + " fps ("
                    + (targetFrameNanos / 1_000_000.0) + " ms/frame)");

            // Raise scheduling priority: the emulation thread must not be
            // pre-empted by UI/background work or frames arrive late and judder.
            // (Apps lack CAP_SYS_NICE, so SCHED_RR is unavailable — nice level is
            // the strongest lever we actually have.)
            android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Absolute-deadline frame pacing. Tracking a running deadline rather
            // than measuring each frame independently stops scheduler wakeup
            // jitter (1-4 ms is typical on Android) from accumulating into a
            // permanent frame-rate deficit.
            long nextFrameDeadline = System.nanoTime() + targetFrameNanos;

            while (running) {
                boolean wasPaused = false;
                while (paused && running) {
                    emulationPaused = true;
                    wasPaused = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                emulationPaused = false;
                if (!running) break;
                if (wasPaused) {
                    // Don't try to "catch up" on time spent paused.
                    nextFrameDeadline = System.nanoTime() + targetFrameNanos;
                }

                if (DEBUG) {
                    Trace.beginSection("EmulatorService: frame");
                }

                // Get input state
                InputDispatcher input = InputDispatcher.getInstance();
                int buttons = input.getPhysicalButtons() | input.getVirtualButtons();
                float analogX = input.getAnalogX();
                float analogY = input.getAnalogY();
                float rightAnalogX = input.getRightAnalogX();
                float rightAnalogY = input.getRightAnalogY();

                // Batched emulation frame: input -> run -> framebuffer + audio.
                // The back buffer identity changes on every swap, so it has to be
                // re-fetched each iteration.
                int geometry = EmulatorBridge.nativeEmulateFrame(buttons, analogX, analogY,
                        rightAnalogX, rightAnalogY,
                        EmulatorBridge.getFramebufferBuffer(), audioBuffer);

                // geometry == 0 means the core duped the frame: nothing new was
                // drawn, so don't publish or ask for a redraw.
                if (geometry != 0) {
                    EmulatorBridge.swapFramebuffers(geometry >>> 16, geometry & 0xFFFF);
                    if (callback != null) {
                        callback.onFrameCompleted();
                    }
                }

                if (DEBUG) {
                    Trace.endSection();
                }

                // Advance the deadline by exactly one frame period. Fast-forward
                // shortens the period, slow-motion lengthens it; the native side
                // already runs N retro_run() calls per iteration when fast
                // forwarding, so the period is divided to match.
                long framePeriod = targetFrameNanos;
                if (fastForwardActive) {
                    int multiplier = EmulatorBridge.nativeGetFastForwardSpeed();
                    if (multiplier > 1) {
                        framePeriod = framePeriod / multiplier;
                    }
                } else if (slowMotionActive) {
                    int divisor = EmulatorBridge.nativeGetSlowMotionDivisor();
                    if (divisor > 1) {
                        framePeriod = framePeriod * divisor;
                    }
                }
                nextFrameDeadline += framePeriod;

                if (!sleepUntil(nextFrameDeadline)) break;

                // If we've fallen more than two frames behind (long GC pause, CD
                // seek, thermal throttle) resync instead of sprinting to catch
                // up, which would only produce a burst of fast-forwarded frames.
                long now = System.nanoTime();
                if (now - nextFrameDeadline > 2 * framePeriod) {
                    nextFrameDeadline = now + framePeriod;
                }
            }

            EmulatorBridge.nativeShutdown();
            if (callback != null) {
                callback.onEmulationStopped();
            }
            stopSelf();
        });
    }

    /**
     * Blocks until {@code deadlineNanos}. Uses {@link LockSupport#parkNanos} rather
     * than {@link Thread#sleep(long, int)} because the latter silently discards its
     * nanosecond argument on Android — a 16.666 ms frame target would become a flat
     * 17 ms sleep, i.e. ~58 fps with visible judder.
     *
     * <p>The final millisecond is waited out in short parks so the wake-up lands
     * close to the deadline without burning a core on a busy-spin.
     *
     * @return false if the wait was aborted (shutdown or interrupt).
     */
    private boolean sleepUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) return true;
            if (!running || Thread.currentThread().isInterrupted()) return false;

            if (remaining > PACING_SLACK_NANOS) {
                LockSupport.parkNanos(remaining - PACING_SLACK_NANOS);
            } else {
                LockSupport.parkNanos(PACING_FINE_STEP_NANOS);
            }
        }
    }

    public void stopEmulation() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    /**
     * User-initiated pause. Sets userPaused=true so that internal
     * operations (save/load/rewind) won't auto-resume.
     * Clears input to prevent stuck buttons on resume.
     */
    public void userPause() {
        userPaused = true;
        pause();
        InputDispatcher.getInstance().setPhysicalButtons(0);
        InputDispatcher.getInstance().setAnalog(0f, 0f);
        InputDispatcher.getInstance().setAnalogRight(0f, 0f);
        updateNotification();
    }

    /**
     * User-initiated resume. Clears userPaused and resumes emulation.
     */
    public void userResume() {
        userPaused = false;
        resume();
        updateNotification();
    }

    /**
     * Toggle user pause state. Returns the new paused state.
     */
    public boolean togglePause() {
        if (userPaused) {
            userResume();
        } else {
            userPause();
        }
        return userPaused;
    }

    public boolean isUserPaused() {
        return userPaused;
    }

    /**
     * Toggle fast-forward mode. When enabled, the emulation runs at the
     * configured multiplier speed and audio is muted.
     */
    public void setFastForward(boolean active) {
        if (fastForwardActive == active) return;
        // Mutual exclusion: disable slow motion when enabling fast-forward
        if (active && slowMotionActive) {
            setSlowMotion(false);
        }
        fastForwardActive = active;
        EmulatorBridge.nativeSetFastForwardEnabled(active);
        if (active) {
            // Remember audio state before muting
            audioWasEnabledBeforeFF = PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getBoolean("audio_enabled", true);
            EmulatorBridge.nativeSetAudioEnabled(false);
            EmulatorBridge.nativeClearAudioRingBuffer();
        } else {
            // Restore audio to its previous state
            EmulatorBridge.nativeSetAudioEnabled(audioWasEnabledBeforeFF);
        }
    }

    public boolean isFastForwardActive() {
        return fastForwardActive;
    }

    /**
     * Toggle slow-motion mode. When enabled, the emulation runs at a fraction
     * of normal speed with pitch-shifted audio (classic slow-mo effect).
     */
    public void setSlowMotion(boolean active) {
        if (slowMotionActive == active) return;
        // Mutual exclusion: disable fast-forward when enabling slow motion
        if (active && fastForwardActive) {
            setFastForward(false);
        }
        slowMotionActive = active;
        EmulatorBridge.nativeSetSlowMotionEnabled(active);
        if (active) {
            int divisor = new SettingsHelper(this).getSlowMotionSpeed();
            EmulatorBridge.nativeSetSlowMotionDivisor(divisor);
        }
        // Clear ring buffer to prevent audio artifacts during transition
        EmulatorBridge.nativeClearAudioRingBuffer();
    }

    public boolean isSlowMotionActive() {
        return slowMotionActive;
    }

    /**
     * Enable or disable rewind capture. When enabled, the emulation core
     * captures state snapshots at the configured interval.
     */
    public void setRewindEnabled(boolean enabled) {
        rewindActive = enabled;
        EmulatorBridge.nativeSetRewindEnabled(enabled);
    }

    /**
     * Set the rewind depth in seconds. Computes the snapshot interval:
     * interval = (depth_seconds * fps) / 30 snapshots
     * At 60fps: 10s → interval=20, 30s → interval=60, 60s → interval=120
     */
    public void setRewindDepth(int seconds) {
        float fps = EmulatorBridge.nativeGetFps();
        if (fps <= 0) fps = 60.0f;
        int interval = (int) ((seconds * fps) / 30.0f);
        if (interval < 1) interval = 1;
        EmulatorBridge.nativeSetRewindInterval(interval);
    }

    /**
     * Perform a single rewind step. Pauses emulation briefly, restores
     * the previous snapshot, clears audio, then resumes.
     * Returns true if a snapshot was restored, false if buffer is empty.
     */
    public boolean rewindStep() {
        if (!running) return false;
        boolean wasAlreadyPaused = paused;
        pauseAndWait();
        try {
            boolean result = EmulatorBridge.nativeRewindStep();
            return result;
        } finally {
            EmulatorBridge.nativeClearAudioRingBuffer();
            if (!wasAlreadyPaused) {
                resume();
            }
        }
    }

    /**
     * Check if the rewind buffer has at least one snapshot available.
     */
    public boolean isRewindBufferAvailable() {
        return EmulatorBridge.nativeGetRewindBufferCount() > 0;
    }

    /**
     * Get the number of snapshots currently in the rewind buffer.
     */
    public int getRewindBufferCount() {
        return EmulatorBridge.nativeGetRewindBufferCount();
    }

    /**
     * Pause the emulation and wait until the current frame has completed
     * and the emulation thread is in the paused sleep loop. After this
     * returns, it is safe to call nativeSaveState / nativeLoadState.
     */
    public void pauseAndWait() {
        if (!running) {
            android.util.Log.w(TAG, "pauseAndWait: not running, returning");
            return;
        }
        android.util.Log.d(TAG, "pauseAndWait: pausing emulation");
        pause();
        // Spin until the emulation thread signals it has entered the pause loop
        int spinCount = 0;
        while (!emulationPaused && running) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            if (++spinCount > 2000) break; // 10 s safety net
        }
        android.util.Log.d(TAG, "pauseAndWait: emulation paused (spinCount=" + spinCount + ")");
    }

    /**
     * Thread-safe save: pauses emulation, performs the save on the calling
     * thread (safe because emulation is paused), then resumes.
     */
    public boolean saveStateSync(String path) {
        android.util.Log.d(TAG, "saveStateSync: path=" + path + " running=" + running);
        if (!running) return false;
        boolean wasAlreadyPaused = paused;
        pauseAndWait();
        try {
            android.util.Log.d(TAG, "saveStateSync: calling nativeSaveState...");
            int result = EmulatorBridge.nativeSaveState(path);
            android.util.Log.d(TAG, "saveStateSync: nativeSaveState returned " + result);
            return result == 0;
        } finally {
            if (!wasAlreadyPaused) {
                resume();
            }
            android.util.Log.d(TAG, "saveStateSync: resumed emulation");
        }
    }

    /**
     * Thread-safe load: pauses emulation, performs the load on the calling
     * thread (safe because emulation is paused), then resumes.
     */
    public boolean loadStateSync(String path) {
        android.util.Log.d(TAG, "loadStateSync: path=" + path + " running=" + running);
        if (!running) return false;
        boolean wasAlreadyPaused = paused;
        pauseAndWait();
        try {
            android.util.Log.d(TAG, "loadStateSync: calling nativeLoadState...");
            int result = EmulatorBridge.nativeLoadState(path);
            android.util.Log.d(TAG, "loadStateSync: nativeLoadState returned " + result);
            return result == 0;
        } finally {
            if (!wasAlreadyPaused) {
                resume();
            }
            android.util.Log.d(TAG, "loadStateSync: resumed emulation");
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        emulationThread.quitSafely();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    public static String getLoadedRomPath() {
        return loadedRomPath;
    }

    public String getGameDiscId() {
        return gameDiscId;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private String deriveDiscId(String romPath) {
        if (romPath == null) return "default";
        File f = new File(romPath);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emulator Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        boolean showPause = userPaused;
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PS1 Emulator")
                .setContentText(showPause ? "Emulation paused" : "Emulation running")
                .setSmallIcon(showPause ? android.R.drawable.ic_media_pause
                                        : android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    /**
     * Refreshes the foreground-service notification (play/pause icon and text).
     *
     * <p>From API 33 posting requires POST_NOTIFICATIONS. If the user has not
     * granted it the post is silently dropped by the platform, so skip the call
     * rather than churn a Binder transaction. The service itself keeps running
     * either way — the notification is purely informational.
     */
    private void updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            // Denied: the platform drops the post anyway. Emulation is unaffected.
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }
}