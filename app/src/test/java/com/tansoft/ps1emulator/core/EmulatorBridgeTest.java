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

import org.junit.Test;

import java.nio.ByteBuffer;

public class EmulatorBridgeTest {

    @Test
    public void framebufferDimensions_matchMaxPsxGeometry() {
        // The PS1 can output up to 1024x512; the framebuffer is sized for the
        // maximum so hi-res modes are displayed rather than cropped.
        assertEquals(1024, EmulatorBridge.FB_WIDTH);
        assertEquals(512, EmulatorBridge.FB_HEIGHT);
    }

    @Test
    public void swapFramebuffers_publishesBackBufferToFront() {
        EmulatorBridge.allocateFramebufferBuffer(16);
        ByteBuffer back = EmulatorBridge.getFramebufferBuffer();
        back.rewind();
        back.put((byte) 0x7F);
        back.rewind();

        EmulatorBridge.swapFramebuffers(4, 1);

        // The back buffer must now be a different instance than the one we wrote.
        assertNotSame(back, EmulatorBridge.getFramebufferBuffer());

        final int[] seen = new int[3];
        EmulatorBridge.withFrontFramebuffer((buf, w, h) -> {
            seen[0] = buf.get(0);
            seen[1] = w;
            seen[2] = h;
        });
        assertEquals(0x7F, seen[0]);
        assertEquals(4, seen[1]);
        assertEquals(1, seen[2]);
    }

    @Test
    public void withFrontFramebuffer_skipsConsumerWhenNoFramePublished() {
        EmulatorBridge.allocateFramebufferBuffer(16);
        EmulatorBridge.swapFramebuffers(0, 0);

        final boolean[] called = {false};
        EmulatorBridge.withFrontFramebuffer((buf, w, h) -> called[0] = true);
        assertFalse(called[0]);
    }

    @Test
    public void allocateFramebufferBuffer_createsBuffer() {
        EmulatorBridge.allocateFramebufferBuffer(EmulatorBridge.FB_WIDTH * EmulatorBridge.FB_HEIGHT * 4);
        ByteBuffer buf = EmulatorBridge.getFramebufferBuffer();
        assertNotNull(buf);
        assertTrue(buf.isDirect());
        assertEquals(EmulatorBridge.FB_WIDTH * EmulatorBridge.FB_HEIGHT * 4, buf.capacity());
    }

    @Test
    public void allocateAudioBuffer_createsBuffer() {
        EmulatorBridge.allocateAudioBuffer(1024 * 2);
        ByteBuffer buf = EmulatorBridge.getAudioBuffer();
        assertNotNull(buf);
        assertTrue(buf.isDirect());
        assertEquals(1024 * 2, buf.capacity());
    }

    @Test
    public void clearFramebufferBuffer_zeroesBuffer() {
        EmulatorBridge.allocateFramebufferBuffer(16);
        ByteBuffer buf = EmulatorBridge.getFramebufferBuffer();
        // Put some non-zero data
        for (int i = 0; i < 16; i++) {
            buf.put((byte) 0xFF);
        }
        buf.rewind();

        EmulatorBridge.clearFramebufferBuffer();

        buf.rewind();
        for (int i = 0; i < 16; i++) {
            assertEquals(0, buf.get());
        }
    }

    @Test
    public void clearFramebufferBuffer_preservesPositionAndLimit() {
        EmulatorBridge.allocateFramebufferBuffer(32);
        ByteBuffer buf = EmulatorBridge.getFramebufferBuffer();
        buf.position(5);
        buf.limit(20);

        EmulatorBridge.clearFramebufferBuffer();

        assertEquals(5, buf.position());
        assertEquals(20, buf.limit());
    }

    @Test
    public void getResolutionScale_clampsToValidRange() {
        float scale = EmulatorBridge.getResolutionScale();
        assertTrue(scale >= 0.5f);
        assertTrue(scale <= 1.0f);
    }

    @Test
    public void constructor_isPrivate() throws Exception {
        java.lang.reflect.Constructor<?> ctor = EmulatorBridge.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        try {
            ctor.newInstance();
            fail("Expected exception from private constructor");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Expected - constructor throws AssertionError
        }
    }
}
