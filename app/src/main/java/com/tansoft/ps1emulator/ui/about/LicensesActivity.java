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
package com.tansoft.ps1emulator.ui.about;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LicensesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_licenses);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.licenses_title);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        RecyclerView list = findViewById(R.id.licenses_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new LicenseAdapter(buildLicenseItems()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private List<LicenseItem> buildLicenseItems() {
        List<LicenseItem> items = new ArrayList<>();

        // ── App header ──────────────────────────────────────────────────
        items.add(LicenseItem.header(
                getString(R.string.licenses_app_header_title),
                readRawResource(R.raw.third_party_licenses)));

        // ── Emulation core (always visible, most important) ─────────────
        items.add(LicenseItem.library("PCSX ReARMed", "git submodule", "GPLv2",
                "Emulation core. The Ari64 ARM dynarec is GPLv2 only.\n"
                + "Copyright (C) 1999-2003 PCSX Team; 2005-2009 PCSX-df Team;\n"
                + "2009-2011 PCSX-Reloaded Team; 2011-2012 Exophase, notaz;\n"
                + "2010 PCSX4ALL Team; Pete Bernert / P.E.Op.S.; 2009-2011 Ari64."));

        // ── Native dependencies ─────────────────────────────────────────
        items.add(LicenseItem.library("libchdr", "bundled", "BSD-3-Clause",
                "CHD disc image support. Copyright Romain Tisserand (wrapper)\n"
                + "and Aaron Giles (original MAME CHD code)."));

        items.add(LicenseItem.library("LZMA SDK", "24.05", "Public Domain",
                "Igor Pavlov / 7-Zip. Used by libchdr for CHD compression."));

        items.add(LicenseItem.library("Zstandard (zstd)", "1.5.6", "BSD-2-Clause",
                "Copyright (c) Meta Platforms, Inc. and affiliates.\n"
                + "Used by libchdr for CHD compression."));

        items.add(LicenseItem.library("zlib", "1.3.1", "zlib License",
                "Copyright (C) 1995-2024 Jean-loup Gailly and Mark Adler."));

        items.add(LicenseItem.library("libretro-common", "bundled", "MIT",
                "Copyright (c) libretro contributors. VFS and utility code."));

        items.add(LicenseItem.library("stb_image_write", "v1.16", "MIT / Public Domain",
                "Written by Sean T. Barrett. Dual-licensed MIT or Public Domain.\n"
                + "Used for screenshots."));

        items.add(LicenseItem.library("Oboe", "1.10.0", "Apache-2.0",
                "Native audio output. Copyright The Oboe Authors (Google LLC).\n"
                + "No NOTICE file in source repository."));

        // ── Java / Android dependencies ─────────────────────────────────
        items.add(LicenseItem.library("AndroidX (AppCompat, Core, Activity,\n"
                + "    Fragment, ConstraintLayout, RecyclerView,\n"
                + "    ViewPager2, Preference, Lifecycle, DocumentFile)",
                "see version catalog", "Apache-2.0",
                "Copyright The Android Open Source Project.\n"
                + "No NOTICE file in source repository."));

        items.add(LicenseItem.library("Google Material Components", "1.14.0", "Apache-2.0",
                "Copyright The Android Open Source Project.\n"
                + "No NOTICE file in source repository."));

        items.add(LicenseItem.library("Room", "2.6.1", "Apache-2.0",
                "Copyright The Android Open Source Project.\n"
                + "No NOTICE file in source repository."));

        items.add(LicenseItem.library("Glide", "4.16.0", "Apache-2.0",
                "Image loading. Main library: Apache-2.0.\n"
                + "gifdecoder sub-component: BSD-2-Clause (Copyright 2014 Google).\n"
                + "No NOTICE file in source repository."));

        items.add(LicenseItem.library("Apache Commons Compress", "1.26.2", "Apache-2.0",
                "Archive extraction (.7z support).\n"
                + "NOTICE: Apache Commons Compress\n"
                + "Copyright 2002-2026 The Apache Software Foundation\n"
                + "This product includes software developed at\n"
                + "The Apache Software Foundation (https://www.apache.org/)."));

        items.add(LicenseItem.library("XZ for Java", "1.9", "Public Domain",
                "Lasse Collin, Igor Pavlov. org.tukaani:xz."));

        // ── Bundled, not compiled ───────────────────────────────────────
        items.add(LicenseItem.library("lightrec", "bundled (not compiled)", "LGPL-2.0",
                "GNU Library GPL v2. Present in source tree but not compiled\n"
                + "into shipped binaries."));

        items.add(LicenseItem.library("GNU Lightning", "bundled (not compiled)", "GPL-3.0 / LGPL-3.0",
                "Dependency of lightrec. Not compiled into shipped binaries."));

        items.add(LicenseItem.library("TLSF allocator", "bundled (not compiled)", "BSD-2-Clause",
                "Copyright (c) 2006-2016 Nick Johnson. Bundled with lightrec.\n"
                + "Not compiled into shipped binaries."));

        // ── GPLv2 §3 source availability (always visible, not collapsible) ─
        items.add(LicenseItem.section("Source Code Availability (GPLv2 §3)",
                "This program is distributed in object code only. The complete\n"
                + "corresponding source code is available from the public repository:\n"
                + "\n"
                + "  https://github.com/webflowify/Vectrix\n"
                + "\n"
                + "It includes all application source, the JNI bridge, native glue\n"
                + "code, build scripts, and the PCSX ReARMed core (git submodule)\n"
                + "needed to build the exact binary. Source is provided under the\n"
                + "GNU GPLv2. Each released binary is tagged in the repository.\n"
                + "\n"
                + "The full GPLv2 text is bundled at assets/licenses/gpl-2.0.txt."));

        // ── Trademark notice (always visible, not collapsible) ──────────
        items.add(LicenseItem.section("Trademark Notice",
                "This application is not affiliated with, endorsed by, or\n"
                + "sponsored by Sony Interactive Entertainment or any other\n"
                + "rights holder. \"PlayStation\", \"PSX\", \"PS1\", \"Sony\" and\n"
                + "related logos are trademarks of their respective owners.\n"
                + "The application does not bundle or link to any BIOS, ROM, or\n"
                + "game files; users must supply their own from hardware they\n"
                + "legally own."));

        return items;
    }

    private String readRawResource(int resId) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            return getString(R.string.licenses_load_error);
        }
        return sb.toString();
    }
}
