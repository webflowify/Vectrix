package com.tansoft.ps1emulator.ads;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * Lightweight network state checker.
 * No-op if called before Application.onCreate (context is null).
 */
public final class NetworkHelper {

    private NetworkHelper() {}

    private static volatile boolean isOnline = false;
    private static ConnectivityManager.NetworkCallback networkCallback;

    /**
     * Start monitoring network state. Call once from Application.onCreate.
     */
    public static void startMonitoring(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        isOnline = isConnected(cm);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    isOnline = true;
                }

                @Override
                public void onLost(@NonNull Network network) {
                    isOnline = false;
                }
            };

            cm.registerNetworkCallback(request, networkCallback);
        }
    }

    public static boolean isOnline() {
        return isOnline;
    }

    public static boolean isOffline() {
        return !isOnline;
    }

    private static boolean isConnected(@NonNull ConnectivityManager cm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }
}
