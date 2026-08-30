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

import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class InputManagerHelper {

    public static boolean isGameController(InputDevice device) {
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    public static List<InputDevice> getConnectedControllers() {
        List<InputDevice> controllers = new ArrayList<>();
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && isGameController(device)) {
                controllers.add(device);
            }
        }
        return controllers;
    }

    public static String describeController(InputDevice device) {
        StringBuilder sb = new StringBuilder();
        sb.append(device.getName());
        sb.append(" [id=").append(device.getId()).append("]");
        sb.append(" sources=0x").append(Integer.toHexString(device.getSources()));

        int[] axes = { MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                       MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                       MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
                       MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y };
        for (int axis : axes) {
            InputDevice.MotionRange range = device.getMotionRange(axis);
            if (range != null) {
                sb.append(" axis=").append(axis);
                sb.append("[").append(range.getMin()).append(",").append(range.getMax()).append("]");
            }
        }
        return sb.toString();
    }
}
