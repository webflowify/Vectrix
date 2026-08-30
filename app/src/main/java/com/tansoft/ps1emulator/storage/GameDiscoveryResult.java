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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameDiscoveryResult {

    public enum Status {
        FOUND_CUE_BIN,
        FOUND_ISO,
        FOUND_CHD,
        FOUND_BIN_ONLY,
        FOUND_EXECUTABLE,
        FOUND_MULTI_DISC,
        NO_GAME_FOUND,
        CORRUPTED
    }

    public final Status status;
    public final File primaryFile;
    public final File cueFile;
    public final List<File> binFiles;
    public final List<String> warnings;
    public final String errorDetail;

    private GameDiscoveryResult(Status status, File primaryFile, File cueFile,
                                List<File> binFiles, List<String> warnings, String errorDetail) {
        this.status = status;
        this.primaryFile = primaryFile;
        this.cueFile = cueFile;
        this.binFiles = binFiles != null ? Collections.unmodifiableList(binFiles) : Collections.emptyList();
        this.warnings = warnings != null ? Collections.unmodifiableList(warnings) : Collections.emptyList();
        this.errorDetail = errorDetail;
    }

    public boolean isPlayable() {
        return status != Status.NO_GAME_FOUND && status != Status.CORRUPTED;
    }

    public static GameDiscoveryResult cueBinFound(File cueFile, List<File> binFiles, List<String> warnings) {
        return new GameDiscoveryResult(Status.FOUND_CUE_BIN, cueFile, cueFile, binFiles, warnings, null);
    }

    public static GameDiscoveryResult isoFound(File isoFile) {
        return new GameDiscoveryResult(Status.FOUND_ISO, isoFile, null, null, null, null);
    }

    public static GameDiscoveryResult chdFound(File chdFile) {
        return new GameDiscoveryResult(Status.FOUND_CHD, chdFile, null, null, null, null);
    }

    public static GameDiscoveryResult binOnlyFound(File binFile, List<String> warnings) {
        return new GameDiscoveryResult(Status.FOUND_BIN_ONLY, binFile, null, Collections.singletonList(binFile), warnings, null);
    }

    public static GameDiscoveryResult executableFound(File exeFile) {
        return new GameDiscoveryResult(Status.FOUND_EXECUTABLE, exeFile, null, null, null, null);
    }

    public static GameDiscoveryResult multiDiscFound(File m3uFile, List<File> binFiles) {
        return new GameDiscoveryResult(Status.FOUND_MULTI_DISC, m3uFile, null, binFiles, null, null);
    }

    public static GameDiscoveryResult noGameFound(String reason) {
        return new GameDiscoveryResult(Status.NO_GAME_FOUND, null, null, null, null, reason);
    }

    public static GameDiscoveryResult corrupted(String detail) {
        return new GameDiscoveryResult(Status.CORRUPTED, null, null, null, null, detail);
    }
}
