package com.traccar.smsgateway;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    
    private static final String PREFS_NAME = "TraccarSMSGateway";
    private static final String KEY_TRACCAR_HOST = "traccar_host";
    private static final String KEY_TRACCAR_PORT = "traccar_port";
    private static final String KEY_DEVICE_ID_PREFIX = "device_id_";
    private static final String KEY_ENABLED = "gateway_enabled";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_LOG_ENABLED = "log_enabled";

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save Traccar server host
     */
    public static void setTraccarHost(Context context, String host) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(KEY_TRACCAR_HOST, host);
        editor.apply();
    }

    /**
     * Get Traccar server host
     */
    public static String getTraccarHost(Context context) {
        return getPreferences(context).getString(KEY_TRACCAR_HOST, "192.168.1.100");
    }

    /**
     * Save Traccar server port
     */
    public static void setTraccarPort(Context context, int port) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putInt(KEY_TRACCAR_PORT, port);
        editor.apply();
    }

    /**
     * Get Traccar server port (default: 5078 for GPS protocol)
     */
    public static int getTraccarPort(Context context) {
        return getPreferences(context).getInt(KEY_TRACCAR_PORT, 5078);
    }

    /**
     * Map phone number to device ID in Traccar
     * If not mapped, returns the phone number as device ID
     */
    public static void setDeviceId(Context context, String phoneNumber, String deviceId) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(KEY_DEVICE_ID_PREFIX + phoneNumber, deviceId);
        editor.apply();
    }

    /**
     * Get device ID for a given phone number
     */
    public static String getDeviceId(Context context, String phoneNumber) {
        // Clean phone number (remove +, spaces, etc.)
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");
        
        String deviceId = getPreferences(context).getString(KEY_DEVICE_ID_PREFIX + cleanNumber, null);
        
        // If no mapping exists, use the phone number as device ID
        if (deviceId == null) {
            deviceId = cleanNumber;
        }
        
        return deviceId;
    }

    /**
     * Enable/disable gateway
     */
    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.apply();
    }

    /**
     * Check if gateway is enabled
     */
    public static boolean isEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_ENABLED, false);
    }

    /**
     * Enable/disable auto-start on boot
     */
    public static void setAutoStart(Context context, boolean autoStart) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putBoolean(KEY_AUTO_START, autoStart);
        editor.apply();
    }

    /**
     * Check if auto-start is enabled
     */
    public static boolean isAutoStartEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_START, true);
    }

    /**
     * Enable/disable logging
     */
    public static void setLogEnabled(Context context, boolean logEnabled) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putBoolean(KEY_LOG_ENABLED, logEnabled);
        editor.apply();
    }

    /**
     * Check if logging is enabled
     */
    public static boolean isLogEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_LOG_ENABLED, true);
    }

    /**
     * Clear all preferences
     */
    public static void clearAll(Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.clear();
        editor.apply();
    }
}