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
package com.tansoft.ps1emulator.ui.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

public class DisclaimerActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_disclaimer);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        Button agreeButton = findViewById(R.id.btn_agree);
        Button disagreeButton = findViewById(R.id.btn_disagree);

        agreeButton.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_DISCLAIMER_ACCEPTED, true)
                    .apply();
            startActivity(new Intent(this, BiosImportActivity.class));
            finish();
        });

        disagreeButton.setOnClickListener(v -> finishAffinity());
    }

    public static boolean isAccepted(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false);
    }
}
