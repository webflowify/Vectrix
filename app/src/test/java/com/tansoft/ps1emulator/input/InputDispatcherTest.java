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

import org.junit.Before;
import org.junit.Test;

public class InputDispatcherTest {

    private InputDispatcher dispatcher;

    @Before
    public void setUp() {
        dispatcher = InputDispatcher.getInstance();
        // Reset state
        dispatcher.setVirtualButtons(0);
        dispatcher.setPhysicalButtons(0);
        dispatcher.setAnalog(0f, 0f);
        dispatcher.setAnalogRight(0f, 0f);
    }

    @Test
    public void getInstance_returnsSameInstance() {
        InputDispatcher a = InputDispatcher.getInstance();
        InputDispatcher b = InputDispatcher.getInstance();
        assertSame(a, b);
    }

    @Test
    public void setVirtualButtons_getVirtualButtons() {
        dispatcher.setVirtualButtons(0xFF);
        assertEquals(0xFF, dispatcher.getVirtualButtons());
    }

    @Test
    public void setPhysicalButtons_getPhysicalButtons() {
        dispatcher.setPhysicalButtons(Ps1Buttons.BUTTON_CROSS);
        assertEquals(Ps1Buttons.BUTTON_CROSS, dispatcher.getPhysicalButtons());
    }

    @Test
    public void setAnalog_getAnalog() {
        dispatcher.setAnalog(0.5f, -0.3f);
        assertEquals(0.5f, dispatcher.getAnalogX(), 0.001f);
        assertEquals(-0.3f, dispatcher.getAnalogY(), 0.001f);
    }

    @Test
    public void setAnalogRight_getAnalogRight() {
        dispatcher.setAnalogRight(0.7f, -0.9f);
        assertEquals(0.7f, dispatcher.getRightAnalogX(), 0.001f);
        assertEquals(-0.9f, dispatcher.getRightAnalogY(), 0.001f);
    }

    @Test
    public void reset_toZero() {
        dispatcher.setVirtualButtons(0xFF);
        dispatcher.setAnalog(1.0f, 1.0f);

        dispatcher.setVirtualButtons(0);
        dispatcher.setAnalog(0f, 0f);

        assertEquals(0, dispatcher.getVirtualButtons());
        assertEquals(0f, dispatcher.getAnalogX(), 0.001f);
        assertEquals(0f, dispatcher.getAnalogY(), 0.001f);
    }

    @Test
    public void defaultState_allZero() {
        InputDispatcher fresh = InputDispatcher.getInstance();
        // After setUp reset, all should be zero
        assertEquals(0, fresh.getVirtualButtons());
        assertEquals(0, fresh.getPhysicalButtons());
        assertEquals(0f, fresh.getAnalogX(), 0.001f);
        assertEquals(0f, fresh.getAnalogY(), 0.001f);
        assertEquals(0f, fresh.getRightAnalogX(), 0.001f);
        assertEquals(0f, fresh.getRightAnalogY(), 0.001f);
    }
}
