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

import org.junit.Test;

public class Ps1ButtonsTest {

    @Test
    public void allButtons_areUniquePowersOfTwo() {
        int[] buttons = {
            Ps1Buttons.BUTTON_SELECT, Ps1Buttons.BUTTON_L3, Ps1Buttons.BUTTON_R3,
            Ps1Buttons.BUTTON_START, Ps1Buttons.BUTTON_UP, Ps1Buttons.BUTTON_RIGHT,
            Ps1Buttons.BUTTON_DOWN, Ps1Buttons.BUTTON_LEFT, Ps1Buttons.BUTTON_L2,
            Ps1Buttons.BUTTON_R2, Ps1Buttons.BUTTON_L1, Ps1Buttons.BUTTON_R1,
            Ps1Buttons.BUTTON_TRIANGLE, Ps1Buttons.BUTTON_CIRCLE,
            Ps1Buttons.BUTTON_CROSS, Ps1Buttons.BUTTON_SQUARE
        };

        for (int i = 0; i < buttons.length; i++) {
            assertEquals("Button " + i + " should be power of 2", 1, Integer.bitCount(buttons[i]));
            for (int j = i + 1; j < buttons.length; j++) {
                assertNotEquals("Buttons " + i + " and " + j + " should differ",
                        buttons[i], buttons[j]);
            }
        }
    }

    @Test
    public void bitmask_combination() {
        int combined = Ps1Buttons.BUTTON_CROSS | Ps1Buttons.BUTTON_CIRCLE;
        assertEquals(Ps1Buttons.BUTTON_CROSS, combined & Ps1Buttons.BUTTON_CROSS);
        assertEquals(Ps1Buttons.BUTTON_CIRCLE, combined & Ps1Buttons.BUTTON_CIRCLE);
        assertEquals(0, combined & Ps1Buttons.BUTTON_SQUARE);
    }

    @Test
    public void allButtons_fitIn16Bits() {
        int allButtons = Ps1Buttons.BUTTON_SELECT | Ps1Buttons.BUTTON_L3 | Ps1Buttons.BUTTON_R3
            | Ps1Buttons.BUTTON_START | Ps1Buttons.BUTTON_UP | Ps1Buttons.BUTTON_RIGHT
            | Ps1Buttons.BUTTON_DOWN | Ps1Buttons.BUTTON_LEFT | Ps1Buttons.BUTTON_L2
            | Ps1Buttons.BUTTON_R2 | Ps1Buttons.BUTTON_L1 | Ps1Buttons.BUTTON_R1
            | Ps1Buttons.BUTTON_TRIANGLE | Ps1Buttons.BUTTON_CIRCLE
            | Ps1Buttons.BUTTON_CROSS | Ps1Buttons.BUTTON_SQUARE;
        assertTrue(allButtons <= 0xFFFF);
        assertEquals(0xFFFF, allButtons);
    }
}
