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

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.storage.BiosManager;
import com.tansoft.ps1emulator.ui.library.LibraryActivity;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

public class BiosImportActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_BIOS_PICKER = 1001;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_bios_import);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        statusText = findViewById(R.id.bios_status_text);
        Button selectButton = findViewById(R.id.btn_select_bios);
        Button skipButton = findViewById(R.id.btn_skip_bios);

        if (BiosManager.isBiosImported(this)) {
            navigateToLibrary();
            return;
        }

        selectButton.setOnClickListener(v -> {
            Intent picker = BiosManager.createBiosPickerIntent();
            startActivityForResult(picker, REQUEST_CODE_BIOS_PICKER);
        });

        skipButton.setOnClickListener(v -> navigateToLibrary());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_BIOS_PICKER && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                validateAndPersist(uri);
            }
        }
    }

    private void validateAndPersist(Uri uri) {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(R.string.bios_setup_validating);

        new Thread(() -> {
            final boolean isValid = BiosManager.validateBios(this, uri);

            runOnUiThread(() -> {
                if (isValid) {
                    BiosManager.persistBiosUri(this, uri);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.bios_setup_valid_title)
                            .setMessage(R.string.bios_setup_valid_message)
                            .setPositiveButton(R.string.bios_setup_continue, (dialog, which) -> navigateToLibrary())
                            .setCancelable(false)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.bios_setup_invalid_title)
                            .setMessage(R.string.bios_setup_invalid_message)
                            .setPositiveButton(R.string.bios_setup_try_again, null)
                            .show();
                    statusText.setText(R.string.bios_setup_invalid_status);
                }
            });
        }).start();
    }

    private void navigateToLibrary() {
        Intent intent = new Intent(this, LibraryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
