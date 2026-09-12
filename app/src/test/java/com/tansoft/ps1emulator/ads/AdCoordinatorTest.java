package com.tansoft.ps1emulator.ads;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.app.Activity;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AdCoordinatorTest {

    private AdCoordinator coordinator;

    @Before
    public void setUp() throws Exception {
        coordinator = AdCoordinator.getInstance();
        // Reset state via reflection between tests
        Field showingField = AdCoordinator.class.getDeclaredField("isFullScreenAdShowing");
        showingField.setAccessible(true);
        showingField.setBoolean(coordinator, false);
        Field endTimeField = AdCoordinator.class.getDeclaredField("lastAdEndTime");
        endTimeField.setAccessible(true);
        endTimeField.setLong(coordinator, 0L);
        Field activityField = AdCoordinator.class.getDeclaredField("currentShowingActivity");
        activityField.setAccessible(true);
        activityField.set(coordinator, null);
    }

    @Test
    public void canShow_initiallyTrue() {
        assertTrue(coordinator.canShow());
    }

    @Test
    public void canShow_whenAdShowing_returnsFalse() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        assertFalse(coordinator.canShow());
    }

    @Test
    public void canShow_afterAdEndsWithinGap_returnsFalse() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        coordinator.onAdEnd();
        // Immediately after end, should be blocked by the gap
        assertFalse(coordinator.canShow());
    }

    @Test
    public void canShow_afterGapElapses_returnsTrue() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        coordinator.onAdEnd();
        // Simulate time passing beyond the gap
        Field endTimeField = AdCoordinator.class.getDeclaredField("lastAdEndTime");
        endTimeField.setAccessible(true);
        endTimeField.setLong(coordinator, System.currentTimeMillis() - AdCoordinator.MIN_GAP_BETWEEN_ADS_MS - 1_000);
        assertTrue(coordinator.canShow());
    }

    @Test
    public void onAdEnd_recordsEndTime() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        long beforeEnd = System.currentTimeMillis();
        coordinator.onAdEnd();
        Field endTimeField = AdCoordinator.class.getDeclaredField("lastAdEndTime");
        endTimeField.setAccessible(true);
        long endTime = endTimeField.getLong(coordinator);
        assertTrue(endTime >= beforeEnd);
        assertTrue(endTime <= System.currentTimeMillis());
    }

    @Test
    public void forceReset_clearsShowingFlag() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        coordinator.forceReset();
        assertFalse(coordinator.isShowing());
        assertTrue(coordinator.canShow());
    }

    @Test
    public void forceReset_whenNotShowing_isNoop() {
        coordinator.forceReset();
        assertTrue(coordinator.canShow());
    }

    @Test
    public void singleton_returnsSameInstance() {
        AdCoordinator a = AdCoordinator.getInstance();
        AdCoordinator b = AdCoordinator.getInstance();
        assertSame(a, b);
    }

    @Test
    public void isShowing_afterStart_returnsTrue() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        assertTrue(coordinator.isShowing());
    }

    @Test
    public void isShowing_afterEnd_returnsFalse() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        coordinator.onAdEnd();
        assertFalse(coordinator.isShowing());
    }

    @Test
    public void canShow_autoClearsStaleState_whenActivityRefCleared() throws Exception {
        Activity mockActivity = mock(Activity.class);
        coordinator.onAdStart(mockActivity);
        // Simulate the WeakReference being cleared by GC (activity ref gone)
        Field showingField = AdCoordinator.class.getDeclaredField("isFullScreenAdShowing");
        showingField.setAccessible(true);
        showingField.setBoolean(coordinator, true);
        Field activityField = AdCoordinator.class.getDeclaredField("currentShowingActivity");
        activityField.setAccessible(true);
        activityField.set(coordinator, new java.lang.ref.WeakReference<>(null));
        // canShow() should auto-clear and return true
        assertTrue(coordinator.canShow());
        assertFalse(coordinator.isShowing());
    }

    @Test
    public void canShow_autoClearsStaleState_whenActivityDestroyed() throws Exception {
        Activity mockActivity = mock(Activity.class);
        when(mockActivity.isFinishing()).thenReturn(false);
        when(mockActivity.isDestroyed()).thenReturn(true);
        coordinator.onAdStart(mockActivity);
        // canShow() should detect destroyed activity and auto-clear
        assertTrue(coordinator.canShow());
        assertFalse(coordinator.isShowing());
    }

    @Test
    public void canShow_doesNotAutoClear_whenActivityStillAlive() throws Exception {
        Activity mockActivity = mock(Activity.class);
        when(mockActivity.isFinishing()).thenReturn(false);
        when(mockActivity.isDestroyed()).thenReturn(false);
        coordinator.onAdStart(mockActivity);
        // Coordinator is still showing; canShow() should NOT auto-clear
        assertFalse(coordinator.canShow());
        assertTrue(coordinator.isShowing());
    }
}
