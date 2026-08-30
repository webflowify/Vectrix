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

import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.card.MaterialCardView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.data.GameEntity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.ViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(GameEntity game, View cardView);
    }

    public interface OnGameLongClickListener {
        void onGameLongClick(GameEntity game, View cardView);
    }

    private List<GameEntity> games = new ArrayList<>();
    private final OnGameClickListener listener;
    private final OnGameLongClickListener longClickListener;

    private static final int[] PLACEHOLDER_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00,
            0xFF8E24AA, 0xFF00ACC1, 0xFF6D4C41, 0xFF546E7A
    };

    public GameAdapter(OnGameClickListener listener, OnGameLongClickListener longListener) {
        this.listener = listener;
        this.longClickListener = longListener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return games.get(position).id;
    }

    public void submitList(List<GameEntity> newGames) {
        List<GameEntity> oldList = new ArrayList<>(this.games);
        this.games = newGames != null ? newGames : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new GameDiffCallback(oldList, this.games));
        diff.dispatchUpdatesTo(this);
    }

    private static class GameDiffCallback extends DiffUtil.Callback {

        private final List<GameEntity> oldList;
        private final List<GameEntity> newList;

        GameDiffCallback(List<GameEntity> oldList, List<GameEntity> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).id == newList.get(newPos).id;
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).equals(newList.get(newPos));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GameEntity game = games.get(position);
        holder.titleView.setText(game.title);

        if (game.discId != null && !game.discId.isEmpty()) {
            holder.discIdView.setVisibility(View.VISIBLE);
            holder.discIdView.setText(game.discId);
        } else {
            holder.discIdView.setVisibility(View.GONE);
        }

        if (game.thumbnailPath != null && !game.thumbnailPath.isEmpty()) {
            File thumbnailFile = new File(game.thumbnailPath);
            if (thumbnailFile.exists()) {
                Glide.with(holder.itemView.getContext())
                        .load(thumbnailFile)
                        .transform(new CenterCrop(), new RoundedCorners(24))
                        .placeholder(R.drawable.game_card_gradient_overlay)
                        .error(R.drawable.game_card_gradient_overlay)
                        .into(holder.placeholderView);
                holder.placeholderText.setVisibility(View.GONE);
            } else {
                setPlaceholderWithInitial(holder, game);
            }
        } else {
            setPlaceholderWithInitial(holder, game);
        }

        if (game.lastPlayedDate > 0) {
            String relativeTime = getRelativeTimeString(game.lastPlayedDate);
            holder.lastPlayedView.setText(relativeTime);
        } else {
            holder.lastPlayedView.setText("Never played");
        }

        String playTime = formatPlayTime(game.totalPlayTimeMs);
        holder.playTimeView.setText(playTime);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            holder.cardView.setTransitionName("game_card_" + game.id);
        }

        holder.itemView.setOnClickListener(v -> listener.onGameClick(game, holder.cardView));

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onGameLongClick(game, holder.cardView);
                return true;
            }
            return false;
        });
    }

    private void setPlaceholderWithInitial(ViewHolder holder, GameEntity game) {
        char firstLetter = game.title != null && !game.title.isEmpty()
                ? game.title.charAt(0) : '?';
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(16f);
        int colorIndex = (int) (game.id % PLACEHOLDER_COLORS.length);
        drawable.setColor(PLACEHOLDER_COLORS[colorIndex]);
        holder.placeholderView.setImageDrawable(drawable);
        holder.placeholderText.setVisibility(View.VISIBLE);
        holder.placeholderText.setText(String.valueOf(firstLetter));
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    private String formatPlayTime(long ms) {
        if (ms <= 0) return "0m";
        
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    private String getRelativeTimeString(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + "m ago";
        } else if (hours < 24) {
            return hours + "h ago";
        } else if (days < 7) {
            return days + "d ago";
        } else if (days < 30) {
            long weeks = days / 7;
            return weeks + "w ago";
        } else if (days < 365) {
            Calendar cal = Calendar.getInstance();
            int currentMonth = cal.get(Calendar.MONTH);
            int currentYear = cal.get(Calendar.YEAR);
            
            cal.setTimeInMillis(timestamp);
            int gameMonth = cal.get(Calendar.MONTH);
            int gameYear = cal.get(Calendar.YEAR);
            
            int monthsDiff = (currentYear - gameYear) * 12 + (currentMonth - gameMonth);
            if (monthsDiff < 1) monthsDiff = 1;
            return monthsDiff + "mo ago";
        } else {
            Calendar cal = Calendar.getInstance();
            int currentYear = cal.get(Calendar.YEAR);
            
            cal.setTimeInMillis(timestamp);
            int gameYear = cal.get(Calendar.YEAR);
            
            int yearsDiff = currentYear - gameYear;
            if (yearsDiff < 1) yearsDiff = 1;
            return yearsDiff + "y ago";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView titleView;
        final TextView discIdView;
        final ImageView placeholderView;
        final TextView placeholderText;
        final TextView lastPlayedView;
        final TextView playTimeView;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.game_card);
            titleView = itemView.findViewById(R.id.game_title);
            discIdView = itemView.findViewById(R.id.game_disc_id);
            placeholderView = itemView.findViewById(R.id.game_placeholder);
            placeholderText = itemView.findViewById(R.id.game_placeholder_text);
            lastPlayedView = itemView.findViewById(R.id.game_last_played);
            playTimeView = itemView.findViewById(R.id.game_play_time);
        }
    }
}
