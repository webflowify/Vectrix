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

#include <atomic>
#include <cstdint>
#include <cstring>
#include <type_traits>

template<typename T, size_t Capacity>
class RingBuffer {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be a power of 2");
    static_assert(std::is_trivially_copyable<T>::value, "T must be trivially copyable");

    static constexpr size_t Mask = Capacity - 1;

    T buffer_[Capacity];
    std::atomic<size_t> head_{0};  // write index (producer)
    std::atomic<size_t> tail_{0};  // read index (consumer)

public:
    RingBuffer() = default;

    // Push count items from data. Returns false if not enough space.
    bool push(const T* data, size_t count) {
        size_t head = head_.load(std::memory_order_relaxed);
        size_t tail = tail_.load(std::memory_order_acquire);
        size_t used = (head - tail) & Mask;
        size_t free = Capacity - used;
        if (count > free) {
            return false;
        }
        for (size_t i = 0; i < count; ++i) {
            buffer_[(head + i) & Mask] = data[i];
        }
        head_.store((head + count) & Mask, std::memory_order_release);
        return true;
    }

    // Pop up to count items into data. Returns number of items actually read.
    size_t pop(T* data, size_t count) {
        size_t head = head_.load(std::memory_order_acquire);
        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t used = (head - tail) & Mask;
        size_t toRead = (count < used) ? count : used;
        for (size_t i = 0; i < toRead; ++i) {
            data[i] = buffer_[(tail + i) & Mask];
        }
        tail_.store((tail + toRead) & Mask, std::memory_order_release);
        return toRead;
    }

    // Number of samples currently available for reading.
    size_t available() const {
        size_t head = head_.load(std::memory_order_acquire);
        size_t tail = tail_.load(std::memory_order_acquire);
        return (head - tail) & Mask;
    }

    // Total capacity in items.
    constexpr size_t capacity() const { return Capacity; }

    // Clear the buffer (reset head and tail).
    // Must only be called when no concurrent push/pop is in progress.
    void clear() {
        tail_.store(0, std::memory_order_release);
        head_.store(0, std::memory_order_release);
    }

    // Discard up to count items, advancing the read cursor without copying.
    size_t discard(size_t count) {
        size_t head = head_.load(std::memory_order_acquire);
        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t used = (head - tail) & Mask;
        size_t toDiscard = (count < used) ? count : used;
        tail_.store((tail + toDiscard) & Mask, std::memory_order_release);
        return toDiscard;
    }
};
