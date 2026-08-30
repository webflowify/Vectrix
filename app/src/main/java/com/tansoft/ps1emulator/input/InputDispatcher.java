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
package com.tansoft.ps1emulator.input;

public class InputDispatcher {

    private static InputDispatcher instance;
    private volatile int virtualButtons = 0;
    private volatile int physicalButtons = 0;
    private volatile float analogX = 0f;
    private volatile float analogY = 0f;
    private volatile float rightAnalogX = 0f;
    private volatile float rightAnalogY = 0f;

    private InputDispatcher() {}

    public static synchronized InputDispatcher getInstance() {
        if (instance == null) {
            instance = new InputDispatcher();
        }
        return instance;
    }

    public void setVirtualButtons(int bitmask) {
        this.virtualButtons = bitmask;
        flush();
    }

    public void setPhysicalButtons(int bitmask) {
        this.physicalButtons = bitmask;
        flush();
    }

    public void setAnalog(float x, float y) {
        this.analogX = x;
        this.analogY = y;
        flush();
    }

    public void setAnalogRight(float x, float y) {
        this.rightAnalogX = x;
        this.rightAnalogY = y;
        flush();
    }

    private void flush() {
        // Input is now passed via batched nativeEmulateFrame in EmulatorService
        // No longer calling EmulatorBridge.nativeSetInput directly
    }

    public int getPhysicalButtons() {
        return physicalButtons;
    }

    public int getVirtualButtons() {
        return virtualButtons;
    }

    public float getAnalogX() {
        return analogX;
    }

    public float getAnalogY() {
        return analogY;
    }

    public float getRightAnalogX() {
        return rightAnalogX;
    }

    public float getRightAnalogY() {
        return rightAnalogY;
    }
}
