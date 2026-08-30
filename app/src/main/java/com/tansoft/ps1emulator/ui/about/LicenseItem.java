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

/**
 * A single library entry for the expandable licenses list.
 */
public class LicenseItem {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_LIBRARY = 1;
    public static final int TYPE_SECTION = 2;

    private final int type;
    private final String name;
    private final String version;
    private final String licenseType;
    private final String fullText;
    private boolean expanded;

    private LicenseItem(int type, String name, String version, String licenseType, String fullText) {
        this.type = type;
        this.name = name;
        this.version = version;
        this.licenseType = licenseType;
        this.fullText = fullText;
        this.expanded = false;
    }

    public static LicenseItem header(String name, String fullText) {
        return new LicenseItem(TYPE_HEADER, name, null, null, fullText);
    }

    public static LicenseItem library(String name, String version, String licenseType, String fullText) {
        return new LicenseItem(TYPE_LIBRARY, name, version, licenseType, fullText);
    }

    public static LicenseItem section(String name, String fullText) {
        return new LicenseItem(TYPE_SECTION, name, null, null, fullText);
    }

    public int getType() { return type; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getLicenseType() { return licenseType; }
    public String getFullText() { return fullText; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
}
