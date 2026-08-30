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
package com.tansoft.ps1emulator.ui.library;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import java.util.ArrayList;
import java.util.List;

public class MoreAppsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_more_apps);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar_more_apps);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler_more_apps_list);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        MoreAppsAdapter adapter = new MoreAppsAdapter(new MoreAppsAdapter.OnAppClickListener() {
            @Override
            public void onInstallClick(MoreAppsAdapter.AppInfo app) {
                openPlayStore(app.playStoreUrl);
            }

            @Override
            public void onCardClick(MoreAppsAdapter.AppInfo app) {
                openPlayStore(app.playStoreUrl);
            }
        }, MoreAppsAdapter.VIEW_TYPE_LIST);

        recycler.setAdapter(adapter);

        List<MoreAppsAdapter.AppInfo> apps = new ArrayList<>();
        apps.add(new MoreAppsAdapter.AppInfo(
                getString(R.string.more_apps_gba_name),
                getString(R.string.more_apps_gba_description),
                "https://play.google.com/store/apps/details?id=com.tansoft.gbaemulator",
                R.drawable.ic_gba_emulator,
                "logo-gba-icon.png"
        ));

        adapter.setApps(apps);
    }

    private void openPlayStore(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open Play Store", Toast.LENGTH_SHORT).show();
        }
    }
}
