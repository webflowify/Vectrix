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
 * Verifies the on-screen D-pad now resolves a touch to the nearest one or two
 * orthogonal directions (8-way), including the four diagonals, while staying
 * compatible with the PS1 bitmask (diagonals == two combined direction bits,
 * the same convention used by EmulationActivity's hat-axis handling).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DpadEightDirectionTest {

    private VirtualGamepadView newLaidOutView(int w, int h) {
        VirtualGamepadView view = new VirtualGamepadView(ApplicationProvider.getApplicationContext(), null);
        view.layout(0, 0, w, h);
        return view;
    }

    @Test
    public void centreIsNeutralDeadZone() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        int cx = view.getDpadCenterX();
        int cy = view.getDpadCenterY();
        assertEquals(0, view.dpadMask(cx, cy));
    }

    @Test
    public void pureUpLeftDownRightStillWork() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        int cx = view.getDpadCenterX();
        int cy = view.getDpadCenterY();
        int r = view.getDpadRadius();

        assertEquals(Ps1Buttons.BUTTON_UP, view.dpadMask(cx, cy - r));
        assertEquals(Ps1Buttons.BUTTON_DOWN, view.dpadMask(cx, cy + r));
        assertEquals(Ps1Buttons.BUTTON_LEFT, view.dpadMask(cx - r, cy));
        assertEquals(Ps1Buttons.BUTTON_RIGHT, view.dpadMask(cx + r, cy));
    }

    @Test
    public void diagonalsAreTwoCombinedDirectionBits() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        int cx = view.getDpadCenterX();
        int cy = view.getDpadCenterY();
        int r = view.getDpadRadius();
        // A point inside the disc (magnitude < r) at a 45-degree diagonal angle.
        int off = (int) (r * 0.7f);

        // up-right (screen: -y, +x)
        assertEquals(Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT,
                view.dpadMask(cx + off, cy - off));
        // up-left
        assertEquals(Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_LEFT,
                view.dpadMask(cx - off, cy - off));
        // down-right
        assertEquals(Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_RIGHT,
                view.dpadMask(cx + off, cy + off));
        // down-left
        assertEquals(Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT,
                view.dpadMask(cx - off, cy + off));
    }

    @Test
    public void touchOutsideDiscIsNotDpad() {
        VirtualGamepadView view = newLaidOutView(1000, 1000);
        int cx = view.getDpadCenterX();
        int cy = view.getDpadCenterY();
        int r = view.getDpadRadius();
        // Well beyond the disc radius along the diagonal.
        assertEquals(0, view.dpadMask(cx + 2 * r, cy - 2 * r));
    }
}
