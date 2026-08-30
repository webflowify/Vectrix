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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Regression test for the production crash
 * {@code com.tansoft.ps1emulator.core.EmulatorService.attachBaseContext java.lang.NoSuchMethodError}
 * (1.0.3 / versionCode 4).
 *
 * <p>{@code Context.createAttributionContext(String)} exists only from API 30, but
 * minSdk is 24, so every game launch on Android 7.0-10 died in
 * {@code attachBaseContext} — before {@code onCreate} ever ran.
 *
 * <p>Runs at minSdk (24), at the last affected API (29) and on a modern API where
 * attribution is genuinely available (33).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 29, 33})
public class EmulatorServiceAttachBaseContextTest {

    private static final String EXPECTED_ATTRIBUTION_TAG = "EmulatorServiceAudio";

    @Test
    public void attachBaseContext_doesNotThrowOnAnySupportedApiLevel() throws Exception {
        EmulatorService service = new EmulatorService();
        Context appContext = ApplicationProvider.getApplicationContext();

        Method attachBaseContext =
                EmulatorService.class.getDeclaredMethod("attachBaseContext", Context.class);
        attachBaseContext.setAccessible(true);

        try {
            attachBaseContext.invoke(service, appContext);
        } catch (InvocationTargetException e) {
            fail("attachBaseContext failed on API " + Build.VERSION.SDK_INT + ": "
                    + e.getTargetException());
        }

        assertNotNull("base context must be attached", service.getBaseContext());
    }

    @Test
    public void attachBaseContext_appliesAttributionTagWhenSupported() throws Exception {
        EmulatorService service = new EmulatorService();
        Context appContext = ApplicationProvider.getApplicationContext();

        Method attachBaseContext =
                EmulatorService.class.getDeclaredMethod("attachBaseContext", Context.class);
        attachBaseContext.setAccessible(true);
        attachBaseContext.invoke(service, appContext);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(EXPECTED_ATTRIBUTION_TAG,
                    service.getBaseContext().getAttributionTag());
        } else {
            assertNotNull(service.getBaseContext());
        }
    }

    @Test
    public void attachBaseContext_toleratesNullBase() throws Exception {
        EmulatorService service = new EmulatorService();

        Method attachBaseContext =
                EmulatorService.class.getDeclaredMethod("attachBaseContext", Context.class);
        attachBaseContext.setAccessible(true);

        try {
            attachBaseContext.invoke(service, (Context) null);
        } catch (InvocationTargetException e) {
            fail("attachBaseContext(null) must not raise NoSuchMethodError/NPE from"
                    + " attribution tagging: " + e.getTargetException());
        }
    }
}
