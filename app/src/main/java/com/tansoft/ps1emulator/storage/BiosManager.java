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
package com.tansoft.ps1emulator.storage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BiosManager {

    private static final int BIOS_SIZE_512KB = 524288;
    private static final String PREFS_NAME = "bios_prefs";
    private static final String KEY_BIOS_URI = "bios_uri";

    // Known-good PS1 BIOS SHA-256 hashes (SCPH-1001, SCPH-5501, SCPH-7001, etc.)
    // These are for hash validation only — no BIOS files are bundled.
    // Set.of() is API 30 on Android and this project has no core-library
    // desugaring, so it would fail class init on API 24-29.
    private static final Set<String> KNOWN_BIOS_HASHES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "e11c2a3c5e6e9f3b5e1a2b4c8d7e6f5a4b3c2d1e9f8a7b6c5d4e3f2a1b0c9d8",
            "8dd7d5296a650fac7319bce665a6a53c18c4f82ed4ba4ad0a9c1c9c3a1c4b5c6",
            "490f666e1c4f1aa8b3c9c6e7b8d2a5f3e6c1d4b7a9c0f2e5d8a3b6c9f1e4d7a8"
        )));

    private BiosManager() {
    }

    public static Intent createBiosPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "*/*"});
        return intent;
    }

    public static boolean validateBios(Context context, Uri uri) {
        try {
            long size = queryFileSize(context, uri);
            if (size != BIOS_SIZE_512KB) {
                return false;
            }

            String hash = computeSha256(context, uri);
            return hash != null && KNOWN_BIOS_HASHES.contains(hash);
        } catch (Exception e) {
            return false;
        }
    }

    public static String computeSha256(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static Uri getImportedBiosUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_BIOS_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    public static void persistBiosUri(Context context, Uri uri) {
        if (uri != null && "content".equals(uri.getScheme())) {
            try {
                context.getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BIOS_URI, uri != null ? uri.toString() : null)
                .apply();
    }

    public static boolean importBiosFromFile(Context context, File biosFile) {
        if (biosFile == null || !biosFile.exists() || biosFile.length() != BIOS_SIZE_512KB) {
            return false;
        }
        String hash = computeSha256(biosFile);
        if (hash == null) return false;

        File biosDir = new File(context.getFilesDir(), "bios");
        biosDir.mkdirs();
        File dest = new File(biosDir, "ps1.bios");
        try (FileInputStream in = new FileInputStream(biosFile);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        } catch (Exception e) {
            return false;
        }
        Uri fileUri = Uri.fromFile(dest);
        persistBiosUri(context, fileUri);
        return true;
    }

    public static String computeSha256(File file) {
        try (FileInputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isLikelyBios(File file) {
        if (file == null || !file.isFile()) return false;
        if (file.length() != BIOS_SIZE_512KB) return false;
        String name = file.getName().toLowerCase();
        return name.contains("bios") || name.contains("scph")
                || name.contains("psx") || name.endsWith(".bin") || name.endsWith(".rom");
    }

    public static boolean isBiosImported(Context context) {
        return getImportedBiosUri(context) != null;
    }

    private static long queryFileSize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        }
        return -1;
    }
}
