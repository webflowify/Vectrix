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
package com.tansoft.ps1emulator.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "games")
public class GameEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public String romPath;
    public String discId;
    public String cueSheetPath;
    public String archivePath;
    public String thumbnailPath;
    public long addedDate;
    public long lastPlayedDate;
    public int playCount;
    public long totalPlayTimeMs;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameEntity that = (GameEntity) o;
        return id == that.id &&
                addedDate == that.addedDate &&
                lastPlayedDate == that.lastPlayedDate &&
                playCount == that.playCount &&
                totalPlayTimeMs == that.totalPlayTimeMs &&
                Objects.equals(title, that.title) &&
                Objects.equals(romPath, that.romPath) &&
                Objects.equals(discId, that.discId) &&
                Objects.equals(cueSheetPath, that.cueSheetPath) &&
                Objects.equals(archivePath, that.archivePath) &&
                Objects.equals(thumbnailPath, that.thumbnailPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, romPath, discId, cueSheetPath, archivePath,
                thumbnailPath, addedDate, lastPlayedDate, playCount, totalPlayTimeMs);
    }
}
