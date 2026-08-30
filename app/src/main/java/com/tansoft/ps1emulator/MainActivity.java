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
package com.tansoft.ps1emulator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import com.tansoft.ps1emulator.ui.library.LibraryActivity;
import com.tansoft.ps1emulator.ui.onboarding.DisclaimerActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!isDisclaimerAccepted(prefs)) {
            startActivity(new Intent(this, DisclaimerActivity.class));
            finish();
            return;
        }

        startActivity(new Intent(this, LibraryActivity.class));
        finish();
    }

    private boolean isDisclaimerAccepted(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false);
    }
}
