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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;

import com.tansoft.ps1emulator.R;

import java.util.ArrayList;
import java.util.List;

public class MoreAppsAdapter extends RecyclerView.Adapter<MoreAppsAdapter.AppViewHolder> {

    public static final int VIEW_TYPE_SLIDER = 0;
    public static final int VIEW_TYPE_LIST = 1;

    public interface OnAppClickListener {
        void onInstallClick(AppInfo app);
        void onCardClick(AppInfo app);
    }

    public static class AppInfo {
        public final String name;
        public final String description;
        public final String playStoreUrl;
        @DrawableRes
        public final int iconRes;
        @Nullable
        public final String iconAsset;

        public AppInfo(String name, String description, String playStoreUrl, @DrawableRes int iconRes) {
            this(name, description, playStoreUrl, iconRes, null);
        }

        public AppInfo(String name, String description, String playStoreUrl,
                       @DrawableRes int iconRes, @Nullable String iconAsset) {
            this.name = name;
            this.description = description;
            this.playStoreUrl = playStoreUrl;
            this.iconRes = iconRes;
            this.iconAsset = iconAsset;
        }
    }

    private List<AppInfo> apps = new ArrayList<>();
    private final OnAppClickListener listener;
    private final int viewType;

    public MoreAppsAdapter(OnAppClickListener listener) {
        this.listener = listener;
        this.viewType = VIEW_TYPE_SLIDER;
    }

    public MoreAppsAdapter(OnAppClickListener listener, int viewType) {
        this.listener = listener;
        this.viewType = viewType;
    }

    public void setApps(List<AppInfo> apps) {
        this.apps = apps != null ? apps : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = viewType == VIEW_TYPE_LIST
                ? R.layout.item_more_app_list
                : R.layout.item_more_app_slider;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = apps.get(position);

        holder.nameView.setText(app.name);
        holder.descriptionView.setText(app.description);

        if (app.iconAsset != null) {
            loadAssetIcon(holder.iconView, app.iconAsset, app.iconRes);
        } else {
            holder.iconView.setImageResource(app.iconRes);
        }

        holder.installButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onInstallClick(app);
            }
        });

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCardClick(app);
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    private void loadAssetIcon(ImageView view, String assetPath, @DrawableRes int fallback) {
        Context context = view.getContext();
        try (InputStream is = context.getAssets().open(assetPath)) {
            Drawable drawable = Drawable.createFromStream(is, null);
            if (drawable != null) {
                view.setImageDrawable(drawable);
                return;
            }
        } catch (IOException ignored) {
        }
        view.setImageResource(fallback);
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final View cardView;
        final ImageView iconView;
        final TextView nameView;
        final TextView descriptionView;
        final TextView installButton;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.more_app_card);
            iconView = itemView.findViewById(R.id.more_app_icon);
            nameView = itemView.findViewById(R.id.more_app_name);
            descriptionView = itemView.findViewById(R.id.more_app_description);
            installButton = itemView.findViewById(R.id.more_app_install);
        }
    }
}
