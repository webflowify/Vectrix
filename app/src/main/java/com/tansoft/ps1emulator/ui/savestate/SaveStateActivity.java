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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.storage.SaveStateManager;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

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

        adapter = new SlotAdapter(slots);
        slotGrid.setAdapter(adapter);
    }

    private void saveToNewSlot() {
        for (SaveStateManager.SlotInfo s : slots) {
            if (!s.exists) {
                performSave(s.slot);
                return;
            }
        }
        // All slots full — overwrite oldest
        SaveStateManager.SlotInfo oldest = null;
        for (SaveStateManager.SlotInfo s : slots) {
            if (oldest == null || s.timestamp < oldest.timestamp) {
                oldest = s;
            }
        }
        if (oldest != null) performSave(oldest.slot);
    }

    private void performSave(int slot) {
        android.util.Log.d(TAG, "performSave: slot=" + slot + " gameDiscId=" + gameDiscId);
        boolean ok = SaveStateManager.saveState(this, gameDiscId, slot);
        android.util.Log.d(TAG, "performSave: saveState returned " + ok);
        if (ok) {
            android.util.Log.d(TAG, "performSave: save succeeded, refreshing slots");
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

            if (info.exists) {
                h.thumbnail.setVisibility(View.VISIBLE);
                h.emptyText.setVisibility(View.GONE);
                h.timestamp.setVisibility(View.VISIBLE);

                recycleBitmapFromView(h.thumbnail);

                Bitmap thumb = SaveStateManager.loadThumbnail(
                        SaveStateActivity.this, gameDiscId, info.slot);
                if (thumb != null) {
                    h.thumbnail.setImageBitmap(thumb);
                } else {
                    h.thumbnail.setImageBitmap(null);
                }

                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(info.timestamp));
                h.timestamp.setText(date);

                h.itemView.setOnClickListener(v -> {
                    android.util.Log.d(TAG, "Slot clicked: slot=" + info.slot + " exists=" + info.exists + " mode=" + mode);
                    if (mode.equals("load")) {
                        confirmAndLoad(info.slot);
                    } else if (mode.equals("save")) {
                        confirmAndOverwrite(info.slot);
                    } else if (mode.equals("browse")) {
                        showSlotInfo(info);
                    }
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

                recycleBitmapFromView(h.thumbnail);

                h.itemView.setOnClickListener(v -> {
                    android.util.Log.d(TAG, "Empty slot clicked: slot=" + info.slot + " mode=" + mode);
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
            recycleBitmapFromView(holder.thumbnail);
        }

        @Override
        public int getItemCount() {
            return slots.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView slotLabel, emptyText, timestamp;
            ImageView thumbnail;

            ViewHolder(View itemView) {
                super(itemView);
                slotLabel = itemView.findViewById(R.id.slotLabel);
                thumbnail = itemView.findViewById(R.id.slotThumbnail);
                emptyText = itemView.findViewById(R.id.slotEmptyText);
                timestamp = itemView.findViewById(R.id.slotTimestamp);
            }
        }
    }

    private static void recycleBitmapFromView(ImageView imageView) {
        if (imageView == null) return;
        Drawable drawable = imageView.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        imageView.setImageDrawable(null);
    }
}