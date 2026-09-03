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
#include <cstring>
#include <cstdlib>
#include <atomic>
#include <mutex>

#include <zlib.h>

#include "debug_log.h"
#include "save_state.h"
#include "freeze_helpers.h"

// --- Rewind snapshot: CoreState + extra state section -----------------------
// Bundles the main CoreState with the v5 extra state (SIO, CDROM, counters,
// MDEC, dynarec, pad) into a single contiguous block for efficient compression.
struct RewindSnapshot {
    CoreState core;
    uint32_t extraSize;
    uint8_t extraData[EXTRA_STATE_BUF_SIZE];
};

static constexpr size_t REWIND_SNAPSHOT_RAW_SIZE =
    sizeof(CoreState) + 4 + EXTRA_STATE_BUF_SIZE;

// --- Rewind Buffer -----------------------------------------------------------
// Pre-allocated ring buffer of raw (uncompressed) emulator state snapshots.
// Capture is a cheap memcpy into a pre-allocated slot — no malloc, no compress.
// This keeps the hot path fast enough to not impact frame pacing.
//
// Memory: each slot is ~4.2MB raw. With 20 slots = ~84MB total.
// 20 snapshots at 7.5/sec = ~2.7 seconds of rewind (configurable).

static constexpr int REWIND_DEFAULT_MAX_SNAPSHOTS = 20;
static constexpr int REWIND_DEFAULT_SNAPSHOT_INTERVAL = 8;

class RewindBuffer {
public:
    RewindBuffer() = default;
    ~RewindBuffer() { deinit(); }

    RewindBuffer(const RewindBuffer&) = delete;
    RewindBuffer& operator=(const RewindBuffer&) = delete;

    bool init(int maxSnapshots = REWIND_DEFAULT_MAX_SNAPSHOTS) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (initialized_) deinit_locked();

        if (maxSnapshots < 2) maxSnapshots = 2;
        if (maxSnapshots > 100) maxSnapshots = 100;

        maxSnapshots_ = maxSnapshots;

        // Pre-allocate all snapshot slots upfront
        slots_ = new (std::nothrow) RewindSlot[maxSnapshots];
        if (!slots_) {
            LOGE("RewindBuffer: failed to allocate %d slots", maxSnapshots);
            return false;
        }
        for (int i = 0; i < maxSnapshots; i++) {
            slots_[i].data = (uint8_t*)malloc(REWIND_SNAPSHOT_RAW_SIZE);
            slots_[i].valid = false;
            if (!slots_[i].data) {
                LOGE("RewindBuffer: failed to allocate slot %d", i);
                // Clean up already allocated
                for (int j = 0; j < i; j++) free(slots_[j].data);
                delete[] slots_;
                slots_ = nullptr;
                return false;
            }
        }

        head_ = 0;
        count_ = 0;
        initialized_ = true;
        LOGI("RewindBuffer: initialized with %d slots (%zu bytes each, ~%zuMB total)",
             maxSnapshots, REWIND_SNAPSHOT_RAW_SIZE,
             (maxSnapshots * REWIND_SNAPSHOT_RAW_SIZE) / (1024 * 1024));
        return true;
    }

    void deinit() {
        std::lock_guard<std::mutex> lock(mutex_);
        deinit_locked();
    }

    // Get a pointer to the next writable slot (no copy yet).
    // Caller fills it, then calls commit() to advance the ring.
    // This avoids a separate memcpy — capture writes directly into the slot.
    uint8_t* nextSlot() {
        if (!initialized_) return nullptr;
        return slots_[head_ % maxSnapshots_].data;
    }

    // Mark the current slot as valid and advance head.
    void commit() {
        if (!initialized_) return;
        slots_[head_ % maxSnapshots_].valid = true;
        head_ = (head_ + 1) % maxSnapshots_;
        if (count_.load(std::memory_order_relaxed) < maxSnapshots_)
            count_.store(count_.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
    }

    // Get a pointer to the most recent valid slot (for pop/peek).
    uint8_t* topSlot() {
        if (!initialized_ || count_ == 0) return nullptr;
        int idx = (head_ - 1 + maxSnapshots_) % maxSnapshots_;
        if (!slots_[idx].valid) return nullptr;
        return slots_[idx].data;
    }

    // Pop: invalidate the most recent slot and move head back.
    void pop() {
        if (!initialized_ || count_.load(std::memory_order_relaxed) == 0) return;
        int idx = (head_ - 1 + maxSnapshots_) % maxSnapshots_;
        slots_[idx].valid = false;
        head_ = idx;
        count_.store(count_.load(std::memory_order_relaxed) - 1, std::memory_order_relaxed);
    }

    void clear() {
        if (!initialized_) return;
        for (int i = 0; i < maxSnapshots_; i++) {
            slots_[i].valid = false;
        }
        head_ = 0;
        count_.store(0, std::memory_order_relaxed);
    }

    int count() const { return count_.load(std::memory_order_relaxed); }
    int capacity() const { return maxSnapshots_; }
    bool empty() const { return count_.load(std::memory_order_relaxed) == 0; }
    bool full() const { return count_.load(std::memory_order_relaxed) >= maxSnapshots_; }

private:
    void deinit_locked() {
        if (!initialized_) return;
        for (int i = 0; i < maxSnapshots_; i++) {
            if (slots_[i].data) {
                free(slots_[i].data);
                slots_[i].data = nullptr;
            }
            slots_[i].valid = false;
        }
        delete[] slots_;
        slots_ = nullptr;
        maxSnapshots_ = 0;
        head_ = 0;
        count_.store(0, std::memory_order_relaxed);
        initialized_ = false;
        LOGI("RewindBuffer: deinitialized");
    }

    struct RewindSlot {
        uint8_t* data;   // pre-allocated raw snapshot buffer
        bool valid;       // whether this slot contains a valid snapshot
    };

    std::mutex mutex_;
    RewindSlot* slots_ = nullptr;
    int maxSnapshots_ = 0;
    int head_ = 0;
    std::atomic<int> count_{0};
    bool initialized_ = false;
};

static RewindBuffer gRewindBuffer;
