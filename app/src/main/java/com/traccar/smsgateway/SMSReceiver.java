package com.traccar.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

/**
 * SMS Receiver - Simple bridge that forwards all incoming SMS directly to Traccar via TCP
 * 
 * No parsing, no format detection - just a pass-through mechanism.
 * Traccar server handles all protocol parsing.
 * Supports ANY tracker format without needing app updates.
 */
public class SMSReceiver extends BroadcastReceiver {

    private static final String TAG = "TraccarSMS";
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private static final String PDU_EXTRA_NAME = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SMS_RECEIVED_ACTION)) {
            Object[] pdus = (Object[]) intent.getExtras().get(PDU_EXTRA_NAME);
            
            if (pdus != null && pdus.length > 0) {
                for (Object pdu : pdus) {
                    SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, "3gpp");
                    String sender = message.getOriginatingAddress();
                    
                    if (PreferenceManager.isBinarySmsEnabled(context, sender)) {
                        byte[] userData = message.getUserData();
                        String hexData = bytesToHex(userData);
                        AppLogger.d(TAG, "SMS Received from: " + sender + " (Binary Mode)");
                        AppLogger.d(TAG, "SMS Binary Content (Hex): " + hexData);
                        forwardToTraccar(context, sender, userData);
                    } else {
                        String body = message.getMessageBody();
                        AppLogger.d(TAG, "SMS Received from: " + sender);
                        AppLogger.d(TAG, "SMS Body: " + body);
                        forwardToTraccar(context, sender, body);
                    }
                }
            }
        }
    }

    /**
     * Convert byte array to Hex string for logging
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Forward incoming SMS directly to Traccar server via TCP
     */
    private void forwardToTraccar(Context context, String sender, Object smsData) {
        // Get Traccar server configuration
        String traccarHost = PreferenceManager.getTraccarHost(context);
        int traccarPort = PreferenceManager.getTraccarPort(context);
        
        // Send in background thread to avoid blocking
        new Thread(() -> {
            try {
                // Send SMS directly to Traccar
                if (smsData instanceof byte[]) {
                    TraccarTCPClient.getInstance().sendMessage(traccarHost, traccarPort, (byte[]) smsData);
                } else {
                    TraccarTCPClient.getInstance().sendMessage(traccarHost, traccarPort, (String) smsData);
                }
                
                AppLogger.d(TAG, "SMS forwarded to Traccar (" + traccarHost + ":" + traccarPort + ")");
            } catch (Exception e) {
                AppLogger.e(TAG, "Error forwarding SMS to Traccar: " + e.getMessage(), e);
            }
        }).start();
    }
}
