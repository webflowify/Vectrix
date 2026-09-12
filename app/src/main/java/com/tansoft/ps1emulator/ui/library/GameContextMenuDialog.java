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
import android.util.Log;
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

    private static final String TAG = "GameCtxMenuDialog";

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

    public interface OnDismissOverlayListener {
        void onOverlayDismissed();
    }

    private OnMenuActionListener listener;
    private OnDismissOverlayListener dismissListener;
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
        Log.d(TAG, "newInstance: game=" + game.title + " id=" + game.id);
        return dialog;
    }

    public void setOnDismissOverlayListener(OnDismissOverlayListener listener) {
        this.dismissListener = listener;
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach: context=" + context.getClass().getSimpleName()
                + " fragmentTag=" + getTag()
                + " hashCode=" + System.identityHashCode(this));
        if (context instanceof OnMenuActionListener) {
            listener = (OnMenuActionListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: savedInstanceState=" + (savedInstanceState == null ? "null" : "EXISTS(size=" + savedInstanceState.size() + ")")
                + " arguments=" + (getArguments() == null ? "null" : "EXISTS")
                + " hashCode=" + System.identityHashCode(this));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: hashCode=" + System.identityHashCode(this));
        return inflater.inflate(R.layout.dialog_game_context_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: savedInstanceState=" + (savedInstanceState == null ? "null" : "EXISTS")
                + " hashCode=" + System.identityHashCode(this));

        if (getDialog() != null) {
            getDialog().setOnDismissListener(dialog -> {
                Log.d(TAG, "dialog onDismiss: hashCode=" + System.identityHashCode(this));
                if (dismissListener != null) {
                    dismissListener.onOverlayDismissed();
                }
            });
        }

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

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: hashCode=" + System.identityHashCode(this)
                + " isAdded=" + isAdded()
                + " dialog=" + (getDialog() != null ? "showing=" + getDialog().isShowing() : "null"));
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: hashCode=" + System.identityHashCode(this));
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: hashCode=" + System.identityHashCode(this));
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop: hashCode=" + System.identityHashCode(this));
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView: hashCode=" + System.identityHashCode(this));
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: hashCode=" + System.identityHashCode(this));
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach: hashCode=" + System.identityHashCode(this));
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState: DISCARDING state, hashCode=" + System.identityHashCode(this)
                + " isAdded=" + isAdded()
                + " isRemoving=" + isRemoving()
                + " getFragmentManager=" + (getParentFragmentManager() != null));
        super.onSaveInstanceState(new Bundle());
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        Log.d(TAG, "onDismiss: hashCode=" + System.identityHashCode(this));
        super.onDismiss(dialog);
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        Log.d(TAG, "onCancel: hashCode=" + System.identityHashCode(this));
        super.onCancel(dialog);
    }
}
