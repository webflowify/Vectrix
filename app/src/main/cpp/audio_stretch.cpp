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

#include "audio_stretch.h"
#include <algorithm>
#include <cmath>

#ifdef AUDIO_USE_SOUNDTOUCH
using soundtouch::SoundTouch;
#endif

namespace {

// Effective stretch rate is clamped so SoundTouch stays in its well-behaved
// range. Beyond these bounds audio is simply quieter / less stretched but
// still pitch-correct. 0.5x .. 8x covers FF multipliers up to 8 and slow-mo
// to 2x.
constexpr float kMinRate = 0.5f;
constexpr float kMaxRate = 8.0f;

// Largest single pump from the source ring into SoundTouch per pop() call.
// Bounds latency and worst-case processing time in the audio callback.
constexpr size_t kMaxPumpSamples = 8192;

inline float i16ToFloat(int16_t s) { return static_cast<float>(s) * (1.0f / 32768.0f); }
inline int16_t floatToI16(float f) {
    int v = static_cast<int>(f * 32768.0f);
    if (v > 32767) v = 32767;
    else if (v < -32768) v = -32768;
    return static_cast<int16_t>(v);
}

}  // namespace

AudioStretch::AudioStretch() = default;

void AudioStretch::init(int sampleRate, int channels) {
    sampleRate_ = sampleRate;
    channels_   = channels;
#ifdef AUDIO_USE_SOUNDTOUCH
    st_.setSampleRate(sampleRate);
    st_.setChannels(channels);
    st_.setTempo(1.0f);   // duration only — pitch preserved
    st_.setRate(1.0f);
    st_.setPitch(1.0f);
    st_.clear();
    scratch_.assign(16384, 0.0f);  // ~372 ms @44.1k stereo headroom
    pumpTmp_.clear();
    pendingRateChange_.store(false);
    pendingStClear_.store(false);
#endif
}

void AudioStretch::setMode(StretchMode m) {
    if (m != mode_.load()) {
        mode_.store(m);
        clear();
    }
}

void AudioStretch::setRate(float rate) {
    float r = rate;
    if (r < kMinRate) r = kMinRate;
    if (r > kMaxRate) r = kMaxRate;
    if (r == rate_) return;
    rate_ = r;
    // Defer the SoundTouch mutation to the audio thread (see popTimeStretch);
    // setRate may be called from the JNI/emulation thread while the audio
    // callback is concurrently inside SoundTouch.
#ifdef AUDIO_USE_SOUNDTOUCH
    pendingRateChange_.store(true);
#endif
}

void AudioStretch::clear() {
    // st_ is touched only on the audio thread; defer its reset. The ring
    // buffers are lock-free, so clearing them here is safe from any thread.
#ifdef AUDIO_USE_SOUNDTOUCH
    pendingStClear_.store(true);
    srcBuf_.clear();
#endif
    pbuf_.clear();
}

void AudioStretch::push(const int16_t* in, size_t frames) {
    switch (mode_.load()) {
        case StretchMode::Mute:
            return;  // discard produced audio
        case StretchMode::TimeStretch:
#ifdef AUDIO_USE_SOUNDTOUCH
            pushTimeStretch(in, frames);
#else
            pushPassthrough(in, frames);
#endif
            break;
        case StretchMode::Off:
        default:
            pushPassthrough(in, frames);
            break;
    }
}

size_t AudioStretch::pop(int16_t* out, size_t frames) {
    switch (mode_.load()) {
        case StretchMode::Mute:
            return 0;
        case StretchMode::TimeStretch:
#ifdef AUDIO_USE_SOUNDTOUCH
            return popTimeStretch(out, frames);
#else
            return popPassthrough(out, frames);
#endif
        case StretchMode::Off:
        default:
            return popPassthrough(out, frames);
    }
}

// --- Passthrough ---------------------------------------------------------------

void AudioStretch::pushPassthrough(const int16_t* in, size_t frames) {
    size_t samples = frames * channels_;
    if (!pbuf_.push(in, samples)) {
        // Full: drop oldest samples so the newest audio is never lost.
        size_t used = pbuf_.available();
        size_t over = samples - (pbuf_.capacity() - used);
        if (over > 0) pbuf_.discard(over);
        pbuf_.push(in, samples);
    }
}

size_t AudioStretch::popPassthrough(int16_t* out, size_t frames) {
    size_t samples = pbuf_.pop(out, frames * channels_);
    return samples / channels_;
}

// --- Time-stretch (SoundTouch) -------------------------------------------------
#ifdef AUDIO_USE_SOUNDTOUCH

void AudioStretch::pushTimeStretch(const int16_t* in, size_t frames) {
    size_t samples = frames * channels_;
    size_t used = srcBuf_.available();
    size_t space = srcBuf_.capacity() - used;
    if (samples > space) {
        // Overflow (producer outpaced consumer): drop oldest so we never grow
        // without bound and never write past capacity. Steady-state FF/slow-mo
        // is rate-matched, so this is a transient-burst safeguard.
        srcBuf_.discard(samples - space);
    }
    srcBuf_.push(in, samples);
}

size_t AudioStretch::popTimeStretch(int16_t* out, size_t frames) {
    const size_t ch = channels_;
    const size_t wantSamples = frames * ch;

    // Apply any deferred SoundTouch mutations (set from other threads).
    if (pendingStClear_.load()) {
        st_.clear();
        srcBuf_.clear();
        pendingStClear_.store(false);
    }
    if (pendingRateChange_.load()) {
        // SoundTouch's tempo == emulation speed: >1 faster (duration compressed),
        // <1 slower (duration stretched), pitch untouched. Clear drops any
        // partially-stretched tail (mirrors DuckStation's EmptyStretchBuffers()).
        st_.setTempo(rate_);
        st_.clear();
        srcBuf_.clear();
        pendingRateChange_.store(false);
        pendingStClear_.store(false);
    }

    // Pump queued source samples into SoundTouch (audio thread only).
    size_t avail = srcBuf_.available();
    size_t toPump = (avail > kMaxPumpSamples) ? kMaxPumpSamples : avail;
    if (toPump > 0) {
        pumpTmp_.resize(toPump);
        srcBuf_.pop(pumpTmp_.data(), toPump);
        if (scratch_.size() < toPump) scratch_.resize(toPump);
        for (size_t i = 0; i < toPump; i++) {
            scratch_[i] = i16ToFloat(pumpTmp_[i]);
        }
        st_.putSamples(scratch_.data(), static_cast<uint>(toPump));
    }

    uint got = st_.receiveSamples(scratch_.data(), static_cast<uint>(wantSamples));
    if (got > wantSamples) got = static_cast<uint>(wantSamples);
    for (uint i = 0; i < got; i++) {
        out[i] = floatToI16(scratch_[i]);
    }
    return got / static_cast<uint>(ch);
}

#else  // !AUDIO_USE_SOUNDTOUCH — fallback (should not be reached; TimeStretch
       // routes to passthrough when SoundTouch is absent).
void AudioStretch::pushTimeStretch(const int16_t*, size_t) {}
size_t AudioStretch::popTimeStretch(int16_t*, size_t frames) { return 0; }
#endif

void AudioStretch::prefillSilence(size_t frames) {
    // TimeStretch: SoundTouch generates from fed input, so there is nothing to
    // pre-buffer; the Oboe callback zero-fills the brief startup gap.
    if (mode_.load() == StretchMode::Off) {
        std::vector<int16_t> z(frames * channels_, 0);
        pushPassthrough(z.data(), frames);
    }
}

size_t AudioStretch::pendingFrames() const {
#ifdef AUDIO_USE_SOUNDTOUCH
    if (mode_.load() == StretchMode::TimeStretch) {
        return st_.numSamples() / channels_;
    }
#endif
    return pbuf_.available() / channels_;
}
