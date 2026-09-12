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
package com.tansoft.ps1emulator.ui.savestate;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.ads.AdsConfig;
import com.tansoft.ps1emulator.ads.RewardedUnlockDialog;
import com.tansoft.ps1emulator.ads.RewardedUnlockManager;
import com.tansoft.ps1emulator.core.EmulatorService;
import com.tansoft.ps1emulator.storage.SaveStateManager;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SaveStateActivity extends AppCompatActivity {

    private static final String TAG = "SaveStateActivity";
    private static final String EXTRA_GAME_DISC_ID = "game_disc_id";
    private static final String EXTRA_MODE = "mode"; // "save", "load", or "browse"

    private RecyclerView slotGrid;
    private Button btnSaveNewSlot;
    private SlotAdapter adapter;
    private String gameDiscId;
    private String mode;
    private List<SaveStateManager.SlotInfo> slots;
    private int nextUnlockedEmptySlot = -1; // first empty slot that is already unlocked (-1 = none)
    private EmulatorService emulatorService;
    private boolean serviceBound = false;

    public static Intent createIntent(Context context, String gameDiscId, boolean saveMode) {
        Intent intent = new Intent(context, SaveStateActivity.class);
        intent.putExtra(EXTRA_GAME_DISC_ID, gameDiscId);
        intent.putExtra(EXTRA_MODE, saveMode ? "save" : "load");
        return intent;
    }

    public static Intent createBrowseIntent(Context context, String gameDiscId) {
        Intent intent = new Intent(context, SaveStateActivity.class);
        intent.putExtra(EXTRA_GAME_DISC_ID, gameDiscId);
        intent.putExtra(EXTRA_MODE, "browse");
        return intent;
    }

    // ── EmulatorService binding ──────────────────────────────────
    // We own pause/resume: pause in onPause() so the emulation thread
    // is guaranteed stopped before any native save/load call runs.
    // onResume() resumes only if we didn't leave it paused intentionally.

    private final android.content.ServiceConnection serviceConnection =
        new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name,
                                           android.os.IBinder binder) {
                EmulatorService.LocalBinder localBinder =
                    (EmulatorService.LocalBinder) binder;
                emulatorService = localBinder.getService();
                serviceBound = true;
                android.util.Log.d(TAG, "EmulatorService bound: running="
                    + (emulatorService != null && emulatorService.isRunning()));
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
                emulatorService = null;
                serviceBound = false;
            }
        };

    @Override
    protected void onPause() {
        super.onPause();
        // Pause emulation so sFramebuffer is stable for any native save call
        if (emulatorService != null && emulatorService.isRunning()) {
            android.util.Log.d(TAG, "onPause: pausing emulation for save-state operation");
            emulatorService.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Do NOT auto-resume here — SaveStateActivity handles its own
        // lifecycle. Emulation will be resumed by EmulationActivity when
        // it regains focus (it calls emulatorService.resume() itself).
        android.util.Log.d(TAG, "onResume: NOT resuming emulation — SaveStateActivity owns pause state");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        // Restore emulation if still running — defensive fallback
        if (emulatorService != null && emulatorService.isRunning()) {
            android.util.Log.d(TAG, "onDestroy: restoring emulation (defensive resume)");
            emulatorService.resume();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_save_state);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        gameDiscId = getIntent().getStringExtra(EXTRA_GAME_DISC_ID);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        android.util.Log.d(TAG, "onCreate: mode=" + mode + " gameDiscId=" + gameDiscId);

        if (gameDiscId == null || gameDiscId.isEmpty()) {
            Toast.makeText(this, "Cannot manage saves: missing game ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize RewardedUnlockManager for save slot gating
        RewardedUnlockManager.getInstance().init(this);

        // One-time grandfather migration: users who had save data in premium slots
        // (3–9) before monetization was introduced keep those slots unlocked.
        RewardedUnlockManager.getInstance().grandfatherExistingSaveSlots(this);

        // Bind to EmulatorService to control pause/resume around native save/load
        bindService(
            new android.content.Intent(this, EmulatorService.class),
            serviceConnection,
            android.content.Context.BIND_AUTO_CREATE
        );

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (mode.equals("browse")) {
            toolbar.setTitle(getString(R.string.manage_saves_title, gameDiscId));
        } else {
            toolbar.setTitle(mode.equals("save") ? "Save State" : "Load State");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        slotGrid = findViewById(R.id.slotGrid);
        slotGrid.setLayoutManager(new GridLayoutManager(this, 2));

        btnSaveNewSlot = findViewById(R.id.btnSaveNewSlot);
        btnSaveNewSlot.setVisibility(mode.equals("save") ? View.VISIBLE : View.GONE);
        btnSaveNewSlot.setOnClickListener(v -> saveToNewSlot());

        refreshSlots();
    }

    private void refreshSlots() {
        // One-time cleanup of orphaned .psst.png files from the old buggy native path
        SaveStateManager.cleanupOrphanedPngs(this, gameDiscId);

        slots = new ArrayList<>(SaveStateManager.listSlots(this, gameDiscId));
        android.util.Log.d(TAG, "refreshSlots: found " + slots.size() + " existing slots for gameDiscId=" + gameDiscId);
        // Ensure we show all 10 slots (including empty ones)
        for (int i = 0; i < SaveStateManager.MAX_SLOTS; i++) {
            boolean found = false;
            for (SaveStateManager.SlotInfo s : slots) {
                if (s.slot == i) { found = true; break; }
            }
            if (!found) {
                SaveStateManager.SlotInfo empty = new SaveStateManager.SlotInfo();
                empty.slot = i;
                empty.exists = false;
                slots.add(empty);
            }
        }
        // Sort by slot number
        slots.sort((a, b) -> Integer.compare(a.slot, b.slot));

        // Find the first empty slot that is already unlocked (no ad required)
        RewardedUnlockManager unlockMgr = RewardedUnlockManager.getInstance();
        nextUnlockedEmptySlot = -1;
        for (SaveStateManager.SlotInfo s : slots) {
            if (!s.exists && unlockMgr.isSaveSlotUnlocked(gameDiscId, s.slot)) {
                nextUnlockedEmptySlot = s.slot;
                break;
            }
        }

        // Update the "save to new slot" button text to reflect reality
        updateSaveNewSlotButton();

        // Log PNG state for all slots — critical for diagnosing stale-thumbnail bug
        for (SaveStateManager.SlotInfo s : slots) {
            File png = SaveStateManager.getThumbnailFile(SaveStateActivity.this, gameDiscId, s.slot);
            android.util.Log.d(TAG, "refreshSlots: slot=" + s.slot
                + " exists=" + s.exists
                + " pngExists=" + png.exists()
                + " pngLastMod=" + png.lastModified()
                + " pngLen=" + png.length());
        }

        adapter = new SlotAdapter(slots);
        slotGrid.setAdapter(adapter);
    }

    private void updateSaveNewSlotButton() {
        if (btnSaveNewSlot == null) return;
        if (!"save".equals(mode)) {
            btnSaveNewSlot.setVisibility(View.GONE);
            return;
        }
        btnSaveNewSlot.setVisibility(View.VISIBLE);
        if (nextUnlockedEmptySlot >= 0) {
            btnSaveNewSlot.setText("Save to Slot " + (nextUnlockedEmptySlot + 1));
        } else {
            // All empty slots are locked — user must unlock one via rewarded ad first
            btnSaveNewSlot.setText("All Slots Locked — Watch Ad to Unlock");
        }
    }

    private void saveToNewSlot() {
        RewardedUnlockManager unlockMgr = RewardedUnlockManager.getInstance();

        // 1. Prefer an empty slot that is already unlocked (no ad required)
        for (SaveStateManager.SlotInfo s : slots) {
            if (!s.exists && unlockMgr.isSaveSlotUnlocked(gameDiscId, s.slot)) {
                android.util.Log.d(TAG, "saveToNewSlot: using unlocked empty slot=" + s.slot);
                performSave(s.slot);
                return;
            }
        }

        // 2. Find the first locked empty slot and unlock it (user taps again to save)
        for (SaveStateManager.SlotInfo s : slots) {
            if (!s.exists) {
                android.util.Log.d(TAG, "saveToNewSlot: triggering unlock for locked empty slot=" + s.slot);
                attemptUnlockOnly(s.slot);
                return;
            }
        }

        // 3. All slots have data — overwrite oldest (slots are always unlocked if they contain data)
        SaveStateManager.SlotInfo oldest = null;
        for (SaveStateManager.SlotInfo s : slots) {
            if (oldest == null || s.timestamp < oldest.timestamp) {
                oldest = s;
            }
        }
        if (oldest != null) {
            android.util.Log.d(TAG, "saveToNewSlot: all slots full, overwriting oldest slot=" + oldest.slot);
            performSave(oldest.slot);
        }
    }

    /**
     * Attempts to unlock a locked slot via rewarded video.
     * After the ad the slot is simply unlocked and the UI refreshes.
     * No save/load/overwrite action is performed — the user must tap the
     * slot again to perform their intended action (the same behaviour as
     * slots 0–2).
     */
    private void attemptUnlockOnly(int slot) {
        RewardedUnlockManager unlockMgr = RewardedUnlockManager.getInstance();

        if (unlockMgr.isSaveSlotUnlocked(gameDiscId, slot)) {
            android.util.Log.d(TAG, "attemptUnlockOnly: slot=" + slot + " already unlocked");
            return;
        }

        if (!unlockMgr.attemptUnlock(SaveStateActivity.this, "save_slot")) {
            android.util.Log.w(TAG, "attemptUnlockOnly: slot=" + slot + " blocked (offline)");
            return;
        }

        android.util.Log.d(TAG, "attemptUnlockOnly: showing rewarded dialog for slot=" + slot);
        RewardedUnlockDialog.show(
            SaveStateActivity.this,
            "Save Slot " + (slot + 1),
            "Watch a short video to permanently unlock this save slot for this game.",
            () -> {
                android.util.Log.d(TAG, "attemptUnlockOnly: slot=" + slot + " rewarded complete, unlocking only");
                unlockMgr.unlockSaveSlot(gameDiscId, slot);
                refreshSlots(); // unlock only — no save/overwrite
            },
            null);
    }

    /**
     * @deprecated Use {@link #attemptUnlockOnly} instead. The rewarded-video
     * flow must only unlock the slot; any save/load action must come from a
     * deliberate second tap by the user.
     */
    @Deprecated
    private void attemptUnlockAndPerform(int slot, Runnable onUnlocked) {
        // Redirect to unlock-only behaviour to prevent silent overwrites.
        attemptUnlockOnly(slot);
    }

    private void performSave(int slot) {
        android.util.Log.d(TAG, "performSave: slot=" + slot + " gameDiscId=" + gameDiscId
            + " serviceBound=" + serviceBound
            + " running=" + (emulatorService != null && emulatorService.isRunning()));
        boolean ok = SaveStateManager.saveState(this, gameDiscId, slot);
        android.util.Log.d(TAG, "performSave: saveState returned " + ok + " for slot=" + slot);
        if (ok) {
            android.util.Log.d(TAG, "performSave: save succeeded — checking PNG thumbnail");

            // Verify PNG was actually written by native code
            File pngFile = SaveStateManager.getThumbnailFile(this, gameDiscId, slot);
            android.util.Log.d(TAG, "performSave: PNG exists=" + pngFile.exists()
                + " lastModified=" + pngFile.lastModified()
                + " length=" + pngFile.length());

            refreshSlots();
        } else {
            android.util.Log.w(TAG, "performSave: save FAILED for slot=" + slot);
            Toast.makeText(this, "Failed to save state", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndLoad(int slot) {
        android.util.Log.d(TAG, "confirmAndLoad: slot=" + slot + " gameDiscId=" + gameDiscId);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_load, null);
        
        TextView slotNumber = dialogView.findViewById(R.id.slotNumber);
        TextView slotTimestamp = dialogView.findViewById(R.id.slotTimestamp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        slotNumber.setText("Slot " + (slot + 1));
        slotTimestamp.setText(getSlotTimestamp(slot));
        
        btnConfirm.setText("Load");
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        btnConfirm.setOnClickListener(v -> {
            android.util.Log.d(TAG, "confirmAndLoad: user confirmed load for slot=" + slot);
            dialog.dismiss();
            boolean ok = SaveStateManager.loadState(this, gameDiscId, slot);
            android.util.Log.d(TAG, "confirmAndLoad: loadState returned " + ok);
            if (ok) {
                android.util.Log.d(TAG, "confirmAndLoad: finishing activity");
                finish();
            } else {
                android.util.Log.w(TAG, "confirmAndLoad: loadState FAILED for slot=" + slot);
                Toast.makeText(this, "Failed to load state", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnCancel.setOnClickListener(v -> {
            android.util.Log.d(TAG, "confirmAndLoad: user cancelled load for slot=" + slot);
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void confirmAndOverwrite(int slot) {
        android.util.Log.d(TAG, "confirmAndOverwrite: slot=" + slot);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_overwrite, null);
        
        TextView slotNumber = dialogView.findViewById(R.id.slotNumber);
        TextView slotTimestamp = dialogView.findViewById(R.id.slotTimestamp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        slotNumber.setText("Slot " + (slot + 1));
        slotTimestamp.setText(getSlotTimestamp(slot));
        
        btnConfirm.setText("Overwrite");
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        btnConfirm.setOnClickListener(v -> {
            android.util.Log.d(TAG, "confirmAndOverwrite: user confirmed overwrite for slot=" + slot);
            dialog.dismiss();
            performSave(slot);
        });
        
        btnCancel.setOnClickListener(v -> {
            android.util.Log.d(TAG, "confirmAndOverwrite: user cancelled overwrite for slot=" + slot);
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void confirmAndDelete(int slot) {
        android.util.Log.d(TAG, "confirmAndDelete: slot=" + slot);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);
        
        TextView slotNumber = dialogView.findViewById(R.id.slotNumber);
        TextView slotTimestamp = dialogView.findViewById(R.id.slotTimestamp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        slotNumber.setText("Slot " + (slot + 1));
        slotTimestamp.setText(getSlotTimestamp(slot));
        
        btnConfirm.setText("Delete");
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        btnConfirm.setOnClickListener(v -> {
            SaveStateManager.deleteState(this, gameDiscId, slot);
            refreshSlots();
            dialog.dismiss();
        });
        
        btnCancel.setOnClickListener(v -> {
            android.util.Log.d(TAG, "confirmAndDelete: user cancelled delete for slot=" + slot);
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void showSlotInfo(SaveStateManager.SlotInfo info) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_load, null);

        TextView slotNumber = dialogView.findViewById(R.id.slotNumber);
        TextView slotTimestamp = dialogView.findViewById(R.id.slotTimestamp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        slotNumber.setText("Slot " + (info.slot + 1));
        slotTimestamp.setText(getSlotTimestamp(info.slot));

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        if (dialogTitle != null) {
            dialogTitle.setText("Save State Info");
        }

        if (info.exists) {
            btnConfirm.setText("Load in Game");
            btnConfirm.setOnClickListener(v2 -> {
                Toast.makeText(this,
                    R.string.manage_saves_load_hint,
                    Toast.LENGTH_LONG).show();
            });
        } else {
            btnConfirm.setText("OK");
            btnConfirm.setOnClickListener(v2 -> {});
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();

        btnCancel.setOnClickListener(v2 -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Perform the appropriate action for a slot based on current mode.
     * Called after a slot is unlocked (or if already free).
     */
    private void performSlotAction(int slot) {
        if (mode.equals("load")) {
            confirmAndLoad(slot);
        } else if (mode.equals("save")) {
            // Check if slot already has data
            for (SaveStateManager.SlotInfo s : slots) {
                if (s.slot == slot && s.exists) {
                    confirmAndOverwrite(slot);
                    return;
                }
            }
            // Empty slot — save directly
            performSave(slot);
        } else if (mode.equals("browse")) {
            // For browse mode, show slot info
            for (SaveStateManager.SlotInfo s : slots) {
                if (s.slot == slot) {
                    showSlotInfo(s);
                    return;
                }
            }
        }
    }

    private String getSlotTimestamp(int slot) {
        for (SaveStateManager.SlotInfo s : slots) {
            if (s.slot == slot && s.exists && s.timestamp > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault());
                return sdf.format(new java.util.Date(s.timestamp));
            }
        }
        return "Empty";
    }

    // ── Adapter ───────────────────────────────────────────────────

    private class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.ViewHolder> {

        private final List<SaveStateManager.SlotInfo> slots;

        SlotAdapter(List<SaveStateManager.SlotInfo> slots) {
            this.slots = slots;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_save_slot, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            SaveStateManager.SlotInfo info = slots.get(position);
            h.slotLabel.setText("Slot " + (info.slot + 1));

            // Check if slot is locked (gated behind rewarded ad)
            boolean isSlotLocked = !RewardedUnlockManager.getInstance()
                    .isSaveSlotUnlocked(gameDiscId, info.slot);

            // Show/hide lock icon and adjust alpha
            if (isSlotLocked) {
                h.lockIcon.setVisibility(View.VISIBLE);
                h.thumbnail.setAlpha(0.5f);
                h.emptyText.setAlpha(0.5f);
            } else {
                h.lockIcon.setVisibility(View.GONE);
                h.thumbnail.setAlpha(1.0f);
                h.emptyText.setAlpha(1.0f);
            }

            if (info.exists) {
                h.thumbnail.setVisibility(View.VISIBLE);
                h.emptyText.setVisibility(View.GONE);
                h.timestamp.setVisibility(View.VISIBLE);

                String thumbnailPath = SaveStateManager.getThumbnailPath(
                        SaveStateActivity.this, gameDiscId, info.slot);

                if (thumbnailPath != null) {
                    File thumbFile = SaveStateManager.getThumbnailFile(
                            SaveStateActivity.this, gameDiscId, info.slot);
                    long lastModified = thumbFile.lastModified();
                    long fileLength = thumbFile.length();

                    android.util.Log.d(TAG, "Adapter bind slot=" + info.slot
                        + " thumbnailPath=" + thumbnailPath
                        + " exists=" + thumbFile.exists()
                        + " lastModified=" + lastModified
                        + " length=" + fileLength);

                    // Clear the ImageView's Glide request (drops any in-flight or
                    // memory-cached request keyed to the previous ObjectKey) before
                    // starting the new load so we never flash the old thumbnail.
                    Glide.with(SaveStateActivity.this).clear(h.thumbnail);

                    Glide.with(SaveStateActivity.this)
                            .load(thumbnailPath)
                            .signature(new ObjectKey(lastModified))
                            .apply(new RequestOptions()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .override(200, 150)
                                    .error(R.drawable.game_card_gradient_overlay))
                            .into(h.thumbnail);
                } else {
                    android.util.Log.d(TAG, "Adapter bind slot=" + info.slot
                        + " NO thumbnail path — falling back to gradient overlay");
                    h.thumbnail.setImageResource(R.drawable.game_card_gradient_overlay);
                }

                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(info.timestamp));
                h.timestamp.setText(date);

                h.itemView.setOnClickListener(v -> {
                    android.util.Log.d(TAG, "Slot clicked: slot=" + info.slot + " exists=" + info.exists + " mode=" + mode);

                    if (!RewardedUnlockManager.getInstance()
                            .isSaveSlotUnlocked(gameDiscId, info.slot)) {
                        attemptUnlockOnly(info.slot);
                        return;
                    }

                    performSlotAction(info.slot);
                });
                h.itemView.setOnLongClickListener(v -> {
                    if (!mode.equals("browse")) {
                        confirmAndDelete(info.slot);
                    }
                    return true;
                });
            } else {
                h.thumbnail.setVisibility(View.GONE);
                h.emptyText.setVisibility(View.VISIBLE);
                h.timestamp.setVisibility(View.GONE);

                // Clear any existing image with Glide
                Glide.with(SaveStateActivity.this).clear(h.thumbnail);

                h.itemView.setOnClickListener(v -> {
                    android.util.Log.d(TAG, "Empty slot clicked: slot=" + info.slot + " mode=" + mode);

                    if (!RewardedUnlockManager.getInstance()
                            .isSaveSlotUnlocked(gameDiscId, info.slot)) {
                        attemptUnlockOnly(info.slot);
                        return;
                    }

                    if (mode.equals("save")) {
                        performSave(info.slot);
                    }
                });
                h.itemView.setOnLongClickListener(null);
            }
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            super.onViewRecycled(holder);
            // Glide handles bitmap recycling automatically
            Glide.with(holder.itemView.getContext()).clear(holder.thumbnail);
        }

        @Override
        public int getItemCount() {
            return slots.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView slotLabel, emptyText, timestamp;
            ImageView thumbnail;
            ImageView lockIcon;

            ViewHolder(View itemView) {
                super(itemView);
                slotLabel = itemView.findViewById(R.id.slotLabel);
                thumbnail = itemView.findViewById(R.id.slotThumbnail);
                emptyText = itemView.findViewById(R.id.slotEmptyText);
                timestamp = itemView.findViewById(R.id.slotTimestamp);
                lockIcon = itemView.findViewById(R.id.lockIcon);
            }
        }
    }
}