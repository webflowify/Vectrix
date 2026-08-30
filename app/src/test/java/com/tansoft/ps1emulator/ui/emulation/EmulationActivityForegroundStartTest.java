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
package com.tansoft.ps1emulator.ui.emulation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 31, 33})

/**
 * Regression test for the production crash
 * {@code com.tansoft.ps1emulator.ui.emulation.EmulationActivity.startEmulationService
 * android.app.ForegroundServiceStartNotAllowedException}
 * (1.0.3 / versionCode 4).
 *
 * <p>The activity used to call {@code startForegroundService()} from
 * {@code onCreate()} without catching the rejection. On API 31+ a cold-start
 * race (or an API 34+ background launch of a {@code specialUse} FGS) makes the
 * system reject the start and throw {@code ForegroundServiceStartNotAllowedException},
 * which propagated and crashed the process. The fix defers and retries from a
 * foreground callback; this test locks in the detection logic that distinguishes
 * a start-not-allowed rejection from any other failure.
 */
public class EmulationActivityForegroundStartTest {

    @Test
    public void securityException_isDetected() {
        assertTrue(EmulationActivity.isForegroundServiceNotAllowed(new SecurityException("denied")));
    }

    @Test
    public void genericRuntimeException_isNotDetected() {
        assertFalse(EmulationActivity.isForegroundServiceNotAllowed(new RuntimeException("boom")));
    }

    @Test
    public void nestedGenericException_isNotDetected() {
        RuntimeException outer = new RuntimeException("wrap", new IllegalStateException("inner"));
        assertFalse(EmulationActivity.isForegroundServiceNotAllowed(outer));
    }

    @Test
    public void foregroundServiceStartNotAllowed_isDetected() {
        Throwable fgs = newForegroundServiceStartNotAllowedException();
        if (fgs == null) {
            // Class unavailable on this test runtime (API < 31); the name-match
            // branch cannot be exercised here, but the logic is verifier-safe.
            return;
        }
        assertTrue(EmulationActivity.isForegroundServiceNotAllowed(fgs));

        // Also detect when wrapped as the cause of another exception.
        assertTrue(EmulationActivity.isForegroundServiceNotAllowed(
                new RuntimeException("wrap", fgs)));
    }

    /** Builds a real {@code ForegroundServiceStartNotAllowedException} if present. */
    private static Throwable newForegroundServiceStartNotAllowedException() {
        try {
            Class<?> clazz = Class.forName("android.app.ForegroundServiceStartNotAllowedException");
            Constructor<?> ctor = clazz.getConstructor(String.class);
            return (Throwable) ctor.newInstance("not allowed");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
