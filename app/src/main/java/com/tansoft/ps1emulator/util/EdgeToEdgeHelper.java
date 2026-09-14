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
package com.tansoft.ps1emulator.util;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.WindowManager;

public final class EdgeToEdgeHelper {

    private static final String TAG = "EdgeToEdgeHelper";

    private EdgeToEdgeHelper() {}

    public static void enableEdgeToEdge(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.getWindow().setDecorFitsSystemWindows(false);
            } else {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        } catch (Exception e) {
            // Some OEM ROMs (notably Spreadtrum/Unisoc devices like Itel) throw
            // or silently fail when setDecorFitsSystemWindows is called. Fall back
            // to legacy flags so the app still renders — edge-to-edge is cosmetic,
            // not a functional requirement.
            Log.w(TAG, "enableEdgeToEdge: modern API failed, using legacy flags", e);
            try {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } catch (Exception ignored) {
                // Last resort — the app will still work, just without edge-to-edge.
            }
        }
    }
}
