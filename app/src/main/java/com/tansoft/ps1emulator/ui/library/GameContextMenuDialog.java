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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.data.GameEntity;

public class GameContextMenuDialog extends BottomSheetDialogFragment {

    private static final String ARG_GAME_TITLE = "game_title";
    private static final String ARG_GAME_ID = "game_id";
    private static final String ARG_GAME_DISC_ID = "game_disc_id";
    private static final String ARG_GAME_ROM_PATH = "game_rom_path";
    private static final String ARG_GAME_THUMBNAIL_PATH = "game_thumbnail_path";

    public interface OnMenuActionListener {
        void onExportSaves(GameEntity game);
        void onImportSaves(GameEntity game);
        void onManageSaveStates(GameEntity game);
        void onRemoveGame(GameEntity game);
    }

    private OnMenuActionListener listener;
    private GameEntity game;

    public static GameContextMenuDialog newInstance(GameEntity game) {
        GameContextMenuDialog dialog = new GameContextMenuDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_GAME_ID, game.id);
        args.putString(ARG_GAME_TITLE, game.title);
        args.putString(ARG_GAME_DISC_ID, game.discId);
        args.putString(ARG_GAME_ROM_PATH, game.romPath);
        args.putString(ARG_GAME_THUMBNAIL_PATH, game.thumbnailPath);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnMenuActionListener) {
            listener = (OnMenuActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_game_context_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        game = new GameEntity();
        game.id = getArguments().getLong(ARG_GAME_ID);
        game.title = getArguments().getString(ARG_GAME_TITLE, "Unknown Game");
        game.discId = getArguments().getString(ARG_GAME_DISC_ID);
        game.romPath = getArguments().getString(ARG_GAME_ROM_PATH);
        game.thumbnailPath = getArguments().getString(ARG_GAME_THUMBNAIL_PATH);

        ((TextView) view.findViewById(R.id.context_game_title)).setText(game.title);

        view.findViewById(R.id.btn_export_saves).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onExportSaves(game);
        });

        view.findViewById(R.id.btn_import_saves).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onImportSaves(game);
        });

        view.findViewById(R.id.btn_manage_saves).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onManageSaveStates(game);
        });

        view.findViewById(R.id.btn_remove_game).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onRemoveGame(game);
        });
    }
}
