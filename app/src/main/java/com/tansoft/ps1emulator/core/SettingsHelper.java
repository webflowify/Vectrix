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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class SettingsHelper {

    private final SharedPreferences prefs;

    public SettingsHelper(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isAutoRotateEnabled() {
        return prefs.getBoolean("auto_rotate", true);
    }

    public boolean isFpsVisible() {
        return prefs.getBoolean("show_fps", false);
    }

    public boolean isAudioEnabled() {
        return prefs.getBoolean("audio_enabled", true);
    }

    public int getAudioBufferSize() {
        try {
            return Integer.parseInt(prefs.getString("audio_buffer_size", "512"));
        } catch (NumberFormatException e) {
            return 512;
        }
    }

    public boolean isVpadVisible() {
        return prefs.getBoolean("vpad_visible", true);
    }

    public boolean isStretchFullscreenEnabled() {
        return prefs.getBoolean("stretch_fullscreen", false);
    }

    public int getVpadOpacity() {
        return prefs.getInt("vpad_opacity", 70);
    }

    public int getFastForwardSpeed() {
        try {
            return Integer.parseInt(prefs.getString("fast_forward_speed", "2"));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public int getRewindDepth() {
        try {
            return Integer.parseInt(prefs.getString("rewind_depth", "10"));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public int getSlowMotionSpeed() {
        try {
            return Integer.parseInt(prefs.getString("slow_motion_speed", "2"));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public int getControllerMapping(int deviceId, String ps1ButtonName) {
        return prefs.getInt("ctrl_map_" + deviceId + "_" + ps1ButtonName, -1);
    }

    public String reverseControllerMapping(int deviceId, int keyCode) {
        String[] names = {"CROSS", "CIRCLE", "SQUARE", "TRIANGLE",
                "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
                "L1", "R1", "L2", "R2",
                "START", "SELECT", "L3", "R3"};
        for (String name : names) {
            if (prefs.getInt("ctrl_map_" + deviceId + "_" + name, -1) == keyCode) {
                return name;
            }
        }
        return null;
    }
}
