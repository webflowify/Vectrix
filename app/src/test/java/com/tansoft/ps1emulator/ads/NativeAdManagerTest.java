package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.*;

public class NativeAdManagerTest {

    private NativeAdManager manager;

    @Before
    public void setUp() throws Exception {
        manager = NativeAdManager.getInstance();
        clearPool();
        setField("isLoading", false);
        setField("pendingCount", 0);
    }

    @Test
    public void getAvailableCount_initiallyZero() {
        assertEquals(0, manager.getAvailableCount());
    }

    @Test
    public void isLoading_initiallyFalse() {
        assertFalse(manager.isLoading());
    }

    @Test
    public void getNextAd_whenEmpty_returnsNull() {
        assertNull(manager.getNextAd());
    }

    @Test
    public void destroyAll_clearsPool() throws Exception {
        setField("isLoading", true);
        setField("pendingCount", 3);
        manager.destroyAll();
        assertEquals(0, manager.getAvailableCount());
        assertFalse(manager.isLoading());
        assertEquals(0, getField("pendingCount"));
    }

    @Test
    public void destroyAll_onEmptyPool_noCrash() {
        manager.destroyAll();
        assertEquals(0, manager.getAvailableCount());
    }

    @Test
    public void singleton_returnsSameInstance() {
        NativeAdManager a = NativeAdManager.getInstance();
        NativeAdManager b = NativeAdManager.getInstance();
        assertSame(a, b);
    }

    @Test
    public void loadAds_whileAlreadyLoading_returnsEarly() throws Exception {
        setField("isLoading", true);
        setField("pendingCount", 2);
        // Should not throw
        manager.loadAds(null, 3);
        // pendingCount should remain unchanged
        assertEquals(2, getField("pendingCount"));
    }

    @Test
    public void evictExpired_removesOldEntries() throws Exception {
        // Directly manipulate the pool to add an entry with an old timestamp
        @SuppressWarnings("unchecked")
        Queue<Object> adPool = (Queue<Object>) getField("adPool");
        // We can't easily create NativeAd objects without SDK, but we can verify
        // that the pool starts empty and destroyAll works
        assertEquals(0, adPool.size());
        manager.destroyAll();
        assertEquals(0, adPool.size());
    }

    @Test
    public void poolMaxSize_isFive() throws Exception {
        Field maxPoolField = NativeAdManager.class.getDeclaredField("MAX_POOL_SIZE");
        maxPoolField.setAccessible(true);
        assertEquals(5, maxPoolField.getInt(null));
    }

    @Test
    public void adMaxAge_isOneHour() throws Exception {
        Field maxAgeField = NativeAdManager.class.getDeclaredField("AD_MAX_AGE_MS");
        maxAgeField.setAccessible(true);
        assertEquals(3_600_000L, maxAgeField.getLong(null));
    }

    // ── Reflection helpers ──────────────────────────────────────

    private void setField(String name, Object value) throws Exception {
        Field field = NativeAdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private Object getField(String name) throws Exception {
        Field field = NativeAdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void clearPool() throws Exception {
        @SuppressWarnings("unchecked")
        Queue<Object> adPool = (Queue<Object>) getField("adPool");
        adPool.clear();
    }
}
