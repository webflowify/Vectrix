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

import static org.junit.Assert.*;

import android.view.KeyEvent;

import org.junit.Test;

public class ControllerMapperTest {

    @Test
    public void keyCodeToPs1Button_cross() {
        assertEquals(Ps1Buttons.BUTTON_CROSS, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void keyCodeToPs1Button_circle() {
        assertEquals(Ps1Buttons.BUTTON_CIRCLE, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_B));
    }

    @Test
    public void keyCodeToPs1Button_square() {
        assertEquals(Ps1Buttons.BUTTON_SQUARE, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_X));
    }

    @Test
    public void keyCodeToPs1Button_triangle() {
        assertEquals(Ps1Buttons.BUTTON_TRIANGLE, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_Y));
    }

    @Test
    public void keyCodeToPs1Button_dpad() {
        assertEquals(Ps1Buttons.BUTTON_UP, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(Ps1Buttons.BUTTON_DOWN, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(Ps1Buttons.BUTTON_LEFT, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(Ps1Buttons.BUTTON_RIGHT, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_DPAD_RIGHT));
    }

    @Test
    public void keyCodeToPs1Button_shoulders() {
        assertEquals(Ps1Buttons.BUTTON_L1, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_L1));
        assertEquals(Ps1Buttons.BUTTON_R1, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_R1));
        assertEquals(Ps1Buttons.BUTTON_L2, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_L2));
        assertEquals(Ps1Buttons.BUTTON_R2, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_R2));
    }

    @Test
    public void keyCodeToPs1Button_meta() {
        assertEquals(Ps1Buttons.BUTTON_START, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_START));
        assertEquals(Ps1Buttons.BUTTON_SELECT, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_SELECT));
        assertEquals(Ps1Buttons.BUTTON_L3, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_THUMBL));
        assertEquals(Ps1Buttons.BUTTON_R3, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_BUTTON_THUMBR));
    }

    @Test
    public void keyCodeToPs1Button_unknown_returnsZero() {
        assertEquals(0, ControllerMapper.keyCodeToPs1Button(KeyEvent.KEYCODE_VOLUME_UP));
    }

    @Test
    public void axisToAnalog_passthrough() {
        assertEquals(0.5f, ControllerMapper.axisToAnalog(0.5f), 0.001f);
        assertEquals(-1.0f, ControllerMapper.axisToAnalog(-1.0f), 0.001f);
        assertEquals(0.0f, ControllerMapper.axisToAnalog(0.0f), 0.001f);
    }

    @Test
    public void keyCodeToPs1ButtonName_allNames() {
        assertEquals("CROSS", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_A));
        assertEquals("CIRCLE", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_B));
        assertEquals("SQUARE", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_X));
        assertEquals("TRIANGLE", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_Y));
        assertEquals("DPAD_UP", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals("DPAD_DOWN", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals("DPAD_LEFT", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals("DPAD_RIGHT", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals("L1", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_L1));
        assertEquals("R1", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_R1));
        assertEquals("L2", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_L2));
        assertEquals("R2", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_R2));
        assertEquals("START", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_START));
        assertEquals("SELECT", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_SELECT));
        assertEquals("L3", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_THUMBL));
        assertEquals("R3", ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_BUTTON_THUMBR));
    }

    @Test
    public void keyCodeToPs1ButtonName_unknown_returnsNull() {
        assertNull(ControllerMapper.keyCodeToPs1ButtonName(KeyEvent.KEYCODE_VOLUME_UP));
    }
}
