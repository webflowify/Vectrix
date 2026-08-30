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

import android.view.KeyEvent;

public class ControllerMapper {

    public static int keyCodeToPs1Button(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:       return Ps1Buttons.BUTTON_CROSS;
            case KeyEvent.KEYCODE_BUTTON_B:       return Ps1Buttons.BUTTON_CIRCLE;
            case KeyEvent.KEYCODE_BUTTON_X:       return Ps1Buttons.BUTTON_SQUARE;
            case KeyEvent.KEYCODE_BUTTON_Y:       return Ps1Buttons.BUTTON_TRIANGLE;
            case KeyEvent.KEYCODE_DPAD_UP:        return Ps1Buttons.BUTTON_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:      return Ps1Buttons.BUTTON_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:      return Ps1Buttons.BUTTON_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:     return Ps1Buttons.BUTTON_RIGHT;
            case KeyEvent.KEYCODE_BUTTON_L1:      return Ps1Buttons.BUTTON_L1;
            case KeyEvent.KEYCODE_BUTTON_R1:      return Ps1Buttons.BUTTON_R1;
            case KeyEvent.KEYCODE_BUTTON_L2:      return Ps1Buttons.BUTTON_L2;
            case KeyEvent.KEYCODE_BUTTON_R2:      return Ps1Buttons.BUTTON_R2;
            case KeyEvent.KEYCODE_BUTTON_START:   return Ps1Buttons.BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_SELECT:  return Ps1Buttons.BUTTON_SELECT;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:  return Ps1Buttons.BUTTON_L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:  return Ps1Buttons.BUTTON_R3;
            default: return 0;
        }
    }

    public static float axisToAnalog(float axisValue) {
        return axisValue; // -1.0 to 1.0
    }

    public static String keyCodeToPs1ButtonName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:       return "CROSS";
            case KeyEvent.KEYCODE_BUTTON_B:       return "CIRCLE";
            case KeyEvent.KEYCODE_BUTTON_X:       return "SQUARE";
            case KeyEvent.KEYCODE_BUTTON_Y:       return "TRIANGLE";
            case KeyEvent.KEYCODE_DPAD_UP:        return "DPAD_UP";
            case KeyEvent.KEYCODE_DPAD_DOWN:      return "DPAD_DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT:      return "DPAD_LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT:     return "DPAD_RIGHT";
            case KeyEvent.KEYCODE_BUTTON_L1:      return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1:      return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2:      return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2:      return "R2";
            case KeyEvent.KEYCODE_BUTTON_START:   return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT:  return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_THUMBL:  return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR:  return "R3";
            default: return null;
        }
    }

    private ControllerMapper() {
        // Utility class
    }
}
