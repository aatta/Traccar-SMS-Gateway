package com.traccar.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * BroadcastReceiver listening for network connectivity changes.
 * Automatically triggers SMS resend retry when network connectivity is restored.
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;

            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
            if (isConnected) {
                AppLogger.i(TAG, "Network connection restored. Triggering pending SMS retry check.");
                SmsRetryManager.retryPendingMessages(context);
            }
        }
    }
}
