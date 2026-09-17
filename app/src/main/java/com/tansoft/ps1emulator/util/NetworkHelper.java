package com.tansoft.ps1emulator.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * Lightweight helper that answers one question: is the device currently
 * connected to the internet? Used by the ad / monetization layer to gate
 * rewarded actions behind an online check (users must be online to watch ads).
 */
public final class NetworkHelper {

    private NetworkHelper() {}

    /**
     * Returns {@code true} if the device has an active network connection
     * with internet capability.
     */
    public static boolean isAvailable(Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            // Pre-Marshmallow fallback
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }
}
