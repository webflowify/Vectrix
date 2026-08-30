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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MemoryCardManager {

    private static final int CARD_SIZE = 128 * 1024; // 128 KB
    private static final String CARD_DIR = "memcards";

    // The core manages the actual card files itself (CreateMcd/LoadMcd/SaveMcd)
    // when the memcard option is "shared". It writes them as pcsx-card1.mcd /
    // pcsx-card2.mcd in the save directory — so we must reference the SAME paths
    // for import/export to operate on the real cards.
    private static final String CORE_CARD_PREFIX = "pcsx-card";
    private static final String CARD_SUFFIX = ".mcd";

    private MemoryCardManager() {
        // Utility class
    }

    // ── Path helpers ──────────────────────────────────────────────

    private static File getCardDir(Context context) {
        File dir = new File(context.getFilesDir(), CARD_DIR);
        dir.mkdirs();
        return dir;
    }

    public static File getCardFile(Context context, String gameDiscId, int slot) {
        // slot: 0 = Card 1, 1 = Card 2. The filename matches the core's "shared"
        // memcard layout (pcsx-card1.mcd / pcsx-card2.mcd) so import/export act on
        // the exact file the core reads/writes. gameDiscId is retained for API
        // compatibility but is unused — the shared card is global across games.
        return new File(getCardDir(context), CORE_CARD_PREFIX + (slot + 1) + CARD_SUFFIX);
    }

    // ── Initialization ────────────────────────────────────────────

    /**
     * Ensures the memory-card directory exists. The actual card file is created
     * and formatted by the PCSX-ReARMed core itself (CreateMcd) the first time
     * the emulation loads it, so we must NOT pre-create a blank file here — doing
     * so would hand the core an empty/garbage file that it loads instead of
     * formatting a fresh, correctly-structured card (which is what avoids the
     * "delete 1 block / insert another memory card" full-card bug).
     */
    public static void initializeCard(Context context, String gameDiscId, int slot) {
        getCardDir(context);
    }

    // ── Import / Export ───────────────────────────────────────────

    /**
     * Import a memory card file from a content URI (e.g., SAF file picker).
     */
    public static boolean importCard(Context context, Uri uri, String gameDiscId) {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;

            // Determine slot based on URI or use default (0)
            int slot = 0;
            File dest = getCardFile(context, gameDiscId, slot);

            // Read the entire stream
            byte[] data = new byte[CARD_SIZE];
            int totalRead = 0;
            while (totalRead < CARD_SIZE) {
                int read = in.read(data, totalRead, CARD_SIZE - totalRead);
                if (read < 0) break;
                totalRead += read;
            }

            if (totalRead != CARD_SIZE) return false; // Wrong size

            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(data);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Export a memory card file to a content URI (SAF file picker).
     */
    public static boolean exportCard(Context context, String gameDiscId, Uri exportUri) {
        File src = getCardFile(context, gameDiscId, 0);
        if (!src.exists()) return false;

        ContentResolver resolver = context.getContentResolver();
        try (OutputStream out = resolver.openOutputStream(exportUri);
             FileInputStream in = new FileInputStream(src)) {
            if (out == null) return false;

            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────

    public static boolean cardExists(Context context, String gameDiscId, int slot) {
        return getCardFile(context, gameDiscId, slot).exists();
    }

    public static boolean deleteCard(Context context, String gameDiscId, int slot) {
        return getCardFile(context, gameDiscId, slot).delete();
    }
}