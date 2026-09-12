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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface GameDao {
    @Query("SELECT * FROM games ORDER BY lastPlayedDate DESC")
    LiveData<List<GameEntity>> getAllGames();

    @Query("SELECT * FROM games")
    List<GameEntity> getAllGamesSync();

    @Query("SELECT * FROM games WHERE romPath = :path LIMIT 1")
    GameEntity getByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(GameEntity game);

    @Update
    void update(GameEntity game);

    @Delete
    void delete(GameEntity game);
}
