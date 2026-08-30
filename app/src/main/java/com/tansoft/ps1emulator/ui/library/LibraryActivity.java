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

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tansoft.ps1emulator.util.EdgeToEdgeHelper;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.data.AppDatabase;
import com.tansoft.ps1emulator.data.GameEntity;
import com.tansoft.ps1emulator.storage.BiosManager;
import com.tansoft.ps1emulator.storage.ImportResult;
import com.tansoft.ps1emulator.storage.ArchiveExtractor;
import com.tansoft.ps1emulator.storage.RomImporter;
import com.tansoft.ps1emulator.storage.SaveExportManager;
import com.tansoft.ps1emulator.storage.SaveImportManager;
import com.tansoft.ps1emulator.storage.MemoryCardManager;
import com.tansoft.ps1emulator.storage.SaveStateManager;
import com.tansoft.ps1emulator.ui.emulation.EmulationActivity;
import com.tansoft.ps1emulator.ui.savestate.SaveStateActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class LibraryActivity extends AppCompatActivity
        implements GameContextMenuDialog.OnMenuActionListener {

    private static final int REQUEST_CODE_ROM_PICKER = 2001;
    private static final int REQUEST_CODE_EXPORT_DIR = 3001;
    private static final int REQUEST_CODE_IMPORT_FILES = 3002;

    private RecyclerView recyclerView;
    private GameAdapter adapter;
    private LibraryViewModel viewModel;
    private EditText searchEditText;
    private ChipGroup filterChipGroup;
    private TextView gameCountTextView;
    private GameEntity pendingExportGame;
    private GameEntity pendingImportGame;
    private ExtendedFloatingActionButton fabAddRom;
    private boolean isFabVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_library);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            v.setPadding(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        setupToolbar();
        setupRecyclerView();
        setupSearch();
        setupFilterChips();
        setupFab();
        setupMoreApps();
        setupViewModel();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Game Library");
        
        gameCountTextView = new TextView(this);
        gameCountTextView.setTextColor(getResources().getColor(R.color.md_on_surface_variant, getTheme()));
        gameCountTextView.setTextSize(14);
        gameCountTextView.setPadding(0, 0, 16, 0);
        
        toolbar.addView(gameCountTextView, new MaterialToolbar.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.END
        ));
        
        gameCountTextView.setText("0 games");
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_games);
        int spanCount = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerView.setItemAnimator(null);

        adapter = new GameAdapter(
            (game, cardView) -> {
                Uri biosUri = BiosManager.getImportedBiosUri(this);

                Intent intent = new Intent(this, EmulationActivity.class);
                intent.putExtra("rom_path", game.romPath);
                intent.putExtra("bios_path", biosUri != null ? biosUri.toString() : null);
                intent.putExtra("game_title", game.title);
                intent.putExtra("game_disc_id", game.discId);
                intent.putExtra("archive_path", game.archivePath);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    String transitionName = "game_card_" + game.id;
                    android.util.Pair<View, String> pair = android.util.Pair.create(cardView, transitionName);
                    intent.putExtra("transition_name", transitionName);
                    android.app.ActivityOptions options = android.app.ActivityOptions
                            .makeSceneTransitionAnimation(this, pair);
                    startActivity(intent, options.toBundle());
                } else {
                    startActivity(intent);
                }
            },
            (game, cardView) -> {
                GameContextMenuDialog dialog = GameContextMenuDialog.newInstance(game);
                dialog.show(getSupportFragmentManager(), "game_context_menu");
            }
        );
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                super.onScrollStateChanged(rv, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy > 8 && isFabVisible) {
                    fabAddRom.shrink();
                    isFabVisible = false;
                } else if (dy < -8 && !isFabVisible) {
                    fabAddRom.extend();
                    isFabVisible = true;
                }
            }
        });
    }

    private void setupSearch() {
        searchEditText = findViewById(R.id.searchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupFilterChips() {
        filterChipGroup = findViewById(R.id.filter_chip_group);
        
        Chip chipTitle = findViewById(R.id.chip_title);
        Chip chipRecentlyPlayed = findViewById(R.id.chip_recently_played);
        Chip chipRecentlyAdded = findViewById(R.id.chip_recently_added);

        chipTitle.setOnClickListener(v -> {
            viewModel.setSortMode(LibraryViewModel.SortMode.TITLE);
        });

        chipRecentlyPlayed.setOnClickListener(v -> {
            viewModel.setSortMode(LibraryViewModel.SortMode.RECENTLY_PLAYED);
        });

        chipRecentlyAdded.setOnClickListener(v -> {
            viewModel.setSortMode(LibraryViewModel.SortMode.RECENTLY_ADDED);
        });
    }

    private void setupFab() {
        fabAddRom = findViewById(R.id.fab_add_rom);
        fabAddRom.setOnClickListener(v -> {
            Intent picker = RomImporter.createRomPickerIntent();
            startActivityForResult(picker, REQUEST_CODE_ROM_PICKER);
        });
    }

    private void setupMoreApps() {
        View section = findViewById(R.id.more_apps_slider_section);
        RecyclerView recycler = findViewById(R.id.recycler_more_apps);
        View seeAll = findViewById(R.id.more_apps_see_all);

        if (section == null || recycler == null || seeAll == null) {
            return;
        }

        MoreAppsAdapter sliderAdapter = new MoreAppsAdapter(new MoreAppsAdapter.OnAppClickListener() {
            @Override
            public void onInstallClick(MoreAppsAdapter.AppInfo app) {
                openPlayStore(app.playStoreUrl);
            }

            @Override
            public void onCardClick(MoreAppsAdapter.AppInfo app) {
                openPlayStore(app.playStoreUrl);
            }
        }, MoreAppsAdapter.VIEW_TYPE_SLIDER);

        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(sliderAdapter);

        java.util.List<MoreAppsAdapter.AppInfo> apps = new java.util.ArrayList<>();
        apps.add(new MoreAppsAdapter.AppInfo(
                getString(R.string.more_apps_gba_name),
                getString(R.string.more_apps_gba_description),
                "https://play.google.com/store/apps/details?id=com.tansoft.gbaemulator",
                R.drawable.ic_gba_emulator,
                "logo-gba-icon.png"
        ));

        sliderAdapter.setApps(apps);
        section.setVisibility(View.VISIBLE);

        seeAll.setOnClickListener(v -> {
            startActivity(new Intent(this, MoreAppsActivity.class));
        });
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

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        
        viewModel.getFilteredGames().observe(this, games -> {
            if (games == null || games.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                findViewById(R.id.empty_state).setVisibility(View.VISIBLE);
                gameCountTextView.setText("0 games");
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                findViewById(R.id.empty_state).setVisibility(View.GONE);
                adapter.submitList(games);
                gameCountTextView.setText(games.size() + " game" + (games.size() == 1 ? "" : "s"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshGames();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_ROM_PICKER && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importRom(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_EXPORT_DIR && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null && pendingExportGame != null) {
                exportSaves(pendingExportGame, data.getData());
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_FILES && resultCode == RESULT_OK) {
            if (data != null && pendingImportGame != null) {
                List<Uri> fileUris = new ArrayList<>();
                if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        fileUris.add(clipData.getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    fileUris.add(data.getData());
                }
                if (!fileUris.isEmpty()) {
                    importSaves(pendingImportGame, fileUris);
                }
            }
        }
    }

    // --- Progress overlay ---

    private void showProgress(String message) {
        showProgress(message, true);
    }

    private void showProgress(String message, boolean showSpinner) {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            TextView text = overlay.findViewById(R.id.progress_text);
            if (text != null) {
                text.setText(message);
            }
            View spinner = overlay.findViewById(R.id.progress_spinner);
            if (spinner != null) {
                spinner.setVisibility(showSpinner ? View.VISIBLE : View.GONE);
            }
            View horizontalBar = overlay.findViewById(R.id.progress_bar_horizontal);
            if (horizontalBar != null) {
                horizontalBar.setVisibility(View.GONE);
            }
            View percentText = overlay.findViewById(R.id.progress_percent);
            if (percentText != null) {
                percentText.setVisibility(View.GONE);
            }
            View fileNameText = overlay.findViewById(R.id.progress_file_name);
            if (fileNameText != null) {
                fileNameText.setVisibility(View.GONE);
            }
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200);
        }
    }

    private void showExtractionProgress(String fileName, int percent) {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay == null) return;

        View spinner = overlay.findViewById(R.id.progress_spinner);
        if (spinner != null) {
            spinner.setVisibility(View.GONE);
        }

        TextView text = overlay.findViewById(R.id.progress_text);
        if (text != null) {
            text.setText("Extracting...");
        }

        TextView fileNameView = overlay.findViewById(R.id.progress_file_name);
        if (fileNameView != null) {
            if (fileName != null && !fileName.isEmpty()) {
                fileNameView.setText(fileName);
                fileNameView.setVisibility(View.VISIBLE);
            } else {
                fileNameView.setVisibility(View.GONE);
            }
        }

        android.widget.ProgressBar horizontalBar = overlay.findViewById(R.id.progress_bar_horizontal);
        if (horizontalBar != null) {
            horizontalBar.setVisibility(View.VISIBLE);
            horizontalBar.setProgress(percent);
        }

        TextView percentView = overlay.findViewById(R.id.progress_percent);
        if (percentView != null) {
            percentView.setVisibility(View.VISIBLE);
            percentView.setText(percent + "%");
        }
    }

    private void hideProgress() {
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            overlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> overlay.setVisibility(View.GONE));
        }
    }

    private void importRom(Uri uri) {
        showProgress("Importing ROM...");
        fabAddRom.setEnabled(false);

        ArchiveExtractor.ExtractionProgressListener listener = (fileName, percent, bytesExtracted, totalBytes) -> {
            runOnUiThread(() -> showExtractionProgress(fileName, percent));
        };

        RomImporter.importRomAsync(this, uri, listener, phase -> {
            runOnUiThread(() -> {
                View overlay = findViewById(R.id.progress_overlay);
                if (overlay != null) {
                    TextView text = overlay.findViewById(R.id.progress_text);
                    if (text != null) {
                        text.setText(phase);
                    }
                }
            });
        }, result -> {
            hideProgress();
            fabAddRom.setEnabled(true);
            handleImportResult(result);
        });
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void handleImportResult(ImportResult result) {
        if (!isActivityAlive()) {
            return;
        }
        switch (result.status) {
            case SUCCESS:
                break;

            case DUPLICATE:
                Toast.makeText(this, "Game already in library", Toast.LENGTH_SHORT).show();
                break;

            case NO_GAME_FOUND:
                new MaterialAlertDialogBuilder(this)
                        .setTitle(result.errorTitle)
                        .setMessage(result.errorMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;

            case PARTIAL_IMPORT:
                new MaterialAlertDialogBuilder(this)
                        .setTitle(result.errorTitle)
                        .setMessage(result.errorMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;

            case EXTRACTION_FAILED:
                new MaterialAlertDialogBuilder(this)
                        .setTitle(result.errorTitle)
                        .setMessage(result.errorMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;

            case CORRUPTED:
                new MaterialAlertDialogBuilder(this)
                        .setTitle(result.errorTitle)
                        .setMessage(result.errorMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;
        }
    }

    // --- Configuration change ---

    private static final String KEY_PROGRESS_VISIBLE = "progress_visible";
    private static final String KEY_PROGRESS_TEXT = "progress_text";

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        View overlay = findViewById(R.id.progress_overlay);
        if (overlay != null) {
            outState.putBoolean(KEY_PROGRESS_VISIBLE, overlay.getVisibility() == View.VISIBLE);
            TextView text = overlay.findViewById(R.id.progress_text);
            if (text != null) {
                outState.putString(KEY_PROGRESS_TEXT, text.getText().toString());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(KEY_PROGRESS_VISIBLE, false)) {
            showProgress(savedInstanceState.getString(KEY_PROGRESS_TEXT, ""));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_library, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            showSupportedFormatsInfo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSupportedFormatsInfo() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_supported_formats, null);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // --- Game context menu handlers ---

    private static String resolveDiscId(GameEntity game) {
        String discId = game.discId;
        if (discId != null && !discId.isEmpty()) {
            return discId;
        }
        if (game.romPath != null) {
            File f = new File(game.romPath);
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            return (dot > 0) ? name.substring(0, dot) : name;
        }
        return null;
    }

    @Override
    public void onExportSaves(GameEntity game) {
        pendingExportGame = game;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_EXPORT_DIR);
    }

    @Override
    public void onImportSaves(GameEntity game) {
        pendingImportGame = game;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FILES);
    }

    @Override
    public void onManageSaveStates(GameEntity game) {
        String discId = resolveDiscId(game);
        if (discId == null) {
            Toast.makeText(this, "Cannot manage saves: Game ID and ROM path are missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = SaveStateActivity.createBrowseIntent(this, discId);
        startActivity(intent);
    }

    @Override
    public void onRemoveGame(GameEntity game) {
        showRemoveGameDialog(game);
    }

    private void exportSaves(GameEntity game, Uri dirUri) {
        showProgress(getString(R.string.export_progress));

        String discId = resolveDiscId(game);
        if (discId == null) {
            hideProgress();
            Toast.makeText(this, "Cannot export: Game ID and ROM path are missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        SaveExportManager.exportAsync(this, discId, game.title, dirUri,
            new SaveExportManager.ExportProgressListener() {
                @Override
                public void onFileExporting(String fileName, int current, int total) {
                    runOnUiThread(() -> showExtractionProgress(fileName,
                        (int) ((current / (float) total) * 100)));
                }

                @Override
                public void onComplete(SaveExportManager.ExportResult result) {
                    runOnUiThread(() -> {
                        hideProgress();
                        if (!isActivityAlive()) {
                            return;
                        }
                        if (result.success) {
                            String msg = getString(R.string.export_success, result.filesExported);
                            if (!result.warnings.isEmpty()) {
                                msg += "\n" + String.join("\n", result.warnings);
                            }
                            Toast.makeText(LibraryActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            new MaterialAlertDialogBuilder(LibraryActivity.this)
                                .setTitle(R.string.export_failed)
                                .setMessage(String.join("\n", result.warnings))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        }
                    });
                }
            });
    }

    private void importSaves(GameEntity game, List<Uri> fileUris) {
        showProgress(getString(R.string.import_progress));

        String discId = resolveDiscId(game);
        if (discId == null) {
            hideProgress();
            Toast.makeText(this, "Cannot import: Game ID and ROM path are missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        SaveImportManager.importAsync(this, discId, fileUris,
            new SaveImportManager.ImportProgressListener() {
                @Override
                public void onFileImporting(String fileName, int current, int total) {
                    runOnUiThread(() -> showExtractionProgress(fileName,
                        (int) ((current / (float) total) * 100)));
                }

                @Override
                public void onComplete(SaveImportManager.ImportResult result) {
                    runOnUiThread(() -> {
                        hideProgress();
                        if (!isActivityAlive()) {
                            return;
                        }
                        int totalImported = result.cardsImported + result.statesImported;
                        if (totalImported > 0) {
                            String msg = getString(R.string.import_success, totalImported);
                            if (!result.warnings.isEmpty()) {
                                msg += "\n" + String.join("\n", result.warnings);
                            }
                            Toast.makeText(LibraryActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            new MaterialAlertDialogBuilder(LibraryActivity.this)
                                .setTitle(R.string.import_failed)
                                .setMessage(String.join("\n", result.warnings))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        }
                    });
                }
            },
            this::showOverwriteSlotDialog);
    }

    private int showOverwriteSlotDialog(String fileName, boolean[] slotStates) {
        final int[] chosenSlot = {-1};
        final CountDownLatch latch = new CountDownLatch(1);

        String[] slotLabels = new String[SaveStateManager.MAX_SLOTS];
        for (int i = 0; i < SaveStateManager.MAX_SLOTS; i++) {
            slotLabels[i] = "Slot " + (i + 1) + (slotStates[i] ? " (occupied)" : " (empty)");
        }

        runOnUiThread(() -> {
            if (!isActivityAlive()) {
                chosenSlot[0] = -1;
                latch.countDown();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                .setTitle("All slots full")
                .setMessage("Cannot import \"" + fileName + "\" — all 10 slots are occupied.\n\nChoose a slot to overwrite:")
                .setSingleChoiceItems(slotLabels, -1, (dialog, which) -> {
                    chosenSlot[0] = which;
                    dialog.dismiss();
                    latch.countDown();
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    chosenSlot[0] = -1;
                    dialog.dismiss();
                    latch.countDown();
                })
                .setCancelable(false)
                .show();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }

        return chosenSlot[0];
    }

    private void showRemoveGameDialog(GameEntity game) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialogView.findViewById(R.id.dialogMessage);
        TextView slotNumber = dialogView.findViewById(R.id.slotNumber);
        TextView slotTimestamp = dialogView.findViewById(R.id.slotTimestamp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        dialogTitle.setText(getString(R.string.remove_game_title, game.title));
        dialogMessage.setText(R.string.remove_game_message);
        slotNumber.setText(game.title);
        slotTimestamp.setText(game.discId != null ? game.discId : "");
        btnConfirm.setText(R.string.remove_game_confirm);

        View slotInfoCard = dialogView.findViewById(R.id.slotInfoCard);
        if (slotInfoCard != null) {
            slotInfoCard.setVisibility(game.discId != null ? View.VISIBLE : View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create();

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            removeGame(game);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void removeGame(GameEntity game) {
        deleteGameSaves(game);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            db.gameDao().delete(game);

            runOnUiThread(() -> {
                if (!isActivityAlive()) {
                    return;
                }
                Toast.makeText(this,
                    getString(R.string.remove_game_success, game.title),
                    Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void deleteGameSaves(GameEntity game) {
        String discId = resolveDiscId(game);
        if (discId == null) {
            return;
        }

        for (int slot = 0; slot <= 1; slot++) {
            File card = MemoryCardManager.getCardFile(this, discId, slot);
            if (card.exists()) card.delete();
        }

        File saveDir = SaveStateManager.getSaveDir(this, discId);
        if (saveDir.exists()) {
            File[] files = saveDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            saveDir.delete();
        }

        if (game.thumbnailPath != null) {
            File thumb = new File(game.thumbnailPath);
            if (thumb.exists()) thumb.delete();
        }
    }
}