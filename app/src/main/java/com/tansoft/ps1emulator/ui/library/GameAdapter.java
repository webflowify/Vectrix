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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.tansoft.ps1emulator.R;
import com.tansoft.ps1emulator.ads.AdsConfig;
import com.tansoft.ps1emulator.data.GameEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GameAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(GameEntity game, View cardView);
    }

    public interface OnGameLongClickListener {
        void onGameLongClick(GameEntity game, View cardView);
    }

    public interface OnGameSettingsClickListener {
        void onGameSettingsClick(GameEntity game, View cardView);
    }

    private List<GameEntity> games = new ArrayList<>();
    private final OnGameClickListener listener;
    private final OnGameLongClickListener longClickListener;
    private final OnGameSettingsClickListener settingsClickListener;

    private static final int TYPE_GAME = 0;
    private static final int TYPE_NATIVE_AD = 1;
    private static final int AD_INTERVAL = 5;

    private static final int[] PLACEHOLDER_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00,
            0xFF8E24AA, 0xFF00ACC1, 0xFF6D4C41, 0xFF546E7A
    };

    private List<NativeAd> nativeAds = new ArrayList<>();

    public void setNativeAds(List<NativeAd> ads) {
        this.nativeAds = ads;
        notifyDataSetChanged();
    }

    public GameAdapter(OnGameClickListener listener, OnGameLongClickListener longListener,
                       OnGameSettingsClickListener settingsListener) {
        this.listener = listener;
        this.longClickListener = longListener;
        this.settingsClickListener = settingsListener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_NATIVE_AD) {
            return -1;
        }
        int gamePos = getGamePosition(position);
        if (gamePos < 0 || gamePos >= games.size()) return -1;
        return games.get(gamePos).id;
    }

    public void submitList(List<GameEntity> newGames) {
        List<GameEntity> oldList = new ArrayList<>(this.games);
        this.games = newGames != null ? newGames : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new GameDiffCallback(oldList, this.games));
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        if (games.size() >= AdsConfig.NATIVE_AD_MIN_GAMES
                && position > 0 && position % AD_INTERVAL == 0
                && !nativeAds.isEmpty()) {
            int adIndex = (position / AD_INTERVAL) - 1;
            if (adIndex < nativeAds.size()) {
                return TYPE_NATIVE_AD;
            }
        }
        return TYPE_GAME;
    }

    @Override
    public int getItemCount() {
        int gameCount = games.size();
        if (gameCount < AdsConfig.NATIVE_AD_MIN_GAMES || nativeAds.isEmpty()) {
            return gameCount;
        }
        int adCount = Math.min(nativeAds.size(), gameCount / AD_INTERVAL);
        return gameCount + adCount;
    }

    public int getGamePosition(int adapterPosition) {
        if (getItemViewType(adapterPosition) == TYPE_NATIVE_AD) {
            return -1;
        }
        int adCountBefore = adapterPosition / (AD_INTERVAL + 1);
        return adapterPosition - adCountBefore;
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_NATIVE_AD) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_native_ad, parent, false);
            return new NativeAdViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NativeAdViewHolder) {
            NativeAd ad = nativeAds.get((position / AD_INTERVAL) - 1);
            ((NativeAdViewHolder) holder).bind(ad);
            return;
        }

        ViewHolder gameHolder = (ViewHolder) holder;
        int gamePos = getGamePosition(position);
        if (gamePos < 0 || gamePos >= games.size()) return;
        GameEntity game = games.get(gamePos);
        gameHolder.titleView.setText(game.title);

        Glide.with(holder.itemView.getContext()).clear(gameHolder.placeholderBg);
        gameHolder.placeholderText.setVisibility(View.GONE);
        setPlaceholderWithInitial(gameHolder, game);
        gameHolder.playOverlay.setVisibility(View.VISIBLE);

        String timeText;
        if (game.lastPlayedDate > 0) {
            String relativeTime = getRelativeTimeString(game.lastPlayedDate);
            String playTime = formatPlayTime(game.totalPlayTimeMs);
            timeText = relativeTime + " · " + playTime;
        } else {
            timeText = "Never played";
        }
        gameHolder.lastPlayedView.setText(timeText);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            gameHolder.cardView.setTransitionName("game_card_" + game.id);
        }

        gameHolder.settingsButton.setOnClickListener(v -> {
            if (settingsClickListener != null) {
                settingsClickListener.onGameSettingsClick(game, gameHolder.cardView);
            }
        });

        gameHolder.cardView.setOnClickListener(v -> listener.onGameClick(game, gameHolder.cardView));

        gameHolder.cardView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onGameLongClick(game, gameHolder.cardView);
                return true;
            }
            return false;
        });
    }

    private void setPlaceholderWithInitial(ViewHolder holder, GameEntity game) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(16f);
        int colorIndex = (int) (game.id % PLACEHOLDER_COLORS.length);
        drawable.setColor(PLACEHOLDER_COLORS[colorIndex]);
        holder.placeholderBg.setImageDrawable(drawable);
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
        final FrameLayout placeholderView;
        final ImageView placeholderBg;
        final TextView lastPlayedView;
        final ImageView settingsButton;
        final ImageView playOverlay;
        final TextView placeholderText;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.game_card);
            titleView = itemView.findViewById(R.id.game_title);
            placeholderView = itemView.findViewById(R.id.game_placeholder);
            placeholderBg = itemView.findViewById(R.id.game_placeholder_bg);
            lastPlayedView = itemView.findViewById(R.id.game_last_played);
            settingsButton = itemView.findViewById(R.id.game_settings_button);
            playOverlay = itemView.findViewById(R.id.game_play_overlay);
            placeholderText = itemView.findViewById(R.id.game_placeholder_text);
        }
    }

    static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        final NativeAdView nativeAdView;

        NativeAdViewHolder(@NonNull View itemView) {
            super(itemView);
            nativeAdView = itemView.findViewById(R.id.nativeAdView);
        }

        void bind(NativeAd ad) {
            TextView headline = itemView.findViewById(R.id.adHeadline);
            ImageView icon = itemView.findViewById(R.id.adIcon);
            MaterialButton cta = itemView.findViewById(R.id.adCallToAction);
            TextView advertiser = itemView.findViewById(R.id.advertiser);

            if (headline != null) headline.setText(ad.getHeadline());

            if (ad.getIcon() != null && icon != null) {
                icon.setImageDrawable(ad.getIcon().getDrawable());
                icon.setVisibility(View.VISIBLE);
            } else if (icon != null) {
                icon.setVisibility(View.GONE);
            }

            if (ad.getCallToAction() != null && cta != null) {
                cta.setText(ad.getCallToAction());
                cta.setVisibility(View.VISIBLE);
            } else if (cta != null) {
                cta.setVisibility(View.GONE);
            }

            if (ad.getAdvertiser() != null && advertiser != null) {
                advertiser.setText(ad.getAdvertiser());
                advertiser.setVisibility(View.VISIBLE);
            } else if (advertiser != null) {
                advertiser.setVisibility(View.GONE);
            }

            nativeAdView.setHeadlineView(headline);
            nativeAdView.setIconView(icon);
            nativeAdView.setCallToActionView(cta);
            nativeAdView.setAdvertiserView(advertiser);
            nativeAdView.setNativeAd(ad);
        }
    }
}
