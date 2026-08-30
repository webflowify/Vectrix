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

#include "audio_oboe.h"
#include "debug_log.h"
#include "profiler_trace.h"

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <time.h>

// Global ring buffer shared between emulation thread (producer)
// and Oboe audio callback (consumer).
// 32768 samples = 16384 stereo frames at 44.1 kHz ˜ 371 ms buffer.
RingBuffer<int16_t, 32768> audioRingBuffer;

// Audio mute flag — set from Java via nativeSetAudioEnabled().
// When false, the Oboe callback outputs silence instead of ring buffer data.
extern std::atomic<bool> gAudioEnabled;

// Slow-motion state — when enabled, audio samples are stretched to match
// the reduced frame rate (lower pitch, classic slow-mo effect).
extern std::atomic<bool> sSlowMotionEnabled;
extern std::atomic<int> sSlowMotionDivisor;

static int64_t monotonicNanos() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000 + ts.tv_nsec;
}

// --- Stream state -------------------------------------------------------------
//
// sStream is the single owner of the live stream and may only be touched while
// holding sStreamLock. A shared_ptr (not a raw pointer) is mandatory here:
// AudioStreamBuilder::openStream(shared_ptr&) stores a weak_ptr inside the
// stream, which Oboe locks for the duration of its data/error callbacks. That
// is what keeps the object alive if the audio device disconnects at the same
// moment we replace or close the stream. With the deprecated raw-pointer
// overload Oboe has nothing to lock and hands the app a pointer that its own
// disconnect thread may be stopping, closing and abandoning concurrently.
static std::mutex sStreamLock;
static std::shared_ptr<oboe::AudioStream> sStream;

// The previously owned stream, kept alive one open cycle longer than it is used.
// Oboe's disconnect thread can still be running a stream's error callback while
// we replace that stream, and when the device cannot play 44.1 kHz natively Oboe
// wraps the hardware stream in a FilterAudioStream — in that case the shared_ptr
// Oboe's error thread holds covers the inner stream only, not the wrapper it
// calls back through. Retiring instead of destroying means such a callback can
// never be pulled out from under Oboe: the object is released only when the next
// stream is retired, which is a whole user action later. At most one closed
// stream shell is alive at a time.
static std::shared_ptr<oboe::AudioStream> sRetiredStream;

// Identity of the stream the callbacks currently belong to. Deliberately a raw
// value that is NEVER dereferenced: it exists only so a late callback arriving
// from a stream we have already replaced can be recognised and ignored.
static std::atomic<oboe::AudioStream*> sLiveStreamId{nullptr};

// Set from Oboe's error thread once the system has closed our stream; consumed
// by the emulation thread, the only thread allowed to open streams.
static std::atomic<bool> sRestartRequested{false};
static std::atomic<int64_t> sStreamOpenedAt{0};
static std::atomic<int> sErrorRestarts{0};

// Give up reopening after this many closures in quick succession, so a device
// that keeps rejecting the stream cannot turn into a reopen loop.
static const int kMaxErrorRestarts = 5;
// A stream that survived this long is considered healthy: the next closure
// starts counting from zero again.
static const int64_t kHealthyStreamNanos = 5'000'000'000;

// Silence pushed into the ring buffer before the stream starts, so the first
// callbacks have something to read while the emulator produces its first frame.
static const size_t kMaxPrefillSamples = 16384;
static int16_t sSilence[kMaxPrefillSamples];  // zero-initialised, never written

// Queue a reopen if the closed stream is still the one we own.
// Called from Oboe's error thread — it must not take sStreamLock: that thread
// can be inside AudioStream::close() holding Oboe's internal lock while the
// emulation thread holds sStreamLock and waits for that very lock, which would
// deadlock both. Atomics only here.
static void requestStreamRestart(oboe::AudioStream* stream, oboe::Result error) {
    if (stream != sLiveStreamId.load(std::memory_order_acquire)) {
        LOGW("Audio: ignoring error %d from a stream we no longer own",
             static_cast<int>(error));
        return;
    }

    if (monotonicNanos() - sStreamOpenedAt.load(std::memory_order_relaxed)
            > kHealthyStreamNanos) {
        sErrorRestarts.store(0, std::memory_order_relaxed);
    }

    int attempt = sErrorRestarts.fetch_add(1, std::memory_order_relaxed) + 1;
    if (attempt > kMaxErrorRestarts) {
        LOGE("Audio: stream closed by the system (error %d) — not reopening after "
             "%d attempts", static_cast<int>(error), kMaxErrorRestarts);
        return;
    }

    sRestartRequested.store(true, std::memory_order_release);
    LOGW("Audio: stream closed by the system (error %d) — reopen queued (attempt %d)",
         static_cast<int>(error), attempt);
}

class EmuAudioCallback : public oboe::AudioStreamCallback {
private:
    int consecutiveUnderruns_{0};
    int64_t lastUnderrunLogTime_{0};
    std::atomic<int64_t> startTime_{0};

public:
    EmuAudioCallback() {
        startTime_.store(monotonicNanos());
    }

    // Called (under sStreamLock) each time a stream is opened, so the underrun
    // log suppression window starts fresh for the new stream.
    void resetTiming() {
        startTime_.store(monotonicNanos(), std::memory_order_relaxed);
        consecutiveUnderruns_ = 0;
        lastUnderrunLogTime_ = 0;
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames) override {
        ScopedTrace _trace("Oboe: onAudioReady");

        int32_t numSamples = numFrames * stream->getChannelCount();
        auto* output = static_cast<int16_t*>(audioData);

        // If audio is disabled, output silence and drain the ring buffer
        if (!gAudioEnabled) {
            memset(output, 0, numSamples * sizeof(int16_t));
            // Drain stale samples from ring buffer to prevent buildup
            audioRingBuffer.discard(audioRingBuffer.available());
            return oboe::DataCallbackResult::Continue;
        }

        // Slow-motion audio stretching: read fewer samples from ring buffer
        // and duplicate each sample to fill the output. This produces the
        // classic pitch-shifted slow-mo audio effect without underruns.
        if (sSlowMotionEnabled.load()) {
            int divisor = sSlowMotionDivisor.load();
            if (divisor > 1) {
                // Read 1/divisor of the output samples from ring buffer
                int32_t inputSamples = numSamples / divisor;
                if (inputSamples < 2) inputSamples = 2; // minimum 1 stereo frame

                // Use stack buffer for small reads, static for larger ones
                static int16_t smTempBuf[8192];
                int32_t clampedInput = (inputSamples > 8192) ? 8192 : inputSamples;
                size_t read = audioRingBuffer.pop(smTempBuf, clampedInput);

                // Stretch: duplicate each stereo sample pair
                int outPos = 0;
                for (size_t i = 0; i + 1 < read && outPos < numSamples; i += 2) {
                    int16_t l = smTempBuf[i];
                    int16_t r = smTempBuf[i + 1];
                    for (int d = 0; d < divisor && outPos + 1 < numSamples; d++) {
                        output[outPos++] = l;
                        output[outPos++] = r;
                    }
                }
                // Fill remaining with silence
                if (outPos < numSamples) {
                    memset(output + outPos, 0, (numSamples - outPos) * sizeof(int16_t));
                }
                return oboe::DataCallbackResult::Continue;
            }
        }

        size_t read = audioRingBuffer.pop(output, numSamples);

        if (read < static_cast<size_t>(numSamples)) {
            // Fill remaining with silence
            memset(output + read, 0, (numSamples - read) * sizeof(int16_t));

            consecutiveUnderruns_++;

            // Rate-limit underrun logging to once per second.
            // Suppress logging entirely for the first 2 seconds after stream creation
            // to avoid noise during the startup fill window.
            int64_t now = monotonicNanos();
            if (now - startTime_.load(std::memory_order_relaxed) > 2'000'000'000 &&
                now - lastUnderrunLogTime_ > 1'000'000'000) {
                LOGW("Audio underrun: %d consecutive (%zu of %d frames filled)",
                     consecutiveUnderruns_, read, numSamples);
                lastUnderrunLogTime_ = now;
            }
        } else {
            consecutiveUnderruns_ = 0;
        }

        return oboe::DataCallbackResult::Continue;
    }

    // Returning false lets Oboe stop and close the disconnected stream on its
    // own thread; onErrorAfterClose() then queues the reopen. Reopening here
    // would mean opening a stream from Oboe's callback thread, which is exactly
    // the cross-thread lifecycle this module exists to prevent.
    bool onError(oboe::AudioStream* /* stream */, oboe::Result /* error */) override {
        return false;
    }

    void onErrorAfterClose(oboe::AudioStream* stream,
                           oboe::Result error) override {
        LOGE("Oboe stream error: %d", static_cast<int>(error));
        consecutiveUnderruns_ = 0;
        requestStreamRestart(stream, error);
    }
};

// Global callback instance. It must outlive every stream, so it is a global
// rather than something owned by the stream lifecycle.
static EmuAudioCallback sAudioCallback;

// Amount of silence to preload, sized from the device's real burst instead of a
// fixed guess: prefill is a permanent latency floor (in steady state the buffer
// never drains below it), so half the ring buffer would defeat the LowLatency
// mode the stream is opened with. 50 ms floor.
static size_t prefillSamples(int32_t burstFrames, int32_t sampleRate, int32_t channelCount) {
    if (sampleRate <= 0) sampleRate = 44100;
    if (channelCount <= 0) channelCount = 2;

    size_t frames = static_cast<size_t>(sampleRate) / 20;  // 50 ms floor
    if (burstFrames > 0) {
        size_t fromBurst = static_cast<size_t>(burstFrames) * 4;
        if (fromBurst > frames) frames = fromBurst;
    }

    size_t samples = frames * static_cast<size_t>(channelCount);
    size_t maxSamples = audioRingBuffer.capacity() / 2;
    if (samples > maxSamples) samples = maxSamples;
    if (samples > kMaxPrefillSamples) samples = kMaxPrefillSamples;
    return samples & ~static_cast<size_t>(1);  // keep stereo-aligned
}

// Stop, close and release the owned stream. Caller must hold sStreamLock.
static void closeStreamLocked() {
    std::shared_ptr<oboe::AudioStream> stream = std::move(sStream);
    sLiveStreamId.store(nullptr, std::memory_order_release);
    if (!stream) return;

    // requestStop() rather than stop(): stop() waits up to 2 s for a state
    // change, which stalls the caller when the device has already gone away.
    // close() stops the stream again internally before tearing it down.
    stream->requestStop();
    stream->close();

    // Hand the closed stream to the retirement slot, which releases whatever was
    // retired before it. Nothing is ever deleted while a callback could still be
    // running inside it.
    sRetiredStream = std::move(stream);
}

bool audioStreamOpen(int32_t sampleRate, int32_t channelCount, int32_t framesPerCallback) {
    std::lock_guard<std::mutex> lock(sStreamLock);

    closeStreamLocked();

    // Any queued reopen is satisfied by this open. Cleared after
    // closeStreamLocked() has cleared sLiveStreamId, so a disconnect callback
    // that was in flight for the stream we just dropped cannot re-arm it.
    sRestartRequested.store(false, std::memory_order_relaxed);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(channelCount)
           ->setSampleRate(sampleRate)
           ->setFramesPerCallback(framesPerCallback)
           ->setDataCallback(&sAudioCallback)
           ->setErrorCallback(&sAudioCallback);

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK || stream == nullptr) {
        LOGE("Failed to open Oboe stream: %d", static_cast<int>(result));
        return false;
    }

    // Size the stream buffer from the device's actual burst size. Two bursts is
    // Oboe's recommended starting point: enough slack to survive a late callback
    // without adding the tens of milliseconds of lag a fixed oversized buffer
    // would impose. Without this the stream keeps its default capacity, which
    // works against the LowLatency performance mode requested above.
    int32_t burst = stream->getFramesPerBurst();
    if (burst > 0) {
        int32_t target = burst * 2;
        // The buffer must hold at least a couple of callbacks, otherwise every
        // callback underruns.
        if (framesPerCallback > 0 && target < framesPerCallback * 2) {
            target = framesPerCallback * 2;
        }
        oboe::ResultWithValue<int32_t> set = stream->setBufferSizeInFrames(target);
        LOGI("Oboe stream: burst=%d, bufferSize=%d (requested %d), sampleRate=%d",
             burst, set ? set.value() : stream->getBufferSizeInFrames(), target,
             stream->getSampleRate());
    }

    // Prefill before start() so the very first callbacks are not underruns.
    audioRingBuffer.clear();
    size_t prefill = prefillSamples(burst, sampleRate, channelCount);
    audioRingBuffer.push(sSilence, prefill);

    sAudioCallback.resetTiming();
    sStreamOpenedAt.store(monotonicNanos(), std::memory_order_relaxed);
    sLiveStreamId.store(stream.get(), std::memory_order_release);
    sStream = stream;

    // requestStart() rather than start(): start() blocks for up to 2 s waiting
    // for the state transition, and this runs on the emulation thread. A stream
    // that starts but never delivers callbacks shows up as underruns or as an
    // error callback, so the confirmation buys nothing here.
    oboe::Result startResult = stream->requestStart();
    if (startResult != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %d", static_cast<int>(startResult));
        closeStreamLocked();
        return false;
    }

    LOGI("Oboe audio stream started (prefill=%zu samples ˜ %zu ms, framesPerCallback=%d)",
         prefill, (prefill / 2) * 1000 / (sampleRate > 0 ? sampleRate : 44100),
         framesPerCallback);
    return true;
}

void audioStreamClose() {
    std::lock_guard<std::mutex> lock(sStreamLock);
    sRestartRequested.store(false, std::memory_order_relaxed);
    sErrorRestarts.store(0, std::memory_order_relaxed);
    closeStreamLocked();
    // Safe now: close() has stopped the callbacks, so there is no concurrent
    // consumer of the ring buffer.
    audioRingBuffer.clear();
}

bool audioStreamTakeRestartRequest() {
    // Called once per frame: keep the common case a plain load and only pay for
    // the read-modify-write when there is actually something to consume.
    if (!sRestartRequested.load(std::memory_order_relaxed)) return false;
    return sRestartRequested.exchange(false, std::memory_order_acq_rel);
}
