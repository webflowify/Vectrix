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

import static org.junit.Assert.*;

import org.junit.Test;

public class ObjectPoolTest {

    private static class TestObject {
        final int value;
        TestObject(int value) { this.value = value; }
    }

    @Test
    public void acquire_newPool_returnsNewInstance() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(4) {
            @Override protected TestObject createInstance() { return new TestObject(0); }
        };
        TestObject obj = pool.acquire();
        assertNotNull(obj);
        assertEquals(0, obj.value);
    }

    @Test
    public void acquire_afterRelease_returnsSameInstance() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(4) {
            @Override protected TestObject createInstance() { return new TestObject(42); }
        };
        TestObject first = pool.acquire();
        pool.release(first);
        TestObject second = pool.acquire();
        assertSame(first, second);
    }

    @Test
    public void acquire_poolExhausted_createsNew() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(2) {
            @Override protected TestObject createInstance() { return new TestObject(1); }
        };
        pool.acquire();
        pool.acquire();
        TestObject third = pool.acquire(); // pool full, should create new
        assertNotNull(third);
    }

    @Test
    public void release_poolFull_ignored() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(1) {
            @Override protected TestObject createInstance() { return new TestObject(0); }
        };
        TestObject a = pool.acquire();
        pool.release(a);
        TestObject b = pool.acquire(); // takes a back
        pool.release(b);
        pool.release(a); // pool already has b, this should be ignored (no error)
        assertNotNull(pool.acquire()); // should return b
    }

    @Test
    public void release_multipleThenAcquire_allReturned() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(3) {
            private int counter = 0;
            @Override protected TestObject createInstance() { return new TestObject(counter++); }
        };
        TestObject a = pool.acquire();
        TestObject b = pool.acquire();
        TestObject c = pool.acquire();
        pool.release(a);
        pool.release(b);
        pool.release(c);

        // All three should be returned in LIFO order
        assertSame(c, pool.acquire());
        assertSame(b, pool.acquire());
        assertSame(a, pool.acquire());
    }

    @Test
    public void poolSize_zero_acquireAlwaysCreatesNew() {
        ObjectPool<TestObject> pool = new ObjectPool<TestObject>(0) {
            private int counter = 0;
            @Override protected TestObject createInstance() { return new TestObject(counter++); }
        };
        TestObject a = pool.acquire();
        TestObject b = pool.acquire();
        assertNotSame(a, b);
    }
}
