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

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Verifies the joystick (D-pad replacement) mapping: a normalized stick vector
 * must produce the correct analog-sign convention (right/down positive, matching
 * the PS1 left-stick axis) and an emulated D-pad bitmask within a radial
 * dead-zone so the stick also works for games that only read the D-pad.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class JoystickMappingTest {

    private VirtualGamepadView newLaidOutView(int w, int h) {
        VirtualGamepadView view = new VirtualGamepadView(ApplicationProvider.getApplicationContext(), null);
        view.layout(0, 0, w, h);
        return view;
    }

    @Test
    public void centreIsNeutral() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        assertEquals(0, view.computeJoystickDpadBits(0f, 0f));
    }

    @Test
    public void pureDirectionsMapToCorrectDpadBits() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        assertEquals(Ps1Buttons.BUTTON_UP, view.computeJoystickDpadBits(0f, -0.8f));
        assertEquals(Ps1Buttons.BUTTON_DOWN, view.computeJoystickDpadBits(0f, 0.8f));
        assertEquals(Ps1Buttons.BUTTON_LEFT, view.computeJoystickDpadBits(-0.8f, 0f));
        assertEquals(Ps1Buttons.BUTTON_RIGHT, view.computeJoystickDpadBits(0.8f, 0f));
    }

    @Test
    public void diagonalsCombineTwoDirectionBits() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        assertEquals(Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT,
                view.computeJoystickDpadBits(0.8f, -0.8f));
        assertEquals(Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT,
                view.computeJoystickDpadBits(-0.8f, 0.8f));
    }

    @Test
    public void withinDeadZoneIsNeutral() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        // Just below the 0.35 dead-zone threshold.
        assertEquals(0, view.computeJoystickDpadBits(0f, 0.2f));
        assertEquals(0, view.computeJoystickDpadBits(0.3f, 0f));
    }
}
