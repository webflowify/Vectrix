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

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {GameEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String TAG = "AppDatabase";
    public abstract GameDao gameDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context);
                }
            }
        }
        return INSTANCE;
    }

    private static AppDatabase buildDatabase(Context context) {
        try {
            return Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "ps1emu_library.db"
            ).fallbackToDestructiveMigration().build();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to build Room database on first attempt", e);
            Context appContext = context.getApplicationContext();
            boolean deleted = appContext.deleteDatabase("ps1emu_library.db");
            Log.i(TAG, "Deleted database file: " + deleted);
            try {
                return Room.databaseBuilder(
                        appContext,
                        AppDatabase.class,
                        "ps1emu_library.db"
                ).fallbackToDestructiveMigration().build();
            } catch (RuntimeException retryError) {
                Log.e(TAG, "Failed to rebuild Room database after deletion", retryError);
                throw new RuntimeException(
                        "Cannot create Room database. This may be caused by R8 stripping " +
                        "the generated database implementation class. Ensure ProGuard rules " +
                        "include: -keep class **_Impl { *; }", retryError);
            }
        }
    }
}
