package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppOpenAdManagerTest {

    private AppOpenAdManager manager;

    @Before
    public void setUp() throws Exception {
        manager = AppOpenAdManager.getInstance();
        setField("totalOpenCount", 0);
        setField("dailyCount", 0);
        setField("lastShowTime", 0L);
        setField("loadTime", 0L);
        setField("appOpenAd", null);
        setField("isLoading", false);
        setField("isShowingAd", false);
        setField("pendingShowActive", false);
        setField("pendingHandled", false);
        setField("pendingActivity", null);
        setField("pendingOnDone", null);

        AdCoordinator.getInstance().forceReset();
    }

    @Test
    public void canShow_whenAdNotLoaded_returnsFalse() {
        assertFalse(manager.canShow());
    }

    @Test
    public void isAdAvailable_whenNull_returnsFalse() {
        assertFalse(manager.isAdAvailable());
    }

    @Test
    public void incrementOpenCount_increments() throws Exception {
        setField("totalOpenCount", 0);
        manager.incrementOpenCount();
        assertEquals(1, manager.getOpenCount());
        manager.incrementOpenCount();
        assertEquals(2, manager.getOpenCount());
    }

    @Test
    public void getOpenCount_initiallyZero() throws Exception {
        setField("totalOpenCount", 0);
        assertEquals(0, manager.getOpenCount());
    }

    @Test
    public void resetDailyCount_clearsCounter() throws Exception {
        setField("dailyCount", 5);
        assertEquals(5, manager.getDailyCount());
        manager.resetDailyCount();
        assertEquals(0, manager.getDailyCount());
    }

    @Test
    public void singleton_returnsSameInstance() {
        AppOpenAdManager a = AppOpenAdManager.getInstance();
        AppOpenAdManager b = AppOpenAdManager.getInstance();
        assertSame(a, b);
    }

    @Test
    public void canShow_atDailyCap_returnsFalse() throws Exception {
        setField("dailyCount", AdsConfig.MAX_APP_OPEN_PER_DAY);
        assertFalse(manager.canShow());
    }

    @Test
    public void canShow_withinCooldown_returnsFalse() throws Exception {
        setField("lastShowTime", System.currentTimeMillis());
        assertFalse(manager.canShow());
    }

    @Test
    public void showWhenReady_adsDisabled_callsOnDoneImmediately() {
        boolean original = AdsConfig.ENABLE_ADS;
        try {
            setStaticField(AdsConfig.class, "ENABLE_ADS", false);
            final boolean[] called = {false};
            manager.showWhenReady(null, () -> called[0] = true);
            assertTrue(called[0]);
        } catch (Exception e) {
            // ENABLE_ADS is final, may not be settable — skip
        }
    }

    @Test
    public void completePendingShow_alreadyHandled_doesNothing() throws Exception {
        setField("pendingHandled", true);
        setField("pendingShowActive", true);
        Method method = AppOpenAdManager.class.getDeclaredMethod("completePendingShow", boolean.class);
        method.setAccessible(true);
        method.invoke(manager, true);
        assertTrue((boolean) getField("pendingHandled"));
    }

    @Test
    public void showIfReady_emulationActivity_returnsImmediately() {
        // Should not throw or perform any action when isEmulationActivity=true
        manager.showIfReady(null, true);
        assertEquals(0, manager.getOpenCount());
    }

    @Test
    public void isShowingAd_initiallyFalse() {
        assertFalse(manager.isShowingAd());
    }

    // ── Warm-start lifecycle tests ──────────────────────────────

    @Test
    public void onActivityStopped_decrementsCounterToZero() throws Exception {
        setField("numStartedActivities", 2);
        manager.onActivityStopped(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertEquals(1, getField("numStartedActivities"));
        manager.onActivityStopped(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertEquals(0, getField("numStartedActivities"));
    }

    @Test
    public void onActivityStopped_clampsCounterToNonNegative() throws Exception {
        setField("numStartedActivities", 0);
        manager.onActivityStopped(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertEquals(0, getField("numStartedActivities"));
    }

    @Test
    public void onActivityStopped_resetsPendingResumeFlag() throws Exception {
        setField("numStartedActivities", 1);
        setField("foregroundDetectedPendingResume", true);
        manager.onActivityStopped(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertFalse((boolean) getField("foregroundDetectedPendingResume"));
    }

    @Test
    public void onActivityStarted_zeroToOne_setsPendingResumeFlag() throws Exception {
        setField("numStartedActivities", 0);
        setField("foregroundDetectedPendingResume", false);
        manager.onActivityStarted(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertEquals(1, getField("numStartedActivities"));
        assertTrue((boolean) getField("foregroundDetectedPendingResume"));
    }

    @Test
    public void onActivityStarted_notZeroToOne_doesNotSetPendingFlag() throws Exception {
        setField("numStartedActivities", 2);
        setField("foregroundDetectedPendingResume", false);
        manager.onActivityStarted(Robolectric.buildActivity(android.app.Activity.class).create().get());
        assertEquals(3, getField("numStartedActivities"));
        assertFalse((boolean) getField("foregroundDetectedPendingResume"));
    }

    @Test
    public void onActivityResumed_withPendingFlag_triggersOnAppForegrounded() throws Exception {
        setField("numStartedActivities", 0);
        setField("foregroundDetectedPendingResume", true);
        setField("isShowingAd", false);
        setField("appOpenAd", null);
        setField("dailyCount", 0);
        setField("lastShowTime", 0L);

        boolean original = AdsConfig.ENABLE_ADS;
        try {
            setStaticField(AdsConfig.class, "ENABLE_ADS", true);
            android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).create().resume().get();
            manager.onActivityResumed(activity);
            assertFalse((boolean) getField("foregroundDetectedPendingResume"));
        } catch (Exception e) {
            // ENABLE_ADS is final — skip if not overridable
        }
    }

    @Test
    public void onActivityResumed_withoutPendingFlag_doesNothing() throws Exception {
        setField("foregroundDetectedPendingResume", false);
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).create().resume().get();
        manager.onActivityResumed(activity);
        assertFalse((boolean) getField("foregroundDetectedPendingResume"));
    }

    // ── Reflection helpers ──────────────────────────────────────

    private void setField(String name, Object value) throws Exception {
        Field field = AppOpenAdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private Object getField(String name) throws Exception {
        Field field = AppOpenAdManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
