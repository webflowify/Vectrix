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
package com.tansoft.ps1emulator.input;

import android.content.Context;

import androidx.preference.PreferenceManager;

public class GamepadPreferences {

    public static float getOpacity(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("vpad_opacity", 70) / 100f;
    }

    public static void setOpacity(Context context, float alpha) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putInt("vpad_opacity", (int) (alpha * 100)).apply();
    }

    public static boolean isVisible(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("vpad_visible", true);
    }

    public static void setVisible(Context context, boolean visible) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean("vpad_visible", visible).apply();
    }
}
