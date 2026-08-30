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

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.core.EmulatorBridge;
import com.tansoft.ps1emulator.storage.BiosManager;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int REQUEST_CODE_BIOS_PICKER = 1001;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference importBiosPref = findPreference("import_bios");
        if (importBiosPref != null) {
            updateBiosSummary(importBiosPref);
            importBiosPref.setOnPreferenceClickListener(preference -> {
                Intent picker = BiosManager.createBiosPickerIntent();
                startActivityForResult(picker, REQUEST_CODE_BIOS_PICKER);
                return true;
            });
        }

        Preference privacyPref = findPreference("privacy_policy");
        if (privacyPref != null) {
            privacyPref.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://webflowify.github.io/psx-emulator-privacy/"));
                startActivity(browserIntent);
                return true;
            });
        }

        Preference licensesPref = findPreference("open_source_licenses");
        if (licensesPref != null) {
            licensesPref.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(),
                        com.tansoft.ps1emulator.ui.about.LicensesActivity.class));
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (key) {
            case "audio_enabled": {
                boolean enabled = prefs.getBoolean(key, true);
                EmulatorBridge.nativeSetAudioEnabled(enabled);
                break;
            }
            case "audio_buffer_size": {
                int size = Integer.parseInt(prefs.getString(key, "256"));
                EmulatorBridge.nativeSetAudioBufferSize(size);
                break;
            }
            case "fast_forward_speed": {
                int speed = Integer.parseInt(prefs.getString(key, "2"));
                EmulatorBridge.nativeSetFastForwardSpeed(speed);
                break;
            }
            case "rewind_depth": {
                // Rewind depth is read via SettingsHelper in EmulationActivity
                // No direct native call needed here — interval is set when
                // emulation starts or when the setting changes during gameplay.
                break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_BIOS_PICKER && resultCode == android.app.Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                validateAndPersistBios(uri);
            }
        }
    }

    private void validateAndPersistBios(Uri uri) {
        new Thread(() -> {
            final boolean isValid = BiosManager.validateBios(requireContext(), uri);

            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (isValid) {
                    BiosManager.persistBiosUri(requireContext(), uri);
                    Preference importBiosPref = findPreference("import_bios");
                    if (importBiosPref != null) {
                        updateBiosSummary(importBiosPref);
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.bios_setup_valid_title)
                            .setMessage(R.string.bios_settings_import_success)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.bios_setup_invalid_title)
                            .setMessage(R.string.bios_settings_import_invalid)
                            .setPositiveButton(R.string.bios_setup_try_again, null)
                            .show();
                }
            });
        }).start();
    }

    private void updateBiosSummary(Preference preference) {
        if (BiosManager.isBiosImported(requireContext())) {
            preference.setSummary(R.string.bios_settings_imported);
        } else {
            preference.setSummary(R.string.bios_settings_not_imported);
        }
    }
}
