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
package com.tansoft.ps1emulator.ui.emulation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.core.EmulatorBridge;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;
import com.tansoft.ps1emulator.core.EmulatorService;
import com.tansoft.ps1emulator.core.SettingsHelper;
import com.tansoft.ps1emulator.storage.ArchiveExtractor;
import com.tansoft.ps1emulator.storage.SaveStateManager;
import com.tansoft.ps1emulator.ui.savestate.SaveStateActivity;
import com.tansoft.ps1emulator.ui.settings.SettingsActivity;

import android.app.AlertDialog;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.tansoft.ps1emulator.input.ControllerMapper;
import com.tansoft.ps1emulator.input.InputDispatcher;
import com.tansoft.ps1emulator.input.Ps1Buttons;
import com.tansoft.ps1emulator.data.AppDatabase;
import com.tansoft.ps1emulator.data.GameDao;
import com.tansoft.ps1emulator.data.GameEntity;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EmulationActivity extends AppCompatActivity
        implements EmulatorService.EmulationCallback {

    private static final String TAG = "EmulationActivity";

    private static final String EXTRA_BIOS_PATH = "bios_path";
    private static final String EXTRA_ROM_PATH = "rom_path";
    private static final String EXTRA_GAME_DISC_ID = "game_disc_id";
    private static final String EXTRA_ARCHIVE_PATH = "archive_path";

    private GLSurfaceView glSurfaceView;
    private EmuGLRenderer renderer;
    private EmulatorService emulatorService;
    private SettingsHelper settingsHelper;
    private boolean serviceBound = false;
    private String biosPath;
    private String romPath;
    private String archivePath;
    private String currentGameDiscId;
    private long gameStartTimeMs;

    // Foreground-service start is deferred until the app is genuinely in the
    // foreground. See maybeStartEmulationService().
    private boolean emulationServiceStartRequested = false;
    private boolean emulationServiceStarted = false;

    private int currentButtons = 0;
    private float analogX = 0f;
    private float analogY = 0f;
    private float rightAnalogX = 0f;
    private float rightAnalogY = 0f;

    private static final float ANALOG_DEADZONE = 0.12f;
    private static final int DPAD_MASK = Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_DOWN
            | Ps1Buttons.BUTTON_LEFT | Ps1Buttons.BUTTON_RIGHT;

    private View fpsOverlay;
    private View vpadOverlay;
    private View pauseOverlay;

    // ─── Stretch-to-fullscreen toggle ────────────────────────────────
    private ImageButton fullscreenButton;
    private boolean stretchFullscreen = false;
    private static final String PREF_STRETCH_FULLSCREEN = "stretch_fullscreen";

    // ─── Menu ────────────────────────────────────────────────────────
    private ImageButton menuButton;
    private ImageButton fastForwardButton;
    private TextView fastForwardText;
    private ImageButton slowMotionButton;
    private TextView slowMotionText;
    private boolean rewindInProgress = false;
    private long lastRewindTime = 0;
    private static final long REWIND_MIN_INTERVAL_MS = 200;
    private final java.util.concurrent.ExecutorService rewindExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler rewindCountHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable rewindCountUpdater = new Runnable() {
        @Override
        public void run() {
            if (emulatorService != null && emulatorService.isRunning()) {
                int count = emulatorService.getRewindBufferCount();
                if (rewindMenuItem != null) {
                    rewindMenuItem.setText("Rewind (" + count + ")");
                }
            }
            rewindCountHandler.postDelayed(this, 500);
        }
    };
    private TextView rewindMenuItem;
    private android.app.Dialog menuDialog;
    private static final String PREF_LAST_SAVE_SLOT = "last_save_slot";
    private static final String PREF_AUTO_SAVE_SLOT = "auto_save_slot";
    private boolean emulationPausedForSaveState = false;

    private static final String KEY_PROGRESS_VISIBLE = "progress_visible";
    private static final String KEY_PROGRESS_TEXT = "progress_text";

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBound = true;
            EmulatorService.LocalBinder localBinder = (EmulatorService.LocalBinder) service;
            emulatorService = localBinder.getService();
            emulatorService.setCallback(EmulationActivity.this);
            // Guard against re-initialising the core when the activity is
            // recreated (e.g. a rotation config change) but the service is
            // still running — startEmulation would otherwise reset the game.
            if (!emulatorService.isRunning()) {
                emulatorService.startEmulation(biosPath, romPath);
            }

            // Enable rewind with configured depth
            int depthSeconds = settingsHelper.getRewindDepth();
            emulatorService.setRewindDepth(depthSeconds);
            emulatorService.setRewindEnabled(true);
            rewindCountHandler.post(rewindCountUpdater);

            updateGameStats();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            emulatorService = null;
        }
    };

public static Intent createIntent(android.content.Context context,
                                       String biosPath, String romPath, String gameDiscId) {
        Intent intent = new Intent(context, EmulationActivity.class);
        intent.putExtra(EXTRA_BIOS_PATH, biosPath);
        intent.putExtra(EXTRA_ROM_PATH, romPath);
        intent.putExtra(EXTRA_GAME_DISC_ID, gameDiscId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        enableImmersiveMode();

        // Set content view - Android resource system will select layout/ or layout-land/ based on orientation
        setContentView(R.layout.activity_emulation);

        settingsHelper = new SettingsHelper(this);

        stretchFullscreen = getPreferences(Context.MODE_PRIVATE)
                .getBoolean(PREF_STRETCH_FULLSCREEN, false);

        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);

        TextView fpsText = findViewById(R.id.fps_text);
        fpsOverlay = fpsText;
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            fpsText.setVisibility(View.GONE);
        }

        vpadOverlay = findViewById(R.id.gamepad_overlay);
        vpadOverlay.setOnTouchListener((v, event) -> {
            dismissMenu();
            return false;
        });

        pauseOverlay = findViewById(R.id.pause_overlay);

        // When user touches the game area (GLSurfaceView), also show the gamepad
        // This handles portrait mode where the gamepad only covers the bottom half
        glSurfaceView.setOnTouchListener((v, event) -> {
            dismissMenu();
            if (vpadOverlay instanceof com.tansoft.ps1emulator.input.VirtualGamepadView) {
                ((com.tansoft.ps1emulator.input.VirtualGamepadView) vpadOverlay).show();
            }
            return false;
        });

        renderer = new EmuGLRenderer(fpsText);
        glSurfaceView.setRenderer(renderer);
        // Draw exactly one GL frame per emulated frame (requested from
        // onFrameCompleted). Rendering continuously instead lets the GL thread
        // run at the panel's refresh rate — 90/120 Hz on many devices —
        // independently of the ~60 Hz emulator, which duplicates and drops
        // frames at the beat frequency and reads a buffer that is being
        // rewritten. That is what makes gameplay look choppy even at full speed.
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        biosPath = getIntent().getStringExtra(EXTRA_BIOS_PATH);
        romPath  = getIntent().getStringExtra(EXTRA_ROM_PATH);
        archivePath     = getIntent().getStringExtra(EXTRA_ARCHIVE_PATH);
        currentGameDiscId = getIntent().getStringExtra(EXTRA_GAME_DISC_ID);
        if (currentGameDiscId == null || currentGameDiscId.isEmpty()) {
            currentGameDiscId = deriveDiscId(romPath);
        }
        android.util.Log.d(TAG, "onCreate: gameDiscId=" + currentGameDiscId + " biosPath=" + biosPath + " romPath=" + romPath);

        // ── ADD THESE LOGS ─────────────────────────────────────────────────────
        android.util.Log.d("EmulationActivity", "onCreate: biosPath=" + biosPath);
        android.util.Log.d("EmulationActivity", "onCreate: romPath=" + romPath);
        android.util.Log.d("EmulationActivity", "onCreate: archivePath=" + archivePath);
        android.util.Log.d("EmulationActivity", "onCreate: gameDiscId=" + currentGameDiscId);
        if (romPath != null) {
            java.io.File f = new java.io.File(romPath);
            android.util.Log.d("EmulationActivity", "romFile: exists=" + f.exists()
                    + " readable=" + f.canRead() + " size=" + f.length());
        }
        // ──────────────────────────────────────────────────────────────────────

        if (romPath == null) {
            Toast.makeText(this, "Missing ROM path", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // If this game came from an archive, ensure cached files still exist
        if (archivePath != null) {
            android.util.Log.d("EmulationActivity", "Archive path detected — will extract before launch");
            ensureArchiveExtracted(this::requestStartEmulationService);
        } else {
            android.util.Log.d("EmulationActivity", "Direct ROM path — launching service directly");
            requestStartEmulationService();
        }

        initMenu();
        initFastForwardButton();
        initSlowMotionButton();
        initFullscreenButton();
        applySettings();
        applyAutoRotateSetting();
        updateLayoutForOrientation();
    }

    private String deriveDiscId(String romPath) {
        if (romPath == null) return "default";
        java.io.File f = new java.io.File(romPath);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private void updateLayoutForOrientation() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        boolean isPortrait = screenHeight > screenWidth;

        android.widget.FrameLayout.LayoutParams glParams = (android.widget.FrameLayout.LayoutParams) glSurfaceView.getLayoutParams();
        android.widget.FrameLayout.LayoutParams padParams = (android.widget.FrameLayout.LayoutParams) vpadOverlay.getLayoutParams();

        int safeMarginTop = (int) (42 * metrics.density); // for camera cutout

        if (isPortrait) {
            // Game screen takes top half
            glParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
            glParams.height = screenHeight / 2 - safeMarginTop;
            glParams.topMargin = safeMarginTop;
            glParams.gravity = android.view.Gravity.TOP;

            // Gamepad takes bottom half
            padParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
            padParams.height = screenHeight / 2;
            padParams.topMargin = screenHeight / 2;
            padParams.gravity = android.view.Gravity.TOP;
            
            if (fpsOverlay != null) {
                android.widget.FrameLayout.LayoutParams fpsParams = (android.widget.FrameLayout.LayoutParams) fpsOverlay.getLayoutParams();
                fpsParams.topMargin = safeMarginTop + (int)(8 * metrics.density);
                fpsOverlay.setLayoutParams(fpsParams);
            }
            if (fullscreenButton != null) {
                fullscreenButton.setVisibility(View.GONE);
            }
            if (menuButton != null) {
                android.widget.FrameLayout.LayoutParams menuParams = (android.widget.FrameLayout.LayoutParams) menuButton.getLayoutParams();
                menuParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                menuParams.topMargin = 0;
                menuParams.bottomMargin = safeMarginTop + (int)(12 * metrics.density);
                menuParams.leftMargin = (int)(12 * metrics.density);
                menuParams.rightMargin = 0;
                menuButton.setLayoutParams(menuParams);
            }
            if (fastForwardButton != null) {
                android.widget.FrameLayout.LayoutParams ffParams = (android.widget.FrameLayout.LayoutParams) fastForwardButton.getLayoutParams();
                ffParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                ffParams.topMargin = 0;
                ffParams.bottomMargin = safeMarginTop + (int)(12 * metrics.density);
                ffParams.leftMargin = 0;
                ffParams.rightMargin = (int)(12 * metrics.density);
                fastForwardButton.setLayoutParams(ffParams);
            }
            if (fastForwardText != null) {
                android.widget.FrameLayout.LayoutParams fftParams = (android.widget.FrameLayout.LayoutParams) fastForwardText.getLayoutParams();
                fftParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                fftParams.bottomMargin = safeMarginTop + (int)(64 * metrics.density);
                fftParams.rightMargin = (int)(12 * metrics.density);
                fastForwardText.setLayoutParams(fftParams);
            }
            if (slowMotionButton != null) {
                android.widget.FrameLayout.LayoutParams smParams = (android.widget.FrameLayout.LayoutParams) slowMotionButton.getLayoutParams();
                smParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                smParams.topMargin = 0;
                smParams.bottomMargin = safeMarginTop + (int)(12 * metrics.density);
                smParams.leftMargin = 0;
                smParams.rightMargin = (int)(68 * metrics.density);
                slowMotionButton.setLayoutParams(smParams);
            }
            if (slowMotionText != null) {
                android.widget.FrameLayout.LayoutParams smtParams = (android.widget.FrameLayout.LayoutParams) slowMotionText.getLayoutParams();
                smtParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                smtParams.bottomMargin = safeMarginTop + (int)(64 * metrics.density);
                smtParams.rightMargin = (int)(68 * metrics.density);
                slowMotionText.setLayoutParams(smtParams);
            }
        } else {
            // Landscape: by default keep the game's native 4:3 aspect ratio with
            // letterbox bars on the left/right. When the fullscreen toggle is on,
            // stretch the game to fill the entire device (full width + height),
            // discarding the aspect ratio. The GL renderer already fills whatever
            // surface it is given, so just changing the surface size is enough.
            if (stretchFullscreen) {
                glParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
                glParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
                glParams.topMargin = 0;
                glParams.gravity = android.view.Gravity.CENTER;
            } else {
                final float PS1_ASPECT = 4f / 3f;
                int gameHeight = screenHeight;
                int gameWidth = (int) (gameHeight * PS1_ASPECT);
                if (gameWidth > screenWidth) {
                    gameWidth = screenWidth;
                    gameHeight = (int) (gameWidth / PS1_ASPECT);
                }
                glParams.width = gameWidth;
                glParams.height = gameHeight;
                glParams.topMargin = 0;
                glParams.gravity = android.view.Gravity.CENTER;
            }

            // Gamepad stays full-screen so its buttons surround the centered game.
            padParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
            padParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT;
            padParams.topMargin = 0;
            padParams.gravity = android.view.Gravity.CENTER;

            if (fpsOverlay != null) {
                android.widget.FrameLayout.LayoutParams fpsParams = (android.widget.FrameLayout.LayoutParams) fpsOverlay.getLayoutParams();
                // Sit the FPS readout below the fullscreen toggle (top-left corner).
                fpsParams.topMargin = (int)(64 * metrics.density);
                fpsOverlay.setLayoutParams(fpsParams);
            }
            if (fullscreenButton != null) {
                fullscreenButton.setVisibility(View.VISIBLE);
            }
            if (menuButton != null) {
                android.widget.FrameLayout.LayoutParams menuParams = (android.widget.FrameLayout.LayoutParams) menuButton.getLayoutParams();
                menuParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                menuParams.topMargin = 0;
                menuParams.bottomMargin = (int)(12 * metrics.density);
                menuParams.leftMargin = (int)(12 * metrics.density);
                menuParams.rightMargin = 0;
                menuButton.setLayoutParams(menuParams);
            }
            if (fastForwardButton != null) {
                android.widget.FrameLayout.LayoutParams ffParams = (android.widget.FrameLayout.LayoutParams) fastForwardButton.getLayoutParams();
                ffParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                ffParams.topMargin = 0;
                ffParams.bottomMargin = (int)(12 * metrics.density);
                ffParams.leftMargin = 0;
                ffParams.rightMargin = (int)(12 * metrics.density);
                fastForwardButton.setLayoutParams(ffParams);
            }
            if (fastForwardText != null) {
                android.widget.FrameLayout.LayoutParams fftParams = (android.widget.FrameLayout.LayoutParams) fastForwardText.getLayoutParams();
                fftParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                fftParams.bottomMargin = (int)(64 * metrics.density);
                fftParams.rightMargin = (int)(12 * metrics.density);
                fastForwardText.setLayoutParams(fftParams);
            }
            if (slowMotionButton != null) {
                android.widget.FrameLayout.LayoutParams smParams = (android.widget.FrameLayout.LayoutParams) slowMotionButton.getLayoutParams();
                smParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                smParams.topMargin = 0;
                smParams.bottomMargin = (int)(12 * metrics.density);
                smParams.leftMargin = 0;
                smParams.rightMargin = (int)(68 * metrics.density);
                slowMotionButton.setLayoutParams(smParams);
            }
            if (slowMotionText != null) {
                android.widget.FrameLayout.LayoutParams smtParams = (android.widget.FrameLayout.LayoutParams) slowMotionText.getLayoutParams();
                smtParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                smtParams.bottomMargin = (int)(64 * metrics.density);
                smtParams.rightMargin = (int)(68 * metrics.density);
                slowMotionText.setLayoutParams(smtParams);
            }
        }
        glSurfaceView.setLayoutParams(glParams);
        vpadOverlay.setLayoutParams(padParams);
    }

    private void applyAutoRotateSetting() {
        if (settingsHelper == null) return;
        if (settingsHelper.isAutoRotateEnabled()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        rewindCountHandler.removeCallbacks(rewindCountUpdater);
        // Only disable features if NOT user-paused
        if (emulatorService != null && !emulatorService.isUserPaused()) {
            if (emulatorService.isFastForwardActive()) {
                emulatorService.setFastForward(false);
                updateFastForwardUI(false);
            }
            if (emulatorService.isSlowMotionActive()) {
                emulatorService.setSlowMotion(false);
                updateSlowMotionUI(false);
            }
            emulatorService.setRewindEnabled(false);
        }
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
        savePlayTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The app is now guaranteed foreground — retry the FGS start if it was
        // previously deferred by a start-not-allowed rejection.
        maybeStartEmulationService();
        android.util.Log.d(TAG, "onResume: emulationPausedForSaveState=" + emulationPausedForSaveState
                + " emulatorService=" + (emulatorService != null)
                + " serviceBound=" + serviceBound);
        if (emulationPausedForSaveState && emulatorService != null) {
            // Only auto-resume if the user didn't manually pause
            if (!emulatorService.isUserPaused()) {
                android.util.Log.d(TAG, "onResume: resuming emulation after save state operation");
                emulatorService.resume();
            }
            emulationPausedForSaveState = false;
        }
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        // Re-enable rewind only if not user-paused
        if (emulatorService != null && emulatorService.isRunning()
                && !emulatorService.isUserPaused()) {
            int depthSeconds = settingsHelper.getRewindDepth();
            emulatorService.setRewindDepth(depthSeconds);
            emulatorService.setRewindEnabled(true);
            rewindCountHandler.post(rewindCountUpdater);
        }
        // Restore render mode and pause overlay based on pause state
        if (emulatorService != null && emulatorService.isUserPaused()) {
            if (glSurfaceView != null) {
                glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            }
            if (pauseOverlay != null) {
                pauseOverlay.setVisibility(View.VISIBLE);
            }
        } else {
            if (pauseOverlay != null) {
                pauseOverlay.setVisibility(View.GONE);
            }
        }
        applyAutoRotateSetting();
        applySettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rewindCountHandler.removeCallbacks(rewindCountUpdater);
        rewindExecutor.shutdownNow();
        // Ensure fast-forward, slow-motion, rewind and pause are off before stopping
        if (emulatorService != null) {
            if (emulatorService.isUserPaused()) {
                emulatorService.userResume();
            }
            if (emulatorService.isFastForwardActive()) {
                emulatorService.setFastForward(false);
            }
            if (emulatorService.isSlowMotionActive()) {
                emulatorService.setSlowMotion(false);
            }
            emulatorService.setRewindEnabled(false);
        }
        stopEmulation();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        stopService(new Intent(this, EmulatorService.class));
    }

    /**
     * Back handling for the emulation screen.
     *
     * <p>Registered on the {@link androidx.activity.OnBackPressedDispatcher}:
     * from Android 16 (API 36) predictive back is on by default and a back
     * gesture no longer calls {@code Activity.onBackPressed()}, which would have
     * let the user leave mid-game without the save prompt.
     */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPressed();
                }
            };

    private void handleBackPressed() {
        if (emulatorService != null && emulatorService.isUserPaused()) {
            // Already user-paused — offer resume or exit
            new AlertDialog.Builder(this)
                .setTitle("Exit Emulation?")
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .setNegativeButton("Resume", (dialog, which) -> {
                    if (emulatorService != null) {
                        emulatorService.userResume();
                    }
                    if (glSurfaceView != null) {
                        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }
                    if (pauseOverlay != null) {
                        pauseOverlay.setVisibility(View.GONE);
                    }
                })
                .setOnCancelListener(dialog -> {
                    if (emulatorService != null) {
                        emulatorService.userResume();
                    }
                    if (glSurfaceView != null) {
                        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }
                    if (pauseOverlay != null) {
                        pauseOverlay.setVisibility(View.GONE);
                    }
                })
                .show();
        } else if (emulatorService != null && emulatorService.isRunning()) {
            emulatorService.pauseAndWait();

            new AlertDialog.Builder(this)
                .setTitle("Exit Emulation?")
                .setMessage("Save state before exiting?")
                .setPositiveButton("Save & Exit", (dialog, which) -> {
                    SaveStateManager.saveState(this, currentGameDiscId, 0);
                    finish();
                })
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setNeutralButton("Cancel", (dialog, which) -> {
                    if (emulatorService != null) {
                        emulatorService.resume();
                    }
                })
                .setOnCancelListener(dialog -> {
                    if (emulatorService != null) {
                        emulatorService.resume();
                    }
                })
                .show();
        } else {
            // Nothing running and nothing to save — same outcome as the
            // framework's default back behaviour for this activity.
            finish();
        }
    }

    private void initMenu() {
        menuButton = findViewById(R.id.btnMenu);
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> showMenu());
        }
    }

    private void initFastForwardButton() {
        fastForwardButton = findViewById(R.id.btnFastForward);
        fastForwardText = findViewById(R.id.fast_forward_text);
        if (fastForwardButton != null) {
            fastForwardButton.setOnClickListener(v -> toggleFastForward());
        }
    }

    private void initSlowMotionButton() {
        slowMotionButton = findViewById(R.id.btnSlowMotion);
        slowMotionText = findViewById(R.id.slow_motion_text);
        if (slowMotionButton != null) {
            slowMotionButton.setOnClickListener(v -> toggleSlowMotion());
        }
    }

    private void initFullscreenButton() {
        fullscreenButton = findViewById(R.id.btnFullscreen);
        if (fullscreenButton != null) {
            updateFullscreenButtonIcon();
            fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        }
    }

    private void toggleFullscreen() {
        stretchFullscreen = !stretchFullscreen;
        getPreferences(Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_STRETCH_FULLSCREEN, stretchFullscreen)
                .apply();
        updateFullscreenButtonIcon();
        updateLayoutForOrientation();
        // Repaint immediately so the stretched/letterboxed view updates even
        // while the emulation is paused (no new frames are being produced).
        if (glSurfaceView != null) {
            glSurfaceView.requestRender();
        }
        dismissMenu();
    }

    private void updateFullscreenButtonIcon() {
        if (fullscreenButton == null) return;
        fullscreenButton.setImageResource(stretchFullscreen
                ? R.drawable.ic_fullscreen_exit
                : R.drawable.ic_fullscreen_enter);
    }

    private void toggleFastForward() {
        if (emulatorService == null || !emulatorService.isRunning()) return;
        boolean newState = !emulatorService.isFastForwardActive();
        emulatorService.setFastForward(newState);
        updateFastForwardUI(newState);
    }

    private void updateFastForwardUI(boolean active) {
        if (fastForwardButton == null) return;
        if (active) {
            int speed = settingsHelper.getFastForwardSpeed();
            fastForwardButton.setColorFilter(0xFF06B6D4); // neon cyan tint
            if (fastForwardText != null) {
                fastForwardText.setText(speed + "x");
                fastForwardText.setVisibility(View.VISIBLE);
            }
        } else {
            fastForwardButton.clearColorFilter();
            if (fastForwardText != null) {
                fastForwardText.setVisibility(View.GONE);
            }
        }
    }

    private void toggleSlowMotion() {
        if (emulatorService == null || !emulatorService.isRunning()) return;
        boolean newState = !emulatorService.isSlowMotionActive();
        emulatorService.setSlowMotion(newState);
        updateSlowMotionUI(newState);
    }

    private void updateSlowMotionUI(boolean active) {
        if (slowMotionButton == null) return;
        if (active) {
            int divisor = settingsHelper.getSlowMotionSpeed();
            slowMotionButton.setColorFilter(0xFFFF6B6B); // soft red tint
            if (slowMotionText != null) {
                float speed = 1.0f / divisor;
                String label;
                if (divisor == 2) label = "0.5x";
                else if (divisor == 3) label = "0.33x";
                else label = "0.25x";
                slowMotionText.setText(label);
                slowMotionText.setVisibility(View.VISIBLE);
            }
        } else {
            slowMotionButton.clearColorFilter();
            if (slowMotionText != null) {
                slowMotionText.setVisibility(View.GONE);
            }
        }
    }

    private void togglePause() {
        if (emulatorService == null || !emulatorService.isRunning()) return;
        boolean nowPaused = emulatorService.togglePause();
        updatePauseUI(nowPaused);
    }

    private void updatePauseUI(boolean paused) {
        // Render mode no longer varies with pause state: the surface is always
        // RENDERMODE_WHEN_DIRTY and driven by onFrameCompleted(). While paused
        // the emulation thread stops producing frames, so rendering stops too.
        if (pauseOverlay != null) {
            pauseOverlay.setVisibility(paused ? View.VISIBLE : View.GONE);
        }
    }

    private void performRewindStep() {
        if (emulatorService == null || !emulatorService.isRunning()) return;
        long now = System.currentTimeMillis();
        if (now - lastRewindTime < REWIND_MIN_INTERVAL_MS) return;
        if (rewindInProgress) return;
        lastRewindTime = now;
        rewindInProgress = true;
        rewindExecutor.execute(() -> {
            try {
                emulatorService.rewindStep();
                runOnUiThread(() -> rewindInProgress = false);
            } catch (Exception e) {
                runOnUiThread(() -> rewindInProgress = false);
            }
        });
    }

    private void showMenu() {
        if (menuDialog != null && menuDialog.isShowing()) {
            menuDialog.dismiss();
        }

        menuDialog = new android.app.Dialog(this);
        menuDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        menuDialog.setContentView(R.layout.dialog_emulation_menu);
        menuDialog.setCancelable(true);
        menuDialog.setCanceledOnTouchOutside(true);

        if (menuDialog.getWindow() != null) {
            menuDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            menuDialog.getWindow().setLayout(
                    (int) (220 * getResources().getDisplayMetrics().density),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            menuDialog.getWindow().setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.START);
            android.view.WindowManager.LayoutParams lp = menuDialog.getWindow().getAttributes();
            lp.x = (int) (12 * getResources().getDisplayMetrics().density);
            lp.y = (int) (60 * getResources().getDisplayMetrics().density);
            menuDialog.getWindow().setAttributes(lp);
            menuDialog.getWindow().setDimAmount(0.3f);
        }

        TextView itemSaveState = menuDialog.findViewById(R.id.itemSaveState);
        TextView itemLoadState = menuDialog.findViewById(R.id.itemLoadState);
        TextView itemFastForward = menuDialog.findViewById(R.id.itemFastForward);
        TextView itemSlowMotion = menuDialog.findViewById(R.id.itemSlowMotion);
        rewindMenuItem = menuDialog.findViewById(R.id.itemRewind);
        TextView itemSettings = menuDialog.findViewById(R.id.itemSettings);
        TextView itemExit = menuDialog.findViewById(R.id.itemExit);

        boolean isPaused = emulatorService != null && emulatorService.isUserPaused();

        TextView itemPause = menuDialog.findViewById(R.id.itemPause);
        itemPause.setText(isPaused ? "Resume" : "Pause");
        itemPause.setOnClickListener(v -> {
            menuDialog.dismiss();
            togglePause();
        });

        boolean ffActive = emulatorService != null && emulatorService.isFastForwardActive();
        itemFastForward.setText(ffActive ? "Fast Forward (ON)" : "Fast Forward (OFF)");
        itemFastForward.setEnabled(!isPaused);
        itemFastForward.setAlpha(isPaused ? 0.4f : 1.0f);

        boolean smActive = emulatorService != null && emulatorService.isSlowMotionActive();
        itemSlowMotion.setText(smActive ? "Slow Motion (ON)" : "Slow Motion (OFF)");
        itemSlowMotion.setEnabled(!isPaused);
        itemSlowMotion.setAlpha(isPaused ? 0.4f : 1.0f);

        int rewindCount = (emulatorService != null && emulatorService.isRunning())
                ? emulatorService.getRewindBufferCount() : 0;
        rewindMenuItem.setText("Rewind (" + rewindCount + ")");
        rewindMenuItem.setEnabled(!isPaused);
        rewindMenuItem.setAlpha(isPaused ? 0.4f : 1.0f);

        itemSaveState.setOnClickListener(v -> {
            menuDialog.dismiss();
            if (emulatorService != null && emulatorService.isRunning()) {
                emulatorService.pauseAndWait();
                emulationPausedForSaveState = true;
            }
            startActivity(SaveStateActivity.createIntent(this, currentGameDiscId, true));
        });

        itemLoadState.setOnClickListener(v -> {
            menuDialog.dismiss();
            if (emulatorService != null && emulatorService.isRunning()) {
                emulatorService.pauseAndWait();
                emulationPausedForSaveState = true;
            }
            startActivity(SaveStateActivity.createIntent(this, currentGameDiscId, false));
        });

        itemFastForward.setOnClickListener(v -> {
            if (!isPaused) {
                toggleFastForward();
                boolean nowActive = emulatorService != null && emulatorService.isFastForwardActive();
                itemFastForward.setText(nowActive ? "Fast Forward (ON)" : "Fast Forward (OFF)");
            }
        });

        itemSlowMotion.setOnClickListener(v -> {
            if (!isPaused) {
                toggleSlowMotion();
                boolean nowActive = emulatorService != null && emulatorService.isSlowMotionActive();
                itemSlowMotion.setText(nowActive ? "Slow Motion (ON)" : "Slow Motion (OFF)");
            }
        });

        rewindMenuItem.setOnClickListener(v -> {
            if (!isPaused) {
                performRewindStep();
            }
        });

        itemSettings.setOnClickListener(v -> {
            menuDialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        itemExit.setOnClickListener(v -> {
            menuDialog.dismiss();
            stopEmulation();
            finish();
        });

        menuDialog.show();
    }

    private void dismissMenu() {
        if (menuDialog != null && menuDialog.isShowing()) {
            menuDialog.dismiss();
        }
    }

    // ─── Quick-save / quick-load ─────────────────────────────────────

    private void handleQuickSave() {
        saveState();
    }

    private void handleQuickLoad() {
        loadState();
    }

    private static int ps1ButtonNameToConstant(String name) {
        switch (name) {
            case "CROSS":    return Ps1Buttons.BUTTON_CROSS;
            case "CIRCLE":   return Ps1Buttons.BUTTON_CIRCLE;
            case "SQUARE":   return Ps1Buttons.BUTTON_SQUARE;
            case "TRIANGLE": return Ps1Buttons.BUTTON_TRIANGLE;
            case "DPAD_UP":  return Ps1Buttons.BUTTON_UP;
            case "DPAD_DOWN": return Ps1Buttons.BUTTON_DOWN;
            case "DPAD_LEFT": return Ps1Buttons.BUTTON_LEFT;
            case "DPAD_RIGHT": return Ps1Buttons.BUTTON_RIGHT;
            case "L1":       return Ps1Buttons.BUTTON_L1;
            case "R1":       return Ps1Buttons.BUTTON_R1;
            case "L2":       return Ps1Buttons.BUTTON_L2;
            case "R2":       return Ps1Buttons.BUTTON_R2;
            case "START":    return Ps1Buttons.BUTTON_START;
            case "SELECT":   return Ps1Buttons.BUTTON_SELECT;
            case "L3":       return Ps1Buttons.BUTTON_L3;
            case "R3":       return Ps1Buttons.BUTTON_R3;
            default:         return 0;
        }
    }

    // ─── Progress overlay ─────────────────────────────────────────────

    private void showProgress(String message) {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            TextView text = overlay.findViewById(R.id.progress_text);
            if (text != null) {
                text.setText(message);
            }
            View spinner = overlay.findViewById(R.id.progress_spinner);
            if (spinner != null) {
                spinner.setVisibility(View.VISIBLE);
            }
            View horizontalBar = overlay.findViewById(R.id.progress_bar_horizontal);
            if (horizontalBar != null) {
                horizontalBar.setVisibility(View.GONE);
            }
            View percentText = overlay.findViewById(R.id.progress_percent);
            if (percentText != null) {
                percentText.setVisibility(View.GONE);
            }
            View fileNameText = overlay.findViewById(R.id.progress_file_name);
            if (fileNameText != null) {
                fileNameText.setVisibility(View.GONE);
            }
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200);
        }
    }

    private void showExtractionProgress(String fileName, int percent) {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay == null) return;

        View spinner = overlay.findViewById(R.id.progress_spinner);
        if (spinner != null) {
            spinner.setVisibility(View.GONE);
        }

        TextView text = overlay.findViewById(R.id.progress_text);
        if (text != null) {
            text.setText("Extracting...");
        }

        TextView fileNameView = overlay.findViewById(R.id.progress_file_name);
        if (fileNameView != null) {
            if (fileName != null && !fileName.isEmpty()) {
                fileNameView.setText(fileName);
                fileNameView.setVisibility(View.VISIBLE);
            } else {
                fileNameView.setVisibility(View.GONE);
            }
        }

        android.widget.ProgressBar horizontalBar = overlay.findViewById(R.id.progress_bar_horizontal);
        if (horizontalBar != null) {
            horizontalBar.setVisibility(View.VISIBLE);
            horizontalBar.setProgress(percent);
        }

        TextView percentView = overlay.findViewById(R.id.progress_percent);
        if (percentView != null) {
            percentView.setVisibility(View.VISIBLE);
            percentView.setText(percent + "%");
        }
    }

    private void hideProgress() {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> overlay.setVisibility(View.GONE));
        }
    }

    private void saveState() {
        showProgress("Saving state…");
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int slot = prefs.getInt(PREF_LAST_SAVE_SLOT, 0);
        int finalSlot = slot;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (emulatorService != null && emulatorService.isRunning()) {
                    emulatorService.saveStateSync(
                            SaveStateManager.getSaveFile(this, currentGameDiscId, finalSlot).getAbsolutePath());
                } else {
                    SaveStateManager.saveState(this, currentGameDiscId, finalSlot);
                }
            } finally {
                runOnUiThread(this::hideProgress);
            }
        });
    }

    private void loadState() {
        showProgress("Loading save state…");
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int slot = prefs.getInt(PREF_LAST_SAVE_SLOT, 0);
        int finalSlot = slot;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (emulatorService != null && emulatorService.isRunning()) {
                    emulatorService.loadStateSync(
                            SaveStateManager.getSaveFile(this, currentGameDiscId, finalSlot).getAbsolutePath());
                } else {
                    SaveStateManager.loadState(this, currentGameDiscId, finalSlot);
                }
            } finally {
                runOnUiThread(this::hideProgress);
            }
        });
    }

    // ─── Configuration change ─────────────────────────────────────────

    private float applyDeadzone(float value) {
        if (Math.abs(value) < ANALOG_DEADZONE) {
            return 0f;
        }
        float sign = value > 0 ? 1f : -1f;
        return sign * (Math.abs(value) - ANALOG_DEADZONE) / (1f - ANALOG_DEADZONE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            outState.putBoolean(KEY_PROGRESS_VISIBLE, overlay.getVisibility() == View.VISIBLE);
            TextView text = overlay.findViewById(R.id.progress_text);
            if (text != null) {
                outState.putString(KEY_PROGRESS_TEXT, text.getText().toString());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(KEY_PROGRESS_VISIBLE, false)) {
            showProgress(savedInstanceState.getString(KEY_PROGRESS_TEXT, ""));
        }
    }

    private void stopEmulation() {
        if (emulatorService != null) {
            emulatorService.stopEmulation();
        }
    }

    /**
     * Requests the emulation foreground service to be started. The actual
     * {@code startForegroundService()} call is deferred until the app is allowed
     * to start a foreground service — see {@link #maybeStartEmulationService()}.
     *
     * <p>On API 31+ {@link android.app.ForegroundServiceStartNotAllowedException}
     * is thrown by {@code startForegroundService()} when the app is not currently
     * permitted to start a foreground service (e.g. a cold-start race during
     * {@code onCreate()} before the process is promoted to foreground, or an
     * API 34+ background-launch restriction for {@code specialUse} services).
     * Rather than crash, we remember the request and retry once the activity is
     * confirmed foreground (in {@code onResume()}/{@code onWindowFocusChanged()}).
     */
    private void requestStartEmulationService() {
        emulationServiceStartRequested = true;
        maybeStartEmulationService();
    }

    /**
     * Starts the foreground service if it has not been started yet and the app
     * is allowed to. If the system rejects the start (FGS-not-allowed), the
     * request is kept pending and retried later from a foreground callback.
     */
    private void maybeStartEmulationService() {
        if (emulationServiceStarted || !emulationServiceStartRequested) {
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent serviceIntent = new Intent(this, EmulatorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            // Binding can proceed only after a successful start; if the start
            // itself was rejected we must not bind (it would otherwise spin up a
            // background service that then fails its own startForeground()).
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
            emulationServiceStarted = true;
        } catch (RuntimeException e) {
            if (isForegroundServiceNotAllowed(e)) {
                // App not yet allowed to start the FGS — defer and retry when
                // the activity is genuinely foreground. Leave both flags as-is.
                android.util.Log.w(TAG,
                        "startForegroundService deferred (not allowed yet); will retry when foreground", e);
            } else {
                // A genuine, unrelated failure — surface it.
                throw e;
            }
        }
    }

    /**
     * Returns true if {@code t} represents a foreground-service-start rejection.
     *
     * <p>{@link android.app.ForegroundServiceStartNotAllowedException} only
     * exists on API 31+, so we avoid naming the class in a {@code catch} clause
     * (which would fail dex verification on older runtimes). Instead we match by
     * class name across the cause chain, which is verifier-safe on minSdk 24.
     */
    static boolean isForegroundServiceNotAllowed(Throwable t) {
        if (t instanceof SecurityException) {
            return true;
        }
        Throwable c = t;
        while (c != null) {
            if ("android.app.ForegroundServiceStartNotAllowedException"
                    .equals(c.getClass().getName())) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private void updateGameStats() {
        if (romPath == null || romPath.isEmpty()) return;
        
        gameStartTimeMs = System.currentTimeMillis();
        
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                GameDao gameDao = AppDatabase.getInstance(this).gameDao();
                GameEntity game = gameDao.getByPath(romPath);
                if (game != null) {
                    game.playCount++;
                    game.lastPlayedDate = gameStartTimeMs;
                    gameDao.update(game);
                }
            } catch (Exception e) {
                android.util.Log.e("EmulationActivity", "Failed to update game stats", e);
            }
        });
    }

    private void savePlayTime() {
        if (romPath == null || romPath.isEmpty() || gameStartTimeMs == 0) return;
        
        long playTimeMs = System.currentTimeMillis() - gameStartTimeMs;
        if (playTimeMs < 1000) return;
        
        long startTime = gameStartTimeMs;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                GameDao gameDao = AppDatabase.getInstance(this).gameDao();
                GameEntity game = gameDao.getByPath(romPath);
                if (game != null) {
                    game.totalPlayTimeMs += playTimeMs;
                    game.lastPlayedDate = startTime;
                    gameDao.update(game);
                }
            } catch (Exception e) {
                android.util.Log.e("EmulationActivity", "Failed to save play time", e);
            }
        });
    }

    private void ensureArchiveExtracted(Runnable onDone) {
        Uri archiveUri = Uri.parse(archivePath);
        android.util.Log.d("EmulationActivity", "ensureArchiveExtracted: checking " + archiveUri);
        if (ArchiveExtractor.isExtracted(this, archiveUri)) {
            updateRomPathFromArchive(archiveUri);
            android.util.Log.d("EmulationActivity", "ensureArchiveExtracted: romPath=" + romPath);
            if (romPath != null && !romPath.isEmpty()) {
                File romFile = new File(romPath);
                if (romFile.exists() && romFile.length() > 0) {
                    android.util.Log.d("EmulationActivity", "ensureArchiveExtracted: ROM OK, size=" + romFile.length());
                    onDone.run();
                    return;
                }
                android.util.Log.w("EmulationActivity", "ensureArchiveExtracted: ROM is 0 bytes, clearing cache");
            }
            ArchiveExtractor.clearCache(this, archiveUri);
        }
        android.util.Log.d("EmulationActivity", "ensureArchiveExtracted: starting extraction");
        showProgress("Extracting archive…");
        ArchiveExtractor.ExtractionProgressListener listener = (fileName, percent, bytesExtracted, totalBytes) -> {
            runOnUiThread(() -> showExtractionProgress(fileName, percent));
        };
        final boolean[] success = {true};
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ArchiveExtractor.extractToCache(this, archiveUri, listener);
            } catch (Exception e) {
                success[0] = false;
                runOnUiThread(() -> {
                    hideProgress();
                    Toast.makeText(this,
                            "Extraction failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            runOnUiThread(() -> {
                hideProgress();
                updateRomPathFromArchive(archiveUri);
                onDone.run();
            });
        });
    }

    private void updateRomPathFromArchive(Uri archiveUri) {
        File cacheDir = ArchiveExtractor.getCacheDir(this, archiveUri);
        File primary = ArchiveExtractor.findPrimaryGameFile(cacheDir);
        if (primary != null) {
            android.util.Log.d("EmulationActivity", "updateRomPathFromArchive: found " + primary.getName()
                    + " size=" + primary.length() + " path=" + primary.getAbsolutePath());
            romPath = primary.getAbsolutePath();
        } else {
            android.util.Log.w("EmulationActivity", "updateRomPathFromArchive: no primary game file found in " + cacheDir);
        }
    }

    public void applySettings() {
        if (settingsHelper == null) return;

        // FPS overlay
        if (fpsOverlay != null) {
            fpsOverlay.setVisibility(
                    settingsHelper.isFpsVisible() ? View.VISIBLE : View.GONE);
        }

        // Virtual gamepad visibility & opacity
        if (vpadOverlay != null) {
            vpadOverlay.setVisibility(
                    settingsHelper.isVpadVisible() ? View.VISIBLE : View.GONE);
            vpadOverlay.setAlpha(settingsHelper.getVpadOpacity() / 100f);
            if (vpadOverlay instanceof com.tansoft.ps1emulator.input.VirtualGamepadView) {
                ((com.tansoft.ps1emulator.input.VirtualGamepadView) vpadOverlay)
                        .applyJoystickModeFromPrefs(this);
            }
        }

        // Audio buffer size
        EmulatorBridge.setAudioBufferSize(settingsHelper.getAudioBufferSize());

        // Audio enabled/disabled
        EmulatorBridge.nativeSetAudioEnabled(settingsHelper.isAudioEnabled());

        // Fast-forward
        EmulatorBridge.nativeSetFastForwardSpeed(settingsHelper.getFastForwardSpeed());

        // Slow motion speed
        if (emulatorService != null && emulatorService.isSlowMotionActive()) {
            EmulatorBridge.nativeSetSlowMotionDivisor(settingsHelper.getSlowMotionSpeed());
        }

        // Rewind
        if (emulatorService != null && emulatorService.isRunning()) {
            int depthSeconds = settingsHelper.getRewindDepth();
            emulatorService.setRewindDepth(depthSeconds);
        }
    }

    private void enableImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Gaining window focus is the most reliable "we are foreground"
            // signal — retry any deferred foreground-service start here.
            maybeStartEmulationService();
            enableImmersiveMode();
        }
    }

    public GLSurfaceView getGlSurfaceView() {
        return glSurfaceView;
    }

    @Override
    public void onFrameCompleted() {
        if (glSurfaceView != null) {
            glSurfaceView.requestRender();
        }
    }

    @Override
    public void onEmulationStopped() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int source = event.getSource();
        boolean isGamepad = (source & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0;

        if (isGamepad) {
            int button = 0;

            // Check custom mapping first
            String customName = settingsHelper.reverseControllerMapping(
                    event.getDeviceId(), keyCode);
            if (customName != null) {
                button = ps1ButtonNameToConstant(customName);
            }

            // Fall back to default mapping
            if (button == 0) {
                button = ControllerMapper.keyCodeToPs1Button(keyCode);
            }

            if (button != 0) {
                currentButtons |= button;
                InputDispatcher.getInstance().setPhysicalButtons(currentButtons);
                InputDispatcher.getInstance().setAnalog(analogX, analogY);
                InputDispatcher.getInstance().setAnalogRight(rightAnalogX, rightAnalogY);

                boolean isPaused = emulatorService != null && emulatorService.isUserPaused();

                // Quick-save: Select + R1 (works even when paused)
                if ((currentButtons & Ps1Buttons.BUTTON_SELECT) != 0 &&
                    (currentButtons & Ps1Buttons.BUTTON_R1) != 0) {
                    handleQuickSave();
                    return true;
                }
                // Quick-load: Select + L1 (works even when paused)
                if ((currentButtons & Ps1Buttons.BUTTON_SELECT) != 0 &&
                    (currentButtons & Ps1Buttons.BUTTON_L1) != 0) {
                    handleQuickLoad();
                    return true;
                }
                // Fast-forward toggle: Select + L2 (ignored when paused)
                if (!isPaused &&
                    (currentButtons & Ps1Buttons.BUTTON_SELECT) != 0 &&
                    (currentButtons & Ps1Buttons.BUTTON_L2) != 0) {
                    toggleFastForward();
                    return true;
                }
                // Rewind step: Select + R2 (ignored when paused)
                if (!isPaused &&
                    (currentButtons & Ps1Buttons.BUTTON_SELECT) != 0 &&
                    (currentButtons & Ps1Buttons.BUTTON_R2) != 0) {
                    performRewindStep();
                    return true;
                }

                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int source = event.getSource();
        boolean isGamepad = (source & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0;

        if (isGamepad) {
            int button = 0;

            // Check custom mapping first
            String customName = settingsHelper.reverseControllerMapping(
                    event.getDeviceId(), keyCode);
            if (customName != null) {
                button = ps1ButtonNameToConstant(customName);
            }

            // Fall back to default mapping
            if (button == 0) {
                button = ControllerMapper.keyCodeToPs1Button(keyCode);
            }

            if (button != 0) {
                currentButtons &= ~button;
                InputDispatcher.getInstance().setPhysicalButtons(currentButtons);
                InputDispatcher.getInstance().setAnalog(analogX, analogY);
                InputDispatcher.getInstance().setAnalogRight(rightAnalogX, rightAnalogY);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        boolean isJoystick = (source & InputDevice.SOURCE_JOYSTICK) != 0;

        if (isJoystick) {
            // Left analog stick with deadzone (ISSUE-014)
            analogX = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X));
            analogY = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y));

            // Right analog stick (ISSUE-015)
            rightAnalogX = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z));
            rightAnalogY = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ));

            // D-Pad via hat axes (ISSUE-016)
            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            int hatButtons = 0;
            if (hatX > 0.5f) hatButtons |= Ps1Buttons.BUTTON_RIGHT;
            if (hatX < -0.5f) hatButtons |= Ps1Buttons.BUTTON_LEFT;
            if (hatY > 0.5f) hatButtons |= Ps1Buttons.BUTTON_DOWN;
            if (hatY < -0.5f) hatButtons |= Ps1Buttons.BUTTON_UP;
            currentButtons = (currentButtons & ~DPAD_MASK) | hatButtons;

            // Analog triggers (ISSUE-017)
            float lTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
            float rTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
            if (lTrigger > 0.5f) currentButtons |= Ps1Buttons.BUTTON_L2;
            else currentButtons &= ~Ps1Buttons.BUTTON_L2;
            if (rTrigger > 0.5f) currentButtons |= Ps1Buttons.BUTTON_R2;
            else currentButtons &= ~Ps1Buttons.BUTTON_R2;

            InputDispatcher.getInstance().setPhysicalButtons(currentButtons);
            InputDispatcher.getInstance().setAnalog(analogX, analogY);
            InputDispatcher.getInstance().setAnalogRight(rightAnalogX, rightAnalogY);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private boolean isInVerticalMode() {
        int orientation = getResources().getConfiguration().orientation;
        return orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        updateLayoutForOrientation();

        applySettings();
    }

    private static class EmuGLRenderer implements GLSurfaceView.Renderer {

        private final int fbWidth = EmulatorBridge.FB_WIDTH;
        private final int fbHeight = EmulatorBridge.FB_HEIGHT;
        private int[] textures = new int[1];

        private final float[] vertexData = new float[12];
        private final float[] texCoordData = new float[8];
        private final FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(12 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        private final FloatBuffer texCoordBuffer = ByteBuffer
                .allocateDirect(8 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        // Current emulator output geometry (PS1 default mode until told otherwise).
        private int scaledWidth = 320;
        private int scaledHeight = 240;
        /** Set once real pixels have been uploaded into the texture. */
        private boolean hasFrame = false;
        private float scaleTexCoordX = 1.0f;
        private float scaleTexCoordY = 1.0f;

        private int surfaceWidth;
        private int surfaceHeight;
        private int viewportX;
        private int viewportY;
        private int viewportW;
        private int viewportH;
        private long lastFpsTime = 0;
        private int frameCount = 0;
        private int currentFps = 0;
        private WeakReference<TextView> fpsTextViewRef;
        private int shaderProgram = 0;

        EmuGLRenderer(TextView fpsTextView) {
            this.fpsTextViewRef = new WeakReference<>(fpsTextView);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // A new GL context means a new, undefined texture.
            hasFrame = false;

            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_BLEND);

            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    fbWidth, fbHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glViewport(0, 0, fbWidth, fbHeight);

            String vertexShader =
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  gl_Position = aPosition;\n" +
                "  vTexCoord = aTexCoord;\n" +
                "}\n";

            // The core emits XRGB8888 while the texture is uploaded as GL_RGBA,
            // so red and blue arrive swapped. Correcting it here with a swizzle
            // is free on the GPU and removes a per-pixel CPU conversion loop
            // that ran over every pixel of every frame.
            String fragmentShader =
                "precision mediump float;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform sampler2D uTexture;\n" +
                "void main() {\n" +
                "  gl_FragColor = vec4(texture2D(uTexture, vTexCoord).bgr, 1.0);\n" +
                "}\n";

            shaderProgram = createProgram(vertexShader, fragmentShader);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
            updateViewport();
        }

        private void updateViewport() {
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // Upload under the swap lock so the emulation thread cannot exchange
            // the buffer out from under glTexSubImage2D (which would tear).
            EmulatorBridge.withFrontFramebuffer((fbBuffer, width, height) -> {
                scaledWidth = Math.max(1, Math.min(width, fbWidth));
                scaledHeight = Math.max(1, Math.min(height, fbHeight));
                scaleTexCoordX = (float) scaledWidth / fbWidth;
                scaleTexCoordY = (float) scaledHeight / fbHeight;

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0,
                        0, 0, scaledWidth, scaledHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, fbBuffer);
                hasFrame = true;
            });

            // The texture is allocated with undefined contents; don't draw it
            // until at least one emulated frame has been uploaded.
            if (!hasFrame) return;

            drawTexturedQuad();

            frameCount++;
            long now = System.nanoTime();
            if (now - lastFpsTime >= 1_000_000_000L) {
                currentFps = frameCount;
                frameCount = 0;
                lastFpsTime = now;

                final TextView tv = fpsTextViewRef.get();
                if (tv != null) {
                    tv.post(() -> tv.setText("FPS: " + currentFps));
                }
            }
        }

        private void drawTexturedQuad() {
            if (shaderProgram == 0) return;

            GLES20.glUseProgram(shaderProgram);

            vertexData[0] = -1f; vertexData[1] = -1f; vertexData[2] = 0f;
            vertexData[3] =  1f; vertexData[4] = -1f; vertexData[5] = 0f;
            vertexData[6] = -1f; vertexData[7] =  1f; vertexData[8] = 0f;
            vertexData[9] =  1f; vertexData[10]=  1f; vertexData[11]= 0f;

            texCoordData[0] = 0f; texCoordData[1] = scaleTexCoordY;
            texCoordData[2] = scaleTexCoordX; texCoordData[3] = scaleTexCoordY;
            texCoordData[4] = 0f; texCoordData[5] = 0f;
            texCoordData[6] = scaleTexCoordX; texCoordData[7] = 0f;

            vertexBuffer.clear();
            vertexBuffer.put(vertexData);
            vertexBuffer.flip();

            texCoordBuffer.clear();
            texCoordBuffer.put(texCoordData);
            texCoordBuffer.flip();

            int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
            int texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord");
            int textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");

            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
                    false, 0, vertexBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);

            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT,
                    false, 0, texCoordBuffer);
            GLES20.glEnableVertexAttribArray(texCoordHandle);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glUniform1i(textureHandle, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(texCoordHandle);
            GLES20.glUseProgram(0);
        }

        private int createProgram(String vertexSrc, String fragmentSrc) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
            if (vertexShader == 0 || fragmentShader == 0) return 0;

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(program);
                return 0;
            }

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);

            return program;
        }

        private int loadShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(shader);
                return 0;
            }

            return shader;
        }
    }
}
