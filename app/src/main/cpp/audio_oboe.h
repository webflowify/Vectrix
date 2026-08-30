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
#pragma once

#include <cstdint>
#include "ring_buffer.h"

// Global ring buffer shared between emulation thread (producer)
// and Oboe audio callback (consumer).
// 32768 samples = 16384 stereo frames at 44.1 kHz ˜ 371 ms buffer.
// Large enough to absorb scheduling jitter and frame time variations.
extern RingBuffer<int16_t, 32768> audioRingBuffer;

// --- Output stream lifecycle --------------------------------------------------
//
// The Oboe output stream is owned exclusively by audio_oboe.cpp: there is never
// more than one stream, no stream pointer ever leaves the module, and every
// entry point below is serialised by an internal mutex.
//
// Callers must drive the lifecycle from the emulation thread only. Oboe closes
// streams on its own internal thread when the audio device disappears
// (headset/Bluetooth/USB unplug, output switch, audio server restart), so the
// mutex is the second line of defence rather than a licence to call these from
// several threads.

// Open, prefill and start the output stream, replacing any existing one.
// Returns false if the device refused to give us a stream; in that case no
// stream is left behind and the emulator simply runs without audio.
bool audioStreamOpen(int32_t sampleRate = 44100,
                     int32_t channelCount = 2,
                     int32_t framesPerCallback = 512);

// Stop, close and release the stream. Idempotent, safe with no stream open.
void audioStreamClose();

// True (once) if the system closed our stream and it should be reopened.
// Consuming the request clears it, so a failed reopen is not retried forever.
bool audioStreamTakeRestartRequest();
