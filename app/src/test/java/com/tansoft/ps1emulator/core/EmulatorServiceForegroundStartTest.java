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

import android.app.Service;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Regression test for the production crash
 * {@code com.tansoft.ps1emulator.core.EmulatorService.onCreate
 * android.app.ForegroundServiceStartNotAllowedException}
 * (1.0.3 / versionCode 4).
 *
 * <p>Returning {@link Service#START_STICKY} let the system re-create the
 * service from the background after a process death; its {@code onCreate()}
 * then called {@code startForeground()} while backgrounded and threw
 * {@code ForegroundServiceStartNotAllowedException} on API 31+. The service is
 * meaningless without the foreground {@code EmulationActivity}, so it must be
 * {@link Service#START_NOT_STICKY} — the user simply relaunches from the
 * activity, which (being foreground) may start the FGS legally.
 *
 * <p>Exercised at the last affected API (31) and on a modern API (33).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 31, 33})
public class EmulatorServiceForegroundStartTest {

    @Test
    public void onStartCommand_returnsNotSticky() {
        EmulatorService service = Robolectric
                .buildService(EmulatorService.class)
                .get();

        int result = service.onStartCommand(
                new Intent(ApplicationProvider.getApplicationContext(),
                        EmulatorService.class),
                0,
                1);

        assertEquals(Service.START_NOT_STICKY, result);
    }
}
