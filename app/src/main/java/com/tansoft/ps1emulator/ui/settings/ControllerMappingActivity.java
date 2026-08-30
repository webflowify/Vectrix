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
package com.tansoft.ps1emulator.ui.settings;

import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import java.util.ArrayList;
import java.util.List;

public class ControllerMappingActivity extends AppCompatActivity {

    private static final String PREFS_KEY_PREFIX = "ctrl_map_";

    private RecyclerView controllerList;
    private TextView noControllersText;
    private ControllerAdapter adapter;
    private List<ControllerInfo> controllers;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_controller_mapping);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        controllerList = findViewById(R.id.controllerList);
        noControllersText = findViewById(R.id.noControllersText);
        controllerList.setLayoutManager(new LinearLayoutManager(this));

        refreshControllers();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void refreshControllers() {
        controllers = new ArrayList<>();
        int[] deviceIds = ((InputManager) getSystemService(INPUT_SERVICE)).getInputDeviceIds();
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && isController(device)) {
                controllers.add(new ControllerInfo(device));
            }
        }

        if (controllers.isEmpty()) {
            noControllersText.setVisibility(View.VISIBLE);
            controllerList.setVisibility(View.GONE);
        } else {
            noControllersText.setVisibility(View.GONE);
            controllerList.setVisibility(View.VISIBLE);
            adapter = new ControllerAdapter(controllers);
            controllerList.setAdapter(adapter);
        }
    }

    private boolean isController(InputDevice device) {
        return (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    // -- Data model -----------------------------------------------------------

    private static class ControllerInfo {
        final InputDevice device;
        final List<Ps1ButtonEntry> buttons;

        ControllerInfo(InputDevice device) {
            this.device = device;
            this.buttons = new ArrayList<>();
            for (Ps1Button btn : Ps1Button.values()) {
                buttons.add(new Ps1ButtonEntry(btn));
            }
        }
    }

    enum Ps1Button {
        CROSS("Cross"),
        CIRCLE("Circle"),
        SQUARE("Square"),
        TRIANGLE("Triangle"),
        DPAD_UP("D-Pad Up"),
        DPAD_DOWN("D-Pad Down"),
        DPAD_LEFT("D-Pad Left"),
        DPAD_RIGHT("D-Pad Right"),
        L1("L1"),
        R1("R1"),
        L2("L2"),
        R2("R2"),
        START("Start"),
        SELECT("Select"),
        L3("L3 (Left Stick Click)"),
        R3("R3 (Right Stick Click)");

        final String displayName;

        Ps1Button(String displayName) {
            this.displayName = displayName;
        }
    }

    private static class Ps1ButtonEntry {
        final Ps1Button ps1Button;
        int mappedKeyCode;
        boolean hasMapping;

        Ps1ButtonEntry(Ps1Button ps1Button) {
            this.ps1Button = ps1Button;
            this.mappedKeyCode = -1;
            this.hasMapping = false;
        }
    }

    // -- Adapter --------------------------------------------------------------

    private class ControllerAdapter
            extends RecyclerView.Adapter<ControllerAdapter.ViewHolder> {

        private final List<ControllerInfo> controllers;

        ControllerAdapter(List<ControllerInfo> controllers) {
            this.controllers = controllers;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_controller_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            ControllerInfo info = controllers.get(position);
            h.name.setText(info.device.getName());
            h.deviceId.setText("ID: " + info.device.getId());

            // Load persisted mappings
            for (Ps1ButtonEntry entry : info.buttons) {
                String key = PREFS_KEY_PREFIX + info.device.getId()
                        + "_" + entry.ps1Button.name();
                int saved = prefs.getInt(key, -1);
                if (saved != -1) {
                    entry.mappedKeyCode = saved;
                    entry.hasMapping = true;
                }
            }

            h.statusText.setText(info.device.getId()
                    + " buttons configured. Tap to remap.");

            // When the card is tapped, show the button list dialog
            h.itemView.setOnClickListener(v -> showRemapDialog(info));
        }

        @Override
        public int getItemCount() {
            return controllers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, deviceId, statusText;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.controllerName);
                deviceId = itemView.findViewById(R.id.controllerId);
                statusText = itemView.findViewById(R.id.statusText);
            }
        }
    }

    // -- Remap dialog ---------------------------------------------------------

    private void showRemapDialog(ControllerInfo info) {
        String[] ps1Names = new String[Ps1Button.values().length];
        for (int i = 0; i < Ps1Button.values().length; i++) {
            Ps1Button btn = Ps1Button.values()[i];
            int saved = prefs.getInt(
                    PREFS_KEY_PREFIX + info.device.getId() + "_" + btn.name(), -1);
            String keyName = saved != -1 ? KeyEvent.keyCodeToString(saved) : "Not mapped";
            ps1Names[i] = btn.displayName + "  ->  " + keyName;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remap -- " + info.device.getName())
                .setItems(ps1Names, (dialog, which) -> {
                    Ps1Button selected = Ps1Button.values()[which];
                    showWaitForInputDialog(info, selected);
                })
                .setNegativeButton("Done", null)
                .show();
    }

    private void showWaitForInputDialog(ControllerInfo info, Ps1Button ps1Button) {
        AlertDialog waitDialog = new AlertDialog.Builder(this)
                .setTitle("Press a button on " + info.device.getName())
                .setMessage("Press the physical button you want to assign to "
                        + ps1Button.displayName)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();

        waitDialog.setCanceledOnTouchOutside(false);

        // Set a key listener on the dialog to capture the next key press
        waitDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // Save the mapping
                String key = PREFS_KEY_PREFIX + info.device.getId()
                        + "_" + ps1Button.name();
                prefs.edit().putInt(key, keyCode).apply();
                dialog.dismiss();
                // Refresh to show updated mappings
                refreshControllers();
                return true;
            }
            return false;
        });
    }

    // -- KeyEvent helper for display ------------------------------------------

    private static String keyCodeToName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "Left Stick";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "Right Stick";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-Pad Up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-Pad Down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-Pad Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-Pad Right";
            default: return "Key " + keyCode;
        }
    }
}
