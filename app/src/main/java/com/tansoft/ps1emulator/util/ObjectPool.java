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
package com.tansoft.ps1emulator.util;

public abstract class ObjectPool<T> {
    private final T[] pool;
    private int index = 0;

    @SuppressWarnings("unchecked")
    public ObjectPool(int maxSize) {
        pool = (T[]) new Object[maxSize];
    }

    protected abstract T createInstance();

    public T acquire() {
        if (index > 0) {
            return pool[--index];
        }
        return createInstance();
    }

    public void release(T instance) {
        if (index < pool.length) {
            pool[index++] = instance;
        }
    }
}
