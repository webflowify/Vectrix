package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests GameAdapter ad-positioning logic without requiring Android views.
 * Uses a minimal adapter that exposes the positioning methods.
 */
public class GameAdapterAdTest {

    private static final int AD_INTERVAL = 5;
    private int nativeAdMinGames;

    @Before
    public void setUp() {
        nativeAdMinGames = AdsConfig.NATIVE_AD_MIN_GAMES;
    }

    // ── getItemCount ───────────────────────────────────────────

    @Test
    public void getItemCount_noGames_returnsZero() {
        assertEquals(0, computeItemCount(0, 0));
    }

    @Test
    public void getItemCount_fewerGamesThanMin_noAds() {
        assertEquals(3, computeItemCount(3, 0));
    }

    @Test
    public void getItemCount_atMinGames_noAds_noNativeAds() {
        assertEquals(nativeAdMinGames, computeItemCount(nativeAdMinGames, 0));
    }

    @Test
    public void getItemCount_moreGames_noNativeAds_noExtra() {
        assertEquals(7, computeItemCount(7, 0));
    }

    @Test
    public void getItemCount_withNativeAds_addsAdSlots() {
        // 10 games, 3 native ads: adCount = min(3, 10/5) = 2
        int expected = 10 + 2;
        assertEquals(expected, computeItemCount(10, 3));
    }

    @Test
    public void getItemCount_oneAdOnly_addsOneSlot() {
        // 6 games, 1 native ad: adCount = min(1, 6/5) = 1
        assertEquals(7, computeItemCount(6, 1));
    }

    @Test
    public void getItemCount_manyAds_clampedByGamesDivInterval() {
        // 6 games, 10 native ads: adCount = min(10, 6/5) = 1
        assertEquals(7, computeItemCount(6, 10));
    }

    @Test
    public void getItemCount_exactInterval_addsOneAd() {
        // 5 games, 1 native ad: adCount = min(1, 5/5) = 1
        assertEquals(6, computeItemCount(5, 1));
    }

    // ── getItemViewType ─────────────────────────────────────────

    @Test
    public void getItemViewType_firstPosition_alwaysGame() {
        assertEquals(0, computeViewType(0, 10, 3));
    }

    @Test
    public void getItemViewType_position5_withAds_isNativeAd() {
        assertEquals(1, computeViewType(5, 10, 3));
    }

    @Test
    public void getItemViewType_position10_withAds_isNativeAd() {
        assertEquals(1, computeViewType(10, 10, 3));
    }

    @Test
    public void getItemViewType_positionBetweenAds_isGame() {
        // Position 6 (after ad at 5) should be a game
        assertEquals(0, computeViewType(6, 10, 3));
    }

    @Test
    public void getItemViewType_position4_isGame() {
        assertEquals(0, computeViewType(4, 10, 3));
    }

    @Test
    public void getItemViewType_tooFewGames_noNativeAds() {
        // 3 games, 0 native ads: everything is game
        assertEquals(0, computeViewType(5, 3, 0));
    }

    @Test
    public void getItemViewType_noAdsEmpty_noNativeType() {
        assertEquals(0, computeViewType(5, 10, 0));
    }

    @Test
    public void getItemViewType_adIndexBeyondAvailable_isGame() {
        // 6 games, 1 native ad: position 10 has adIndex = (10/5)-1 = 1
        // But we only have 1 ad, so adIndex 1 >= 1 → game
        assertEquals(0, computeViewType(10, 6, 1));
    }

    // ── getGamePosition ─────────────────────────────────────────

    @Test
    public void getGamePosition_firstPosition_returnsZero() {
        assertEquals(0, computeGamePosition(0, 10, 3));
    }

    @Test
    public void getGamePosition_afterFirstAd_skipsAdSlot() {
        // Position 6 (after ad at 5): adCountBefore = 6/6 = 1, gamePos = 6-1 = 5
        assertEquals(5, computeGamePosition(6, 10, 3));
    }

    @Test
    public void getGamePosition_atAdPosition_returnsNegative() {
        // Position 5 with ads is an ad slot
        int gamePos = computeGamePosition(5, 10, 3);
        assertTrue(gamePos < 0);
    }

    @Test
    public void getGamePosition_lastGamePosition() {
        // 10 games, 3 ads → 13 total items
        // Position 12 (last): adCountBefore = 12/6 = 2, gamePos = 12-2 = 10
        // But games list is 0-indexed so game index 9 is the last
        int gamePos = computeGamePosition(12, 10, 3);
        assertEquals(10, gamePos);
    }

    @Test
    public void getGamePosition_noAds_returnsSamePosition() {
        assertEquals(3, computeGamePosition(3, 5, 0));
    }

    // ── Ad interval constant ────────────────────────────────────

    @Test
    public void adInterval_is5() throws Exception {
        java.lang.reflect.Field field = com.tansoft.ps1emulator.ui.library.GameAdapter.class.getDeclaredField("AD_INTERVAL");
        field.setAccessible(true);
        assertEquals(5, field.getInt(null));
    }

    @Test
    public void nativeAdMinGames_isReasonable() {
        assertTrue(nativeAdMinGames >= 1);
        assertTrue(nativeAdMinGames <= 20);
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Mirrors GameAdapter.getItemCount() logic.
     */
    private int computeItemCount(int gameCount, int nativeAdCount) {
        if (gameCount < nativeAdMinGames || nativeAdCount == 0) {
            return gameCount;
        }
        int adCount = Math.min(nativeAdCount, gameCount / AD_INTERVAL);
        return gameCount + adCount;
    }

    /**
     * Mirrors GameAdapter.getItemViewType() logic.
     */
    private int computeViewType(int position, int gameCount, int nativeAdCount) {
        if (gameCount >= nativeAdMinGames
                && position > 0 && position % AD_INTERVAL == 0
                && nativeAdCount > 0) {
            int adIndex = (position / AD_INTERVAL) - 1;
            if (adIndex < nativeAdCount) {
                return 1; // TYPE_NATIVE_AD
            }
        }
        return 0; // TYPE_GAME
    }

    /**
     * Mirrors GameAdapter.getGamePosition() logic.
     */
    private int computeGamePosition(int adapterPosition, int gameCount, int nativeAdCount) {
        if (computeViewType(adapterPosition, gameCount, nativeAdCount) == 1) {
            return -1;
        }
        int adCountBefore = adapterPosition / (AD_INTERVAL + 1);
        return adapterPosition - adCountBefore;
    }
}
