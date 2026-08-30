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
package com.tansoft.ps1emulator.core;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsHelperTest {

    private Context context;
    private SettingsHelper helper;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        helper = new SettingsHelper(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void isAutoRotateEnabled_defaultTrue() {
        assertTrue(helper.isAutoRotateEnabled());
    }

    @Test
    public void isAutoRotateEnabled_disabled() {
        prefs.edit().putBoolean("auto_rotate", false).commit();
        assertFalse(helper.isAutoRotateEnabled());
    }

    @Test
    public void isFpsVisible_defaultFalse() {
        assertFalse(helper.isFpsVisible());
    }

    @Test
    public void isFpsVisible_enabled() {
        prefs.edit().putBoolean("show_fps", true).commit();
        assertTrue(helper.isFpsVisible());
    }

    @Test
    public void isAudioEnabled_defaultTrue() {
        assertTrue(helper.isAudioEnabled());
    }

    @Test
    public void isAudioEnabled_disabled() {
        prefs.edit().putBoolean("audio_enabled", false).commit();
        assertFalse(helper.isAudioEnabled());
    }

    @Test
    public void getAudioBufferSize_default512() {
        assertEquals(512, helper.getAudioBufferSize());
    }

    @Test
    public void getAudioBufferSize_customValue() {
        prefs.edit().putString("audio_buffer_size", "256").commit();
        assertEquals(256, helper.getAudioBufferSize());
    }

    @Test
    public void getAudioBufferSize_invalidString_fallback512() {
        prefs.edit().putString("audio_buffer_size", "not_a_number").commit();
        assertEquals(512, helper.getAudioBufferSize());
    }

    @Test
    public void isVpadVisible_defaultTrue() {
        assertTrue(helper.isVpadVisible());
    }

    @Test
    public void getVpadOpacity_default70() {
        assertEquals(70, helper.getVpadOpacity());
    }

    @Test
    public void getVpadOpacity_customValue() {
        prefs.edit().putInt("vpad_opacity", 50).commit();
        assertEquals(50, helper.getVpadOpacity());
    }

    @Test
    public void getFastForwardSpeed_default2() {
        assertEquals(2, helper.getFastForwardSpeed());
    }

    @Test
    public void getFastForwardSpeed_customValue() {
        prefs.edit().putString("fast_forward_speed", "4").commit();
        assertEquals(4, helper.getFastForwardSpeed());
    }

    @Test
    public void getRewindDepth_default10() {
        assertEquals(10, helper.getRewindDepth());
    }

    @Test
    public void getSlowMotionSpeed_default2() {
        assertEquals(2, helper.getSlowMotionSpeed());
    }

    @Test
    public void getControllerMapping_defaultNeg1() {
        assertEquals(-1, helper.getControllerMapping(0, "CROSS"));
    }

    @Test
    public void getControllerMapping_customValue() {
        prefs.edit().putInt("ctrl_map_0_CROSS", 96).commit();
        assertEquals(96, helper.getControllerMapping(0, "CROSS"));
    }

    @Test
    public void reverseControllerMapping_found() {
        prefs.edit().putInt("ctrl_map_0_CROSS", 96).commit();
        assertEquals("CROSS", helper.reverseControllerMapping(0, 96));
    }

    @Test
    public void reverseControllerMapping_notFound() {
        assertNull(helper.reverseControllerMapping(0, 999));
    }
}
