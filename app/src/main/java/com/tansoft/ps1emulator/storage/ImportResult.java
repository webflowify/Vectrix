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

import com.tansoft.ps1emulator.data.GameEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportResult {

    public enum Status {
        SUCCESS,
        DUPLICATE,
        NO_GAME_FOUND,
        PARTIAL_IMPORT,
        EXTRACTION_FAILED,
        CORRUPTED
    }

    public final Status status;
    public final GameEntity game;
    public final List<String> warnings;
    public final String errorTitle;
    public final String errorMessage;

    private ImportResult(Status status, GameEntity game, List<String> warnings,
                         String errorTitle, String errorMessage) {
        this.status = status;
        this.game = game;
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.errorTitle = errorTitle;
        this.errorMessage = errorMessage;
    }

    public boolean isPlayable() {
        return status == Status.SUCCESS || status == Status.DUPLICATE || status == Status.PARTIAL_IMPORT;
    }

    public static ImportResult success(GameEntity game) {
        return new ImportResult(Status.SUCCESS, game, null, null, null);
    }

    public static ImportResult duplicate(GameEntity game) {
        return new ImportResult(Status.DUPLICATE, game, null, null, null);
    }

    public static ImportResult noGameFound(String archiveName) {
        return new ImportResult(Status.NO_GAME_FOUND, null, null,
                "Import Failed",
                "No playable game files found in \"" + archiveName + "\".\n\n"
                        + "Supported formats: .bin, .cue, .iso, .chd, .exe, .m3u, .pbp, .chd");
    }

    public static ImportResult partialImport(GameEntity game, List<String> warnings) {
        return new ImportResult(Status.PARTIAL_IMPORT, game, warnings,
                "Imported with Warnings",
                buildPartialMessage(warnings));
    }

    public static ImportResult extractionFailed(String archiveName, String detail) {
        return new ImportResult(Status.EXTRACTION_FAILED, null, null,
                "Extraction Failed",
                "Could not extract \"" + archiveName + "\".\n\n" + detail);
    }

    public static ImportResult corrupted(String archiveName) {
        return new ImportResult(Status.CORRUPTED, null, null,
                "Archive Corrupted",
                "\"" + archiveName + "\" appears to be damaged or incomplete.");
    }

    private static String buildPartialMessage(List<String> warnings) {
        StringBuilder sb = new StringBuilder("Game was imported with the following issues:\n\n");
        for (int i = 0; i < warnings.size(); i++) {
            sb.append("• ").append(warnings.get(i));
            if (i < warnings.size() - 1) sb.append("\n");
        }
        sb.append("\n\nThe game may not load correctly.");
        return sb.toString();
    }
}
