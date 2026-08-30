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
package com.tansoft.ps1emulator.ui.about;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tansoft.ps1emulator.R;

import java.util.List;

/**
 * RecyclerView adapter for the expandable/collapsible licenses list.
 * Headers and sections are always visible; library items expand on tap.
 */
public class LicenseAdapter extends RecyclerView.Adapter<LicenseAdapter.ViewHolder> {

    private final List<LicenseItem> items;

    public LicenseAdapter(List<LicenseItem> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case LicenseItem.TYPE_HEADER:
                return new ViewHolder(inflater.inflate(R.layout.item_license_header, parent, false));
            case LicenseItem.TYPE_SECTION:
                return new ViewHolder(inflater.inflate(R.layout.item_license_section, parent, false));
            default:
                return new ViewHolder(inflater.inflate(R.layout.item_license_library, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LicenseItem item = items.get(position);

        switch (item.getType()) {
            case LicenseItem.TYPE_HEADER:
                holder.title.setText(item.getName());
                holder.body.setText(item.getFullText());
                holder.body.setVisibility(View.VISIBLE);
                holder.expandIcon.setVisibility(View.GONE);
                break;

            case LicenseItem.TYPE_SECTION:
                holder.title.setText(item.getName());
                holder.body.setText(item.getFullText());
                holder.body.setVisibility(View.VISIBLE);
                holder.expandIcon.setVisibility(View.GONE);
                break;

            case LicenseItem.TYPE_LIBRARY:
                holder.title.setText(item.getName());
                String subtitle = item.getVersion() != null
                        ? item.getVersion() + " \u00b7 " + item.getLicenseType()
                        : item.getLicenseType();
                holder.subtitle.setText(subtitle);
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.body.setText(item.getFullText());
                holder.body.setVisibility(item.isExpanded() ? View.VISIBLE : View.GONE);
                holder.expandIcon.setVisibility(View.VISIBLE);
                holder.expandIcon.setImageResource(
                        item.isExpanded() ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);

                holder.itemView.setOnClickListener(v -> {
                    item.setExpanded(!item.isExpanded());
                    notifyItemChanged(position);
                });
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView body;
        final ImageView expandIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.license_item_title);
            subtitle = itemView.findViewById(R.id.license_item_subtitle);
            body = itemView.findViewById(R.id.license_item_body);
            expandIcon = itemView.findViewById(R.id.license_item_expand_icon);
        }
    }
}
