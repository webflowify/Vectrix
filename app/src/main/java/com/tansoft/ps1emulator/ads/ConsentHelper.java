package com.tansoft.ps1emulator.ads;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class ConsentHelper {

    private static final String TAG = "ConsentHelper";

    // Replace with your test device hash from AdMob dashboard logcat output
    private static final String TEST_DEVICE_HASHED_ID = "YOUR_TEST_DEVICE_HASH";

    private static ConsentInformation consentInformation;

    private ConsentHelper() {}

    /**
     * Checks and requests user consent if needed before loading personalized ads.
     * For EU/CA users, this shows the UMP consent form. For other regions, it's a no-op.
     *
     * @param activity      the current activity (needed to show consent form)
     * @param onConsentReady callback invoked once consent has been obtained or is not required
     */
    public static void requestConsentIfNeeded(@NonNull Activity activity,
                                              @NonNull Runnable onConsentReady) {
        if (!AdsConfig.ENABLE_ADS) {
            onConsentReady.run();
            return;
        }

        Context context = activity.getApplicationContext();

        ConsentRequestParameters params = buildConsentParams(context);

        consentInformation = UserMessagingPlatform.getConsentInformation(context);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            activity,
                            formError -> {
                                if (formError != null) {
                                    Log.w(TAG, "Consent form error: " + formError.getMessage());
                                }
                                onConsentReady.run();
                            });
                },
                requestConsentError -> {
                    Log.w(TAG, "Consent info update error: " + requestConsentError.getMessage());
                    onConsentReady.run();
                });
    }

    /**
     * Returns true if the user has granted consent for personalized ads.
     * Must be called after {@link #requestConsentIfNeeded} has completed.
     */
    public static boolean canRequestPersonalizedAds() {
        if (consentInformation == null) return false;
        return consentInformation.canRequestAds();
    }

    /**
     * Resets consent state. Useful for testing or when consent should be re-evaluated.
     */
    public static void reset() {
        consentInformation = null;
    }

    @NonNull
    private static ConsentRequestParameters buildConsentParams(@NonNull Context context) {
        ConsentRequestParameters.Builder builder = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false);

        if (isDebuggable(context)) {
            ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(context)
                    .setDebugGeography(
                            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(TEST_DEVICE_HASHED_ID)
                    .build();
            builder.setConsentDebugSettings(debugSettings);
        }

        return builder.build();
    }

    private static boolean isDebuggable(@NonNull Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
