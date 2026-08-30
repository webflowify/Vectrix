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
package com.tansoft.ps1emulator.cheat;

import java.util.regex.Pattern;

public final class CheatCodeParser {

    private static final Pattern LINE_PATTERN = Pattern.compile("^\\s*([0-9A-Fa-f]{8})\\s+([0-9A-Fa-f]{4})\\s*$");
    private static final int MIN_ADDRESS = 0x10000000;

    private CheatCodeParser() {
    }

    /**
     * Normalize a raw cheat code string: trim whitespace, uppercase hex,
     * collapse multiple whitespace between address and value, join lines
     * with '\n', and strip blank lines.
     *
     * @throws IllegalArgumentException if the input is empty/null or contains invalid codes
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        String[] rawLines = raw.split("\\r?\\n|\\r");
        StringBuilder result = new StringBuilder();
        boolean hasValidLine = false;

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] tokens = trimmed.split("\\s+");
            if (tokens.length != 2) {
                throw new IllegalArgumentException("Each line must have exactly two tokens (address and value): " + trimmed);
            }

            String addressStr = tokens[0].toUpperCase();
            String valueStr = tokens[1].toUpperCase();

            if (addressStr.length() != 8) {
                throw new IllegalArgumentException("Address must be exactly 8 hex digits: " + addressStr);
            }
            if (valueStr.length() != 4) {
                throw new IllegalArgumentException("Value must be exactly 4 hex digits: " + valueStr);
            }

            long address;
            try {
                address = Long.parseLong(addressStr, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex address: " + addressStr);
            }

            try {
                Long.parseLong(valueStr, 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex value: " + valueStr);
            }

            if (address < MIN_ADDRESS) {
                throw new IllegalArgumentException("Address must be >= 0x10000000: " + addressStr);
            }

            if (hasValidLine) {
                result.append('\n');
            }
            result.append(addressStr).append(' ').append(valueStr);
            hasValidLine = true;
        }

        if (!hasValidLine) {
            throw new IllegalArgumentException("Input contains no valid cheat codes");
        }

        return result.toString();
    }

    /**
     * Check whether a raw cheat code string is valid without throwing.
     */
    public static boolean isValid(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
