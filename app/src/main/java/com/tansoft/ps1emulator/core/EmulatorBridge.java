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

import java.nio.ByteBuffer;

public class EmulatorBridge {
    // Maximum PS1 output geometry, and therefore the framebuffer/texture size.
    // The console can output up to 1024x512; sizing these at 320x240 crops
    // hi-res modes (512x240, 640x480) rather than displaying them.
    // The actual per-frame geometry is reported by nativeEmulateFrame().
    public static final int FB_WIDTH = 1024;
    public static final int FB_HEIGHT = 512;

    /** Written by the emulation thread. */
    private static ByteBuffer framebufferBuffer;
    /** Read by the GL thread. Swapped with the back buffer once per frame. */
    private static ByteBuffer framebufferFront;
    /** Emulator output geometry of the frame currently in the front buffer. */
    private static int frontWidth;
    private static int frontHeight;
    private static final Object FB_LOCK = new Object();

    private static ByteBuffer audioBuffer;

    /** True once the native emulation core has been successfully loaded. */
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("ps1emulatorcore");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            // The native core is only bundled in the on-device APK. JVM unit
            // tests run without it; the pure-Java buffer management below still
            // works, and any native call would fail loudly (correctly) only if
            // invoked outside a device build.
        }
        // Buffers are allocated lazily on first access (see getFramebufferBuffer /
        // getAudioBuffer) to avoid a ~4 MB upfront allocation during class
        // loading, which can OOM on low-memory devices.
    }

    // Core lifecycle
    public static native int nativeInit(String biosPath, String card1Path, String card2Path);
    public static native int nativeLoadGame(String romPath);
    public static native int nativeShutdown();

    // Audio
    public static native void nativeSetAudioBufferSize(int frames);
    public static native void nativeSetAudioEnabled(boolean enabled);
    public static native void nativeClearAudioRingBuffer();

    // Save states
    public static native int nativeSaveState(String path);
    public static native int nativeLoadState(String path);

    // Rendering
    public static native float nativeGetResolutionScale();
    public static native float nativeGetFps();

    // Libretro core options. Must be set before nativeInit() to take effect at
    // startup; later calls are picked up by the core on its next option refresh.
    // Keys/values are those of PCSX-ReARMed, e.g.
    // nativeSetCoreOption("pcsx_rearmed_gpu_thread_rendering", "enabled").
    public static native boolean nativeSetCoreOption(String key, String value);

    // Settings propagation
    public static native void nativeSetFastForwardSpeed(int multiplier);
    public static native int nativeGetFastForwardSpeed();
    public static native void nativeSetFastForwardEnabled(boolean enabled);

    // Slow motion
    public static native void nativeSetSlowMotionEnabled(boolean enabled);
    public static native void nativeSetSlowMotionDivisor(int divisor);
    public static native int nativeGetSlowMotionDivisor();

    // Rewind
    public static native void nativeSetRewindEnabled(boolean enabled);
    public static native void nativeSetRewindInterval(int frames);
    public static native boolean nativeRewindStep();
    public static native int nativeGetRewindBufferCount();

    /**
     * Batched emulation frame (P08-M06): input -> run -> framebuffer + audio.
     *
     * @return the geometry of the frame just written, packed as
     *         {@code (width << 16) | height}, or 0 if the core duped the frame
     *         (nothing changed) — in which case the previous image should be
     *         left on screen: no swap, no GL upload.
     */
    public static native int nativeEmulateFrame(
            int buttons, float analogX, float analogY,
            float rightAnalogX, float rightAnalogY,
            java.nio.Buffer framebufferBuffer, java.nio.Buffer audioBuffer);

    private static final int[] BUFFER_SIZES = {128, 256, 512, 1024};
    private static int sCurrentAudioBufferSize = 0;

    public static void setAudioBufferSize(int frames) {
        int clamped = Math.max(128, Math.min(1024, frames));
        int nearest = 256;
        for (int size : BUFFER_SIZES) {
            if (Math.abs(size - clamped) < Math.abs(nearest - clamped)) {
                nearest = size;
            }
        }
        if (nearest == sCurrentAudioBufferSize) {
            return;
        }
        sCurrentAudioBufferSize = nearest;
        nativeSetAudioBufferSize(nearest);
    }

    public static void allocateFramebufferBuffer(int size) {
        synchronized (FB_LOCK) {
            framebufferBuffer = ByteBuffer.allocateDirect(size);
            framebufferFront = ByteBuffer.allocateDirect(size);
        }
    }

    /**
     * The back buffer — the one the emulation thread hands to native code.
     * Its identity changes on every {@link #swapFramebuffers(int, int)}, so
     * callers must re-fetch it each frame rather than caching the reference.
     *
     * <p>Allocated lazily on first call to avoid a ~4 MB upfront allocation
     * during class loading, which can OOM on low-memory devices.
     */
    public static ByteBuffer getFramebufferBuffer() {
        if (framebufferBuffer == null) {
            allocateFramebufferBuffer(FB_WIDTH * FB_HEIGHT * 4);
        }
        return framebufferBuffer;
    }

    /**
     * Publishes the just-rendered back buffer to the GL thread.
     *
     * <p>Without this the emulation thread and the GL thread share a single
     * buffer: the emulator writes frame N+1 while the renderer is still
     * uploading frame N, which shows up as tearing and judder even at a solid
     * 60 fps. Call once per frame, after native emulation returns.
     */
    public static void swapFramebuffers(int width, int height) {
        synchronized (FB_LOCK) {
            ByteBuffer tmp = framebufferFront;
            framebufferFront = framebufferBuffer;
            framebufferBuffer = tmp;
            frontWidth = width;
            frontHeight = height;
        }
    }

    /**
     * Runs {@code consumer} against the front buffer while holding the swap lock,
     * guaranteeing the emulation thread cannot swap it out mid-upload.
     *
     * <p>The width/height passed to the consumer are the emulator's real output
     * geometry for that frame, which changes at runtime (320x240, 512x240,
     * 640x480, ...).
     */
    public static void withFrontFramebuffer(FramebufferConsumer consumer) {
        synchronized (FB_LOCK) {
            if (framebufferFront != null && frontWidth > 0 && frontHeight > 0) {
                framebufferFront.rewind();
                consumer.accept(framebufferFront, frontWidth, frontHeight);
            }
        }
    }

    @FunctionalInterface
    public interface FramebufferConsumer {
        void accept(ByteBuffer buffer, int width, int height);
    }

    /**
     * Clear both framebuffers to black.
     * Must be called when starting a new emulation session to prevent
     * stale data from a previous session from showing through.
     *
     * <p>Allocates the framebuffers if they haven't been created yet.
     */
    public static void clearFramebufferBuffer() {
        synchronized (FB_LOCK) {
            if (framebufferBuffer == null) {
                allocateFramebufferBuffer(FB_WIDTH * FB_HEIGHT * 4);
            }
            zeroBuffer(framebufferBuffer);
            zeroBuffer(framebufferFront);
        }
    }

    private static void zeroBuffer(ByteBuffer buf) {
        if (buf == null) return;
        int savedLimit = buf.limit();
        int savedPosition = buf.position();
        // rewind() only resets position, not limit; set the limit to capacity so
        // we can zero the whole buffer regardless of the caller's view window.
        buf.position(0);
        buf.limit(buf.capacity());
        byte[] zeros = new byte[buf.capacity()];
        buf.put(zeros);
        buf.position(savedPosition);
        buf.limit(savedLimit);
    }

    public static void allocateAudioBuffer(int size) {
        audioBuffer = ByteBuffer.allocateDirect(size);
    }

    /**
     * Returns the audio buffer, allocating it lazily on first access.
     * This avoids a 4 KB allocation during class loading, keeping the
     * EmulatorBridge class lightweight until emulation actually starts.
     */
    public static ByteBuffer getAudioBuffer() {
        if (audioBuffer == null) {
            allocateAudioBuffer(1024 * 2); // 1024 stereo frames = 2048 samples = 4096 bytes
        }
        return audioBuffer;
    }

    public static float getResolutionScale() {
        // When the native core isn't available (e.g. JVM unit tests) report the
        // identity scale rather than invoking a native method that would throw.
        float scale = nativeLoaded ? nativeGetResolutionScale() : 1.0f;
        // Clamp to valid range on Java side as a safety measure
        return Math.max(0.5f, Math.min(1.0f, scale));
    }

    private EmulatorBridge() {
        // Utility class — no instantiation
        throw new AssertionError("No instances");
    }
}
