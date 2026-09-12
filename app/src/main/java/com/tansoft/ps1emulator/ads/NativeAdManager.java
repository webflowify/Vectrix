package com.tansoft.ps1emulator.ads;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * Manages loading and caching of native ads for the library grid.
 *
 * <p>Ads are preloaded into an internal pool. The grid adapter calls
 * {@link #getNextAd()} to consume one ad at a time. Ads that are not
 * consumed within {@link #AD_MAX_AGE_MS} are evicted automatically.
 *
 * <p>Thread-safe: all public methods are synchronized.
 */
public class NativeAdManager {

    private static final String TAG = "NativeAdManager";

    /** Maximum age of a cached ad before it is evicted (1 hour). */
    private static final long AD_MAX_AGE_MS = 3_600_000;

    /** Maximum number of ads to keep in the pool. */
    private static final int MAX_POOL_SIZE = 5;

    private static NativeAdManager instance;

    /** Pool of loaded ads, oldest first. */
    private final Queue<NativeAdEntry> adPool = new ArrayDeque<>();

    private AdLoader adLoader;
    private boolean isLoading = false;
    private int pendingCount = 0;

    private NativeAdManager() {}

    public static synchronized NativeAdManager getInstance() {
        if (instance == null) {
            instance = new NativeAdManager();
        }
        return instance;
    }

    /**
     * Preload native ads into the pool.
     *
     * @param context Application or activity context.
     * @param count   Number of ads to request (clamped to {@link #MAX_POOL_SIZE}).
     */
    public synchronized void loadAds(@NonNull Context context, int count) {
        if (!AdsConfig.ENABLE_ADS) return;
        if (isLoading) return;

        evictExpired();

        int needed = Math.min(count, MAX_POOL_SIZE) - adPool.size();
        if (needed <= 0) return;

        isLoading = true;
        pendingCount = needed;

        adLoader = new AdLoader.Builder(context, AdsConfig.getAdUnitNative())
            .forNativeAd(nativeAd -> {
                synchronized (NativeAdManager.this) {
                    if (adPool.size() < MAX_POOL_SIZE) {
                        adPool.add(new NativeAdEntry(nativeAd));
                    } else {
                        nativeAd.destroy();
                    }
                    pendingCount--;
                    if (pendingCount <= 0) {
                        isLoading = false;
                    }
                }
            })
            .withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull com.google.android.gms.ads.LoadAdError error) {
                    synchronized (NativeAdManager.this) {
                        pendingCount--;
                        if (pendingCount <= 0) {
                            isLoading = false;
                        }
                    }
                    Log.w(TAG, "Native ad failed to load: " + error.getMessage());
                }
            })
            .withNativeAdOptions(new NativeAdOptions.Builder().build())
            .build();

        adLoader.loadAds(new AdRequest.Builder().build(), needed);
    }

    /**
     * Return the next available native ad, or {@code null} if none are ready.
     * The caller is responsible for calling {@link NativeAd#destroy()} when done.
     */
    @Nullable
    public synchronized NativeAd getNextAd() {
        evictExpired();
        NativeAdEntry entry = adPool.poll();
        if (entry == null) return null;
        return entry.ad;
    }

    /**
     * Peek at the number of ads currently available (without consuming).
     */
    public synchronized int getAvailableCount() {
        evictExpired();
        return adPool.size();
    }

    /**
     * Returns {@code true} if an ad load request is in flight.
     */
    public synchronized boolean isLoading() {
        return isLoading;
    }

    /**
     * Destroy all cached ads and release resources.
     * Call this from the activity/fragment onDestroy to prevent leaks.
     */
    public synchronized void destroyAll() {
        for (NativeAdEntry entry : adPool) {
            entry.ad.destroy();
        }
        adPool.clear();
        isLoading = false;
        pendingCount = 0;
    }

    // ── Internals ──────────────────────────────────────────────

    /** Remove ads older than {@link #AD_MAX_AGE_MS}. */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<NativeAdEntry> it = adPool.iterator();
        while (it.hasNext()) {
            NativeAdEntry entry = it.next();
            if (now - entry.loadedAt > AD_MAX_AGE_MS) {
                entry.ad.destroy();
                it.remove();
            }
        }
    }

    /** Wrapper that tracks when an ad was loaded. */
    private static class NativeAdEntry {
        final NativeAd ad;
        final long loadedAt;

        NativeAdEntry(NativeAd ad) {
            this.ad = ad;
            this.loadedAt = System.currentTimeMillis();
        }
    }
}
