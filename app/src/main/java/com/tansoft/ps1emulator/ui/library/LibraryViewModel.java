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

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.tansoft.ps1emulator.data.AppDatabase;
import com.tansoft.ps1emulator.data.GameEntity;
import com.tansoft.ps1emulator.storage.ImportResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    public enum SortMode {
        TITLE,
        RECENTLY_PLAYED,
        RECENTLY_ADDED
    }

    public static class ImportProgress {
        public final String fileName;
        public final int percent;

        public ImportProgress(String fileName, int percent) {
            this.fileName = fileName;
            this.percent = percent;
        }
    }

    private final LiveData<List<GameEntity>> allGames;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<SortMode> sortMode = new MutableLiveData<>(SortMode.TITLE);
    private final MediatorLiveData<List<GameEntity>> filteredGames = new MediatorLiveData<>();

    private final MutableLiveData<Boolean> isImporting = new MutableLiveData<>(false);
    private final MutableLiveData<String> importMessage = new MutableLiveData<>();
    private final MutableLiveData<ImportProgress> importProgress = new MutableLiveData<>();
    private final MutableLiveData<ImportResult> importResult = new MutableLiveData<>();
    private Uri pendingImportUri;

    public LibraryViewModel(Application application) {
        super(application);
        allGames = AppDatabase.getInstance(application).gameDao().getAllGames();

        filteredGames.addSource(allGames, games -> applyFilters(games, searchQuery.getValue(), sortMode.getValue()));
        filteredGames.addSource(searchQuery, query -> applyFilters(allGames.getValue(), query, sortMode.getValue()));
        filteredGames.addSource(sortMode, mode -> applyFilters(allGames.getValue(), searchQuery.getValue(), mode));
    }

    public LiveData<List<GameEntity>> getAllGames() {
        return allGames;
    }

    public LiveData<List<GameEntity>> getFilteredGames() {
        return filteredGames;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public void setSortMode(SortMode mode) {
        sortMode.setValue(mode);
    }

    public SortMode getSortMode() {
        return sortMode.getValue();
    }

    public LiveData<SortMode> getSortModeLiveData() {
        return sortMode;
    }

    public void refreshGames() {
        applyFilters(allGames.getValue(), searchQuery.getValue(), sortMode.getValue());
    }

    // --- Import state (survives configuration changes) ---

    public LiveData<Boolean> getIsImporting() {
        return isImporting;
    }

    public LiveData<String> getImportMessage() {
        return importMessage;
    }

    public LiveData<ImportProgress> getImportProgress() {
        return importProgress;
    }

    public LiveData<ImportResult> getImportResult() {
        return importResult;
    }

    public Uri getPendingImportUri() {
        return pendingImportUri;
    }

    public void startImport(Uri uri) {
        pendingImportUri = uri;
        isImporting.postValue(true);
        importMessage.postValue("Importing ROM...");
        importProgress.postValue(new ImportProgress(null, 0));
    }

    public void updateImportMessage(String message) {
        importMessage.postValue(message);
    }

    public void updateExtractionProgress(String fileName, int percent) {
        importProgress.postValue(new ImportProgress(fileName, percent));
    }

    public void completeImport(ImportResult result) {
        pendingImportUri = null;
        isImporting.postValue(false);
        importResult.postValue(result);
    }

    public void consumeImportResult() {
        importResult.setValue(null);
    }

    private void applyFilters(List<GameEntity> games, String query, SortMode mode) {
        if (games == null) {
            filteredGames.setValue(new ArrayList<>());
            return;
        }

        List<GameEntity> result = new ArrayList<>(games);

        // Apply search filter
        if (query != null && !query.trim().isEmpty()) {
            String lowerQuery = query.trim().toLowerCase();
            result.removeIf(game ->
                    game.title == null || !game.title.toLowerCase().contains(lowerQuery));
        }

        // Apply sorting
        if (mode != null) {
            switch (mode) {
                case TITLE:
                    Collections.sort(result, (a, b) -> {
                        String titleA = a.title != null ? a.title.toLowerCase() : "";
                        String titleB = b.title != null ? b.title.toLowerCase() : "";
                        return titleA.compareTo(titleB);
                    });
                    break;
                case RECENTLY_PLAYED:
                    Collections.sort(result, (a, b) -> Long.compare(b.lastPlayedDate, a.lastPlayedDate));
                    break;
                case RECENTLY_ADDED:
                    Collections.sort(result, (a, b) -> Long.compare(b.addedDate, a.addedDate));
                    break;
            }
        }

        filteredGames.setValue(result);
    }
}