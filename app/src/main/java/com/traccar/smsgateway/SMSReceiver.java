package com.traccar.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

/**
 * SMS Receiver - Forwards incoming SMS directly to Traccar via TCP.
 * Parses Teltonika FM2200 binary SMS into Codec 8 TCP AVL Data Packets when applicable.
 */
public class SMSReceiver extends BroadcastReceiver {

    private static final String TAG = "TraccarSMS";
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private static final String PDU_EXTRA_NAME = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        AppLogger.init(context);
        if (intent != null && intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED_ACTION)) {
            Object[] pdus = (Object[]) intent.getExtras().get(PDU_EXTRA_NAME);
            
            if (pdus != null && pdus.length > 0) {
                for (Object pdu : pdus) {
                    SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, "3gpp");
                    if (message == null) continue;
                    
                    String sender = message.getOriginatingAddress();
                    byte[] userData = message.getUserData();

                    // Check if message is Teltonika binary SMS (starts with CodecId 4) or if device is configured for binary SMS
                    if ((userData != null && userData.length > 0 && (userData[0] & 0xFF) == 4)
                            || PreferenceManager.isBinarySmsEnabled(context, sender)) {

                        String hexData = bytesToHex(userData);
                        AppLogger.d(TAG, "SMS Received from: " + sender + " (Binary Mode)");
                        AppLogger.d(TAG, "SMS Binary Content (Hex): " + hexData);

                        // Try parsing Teltonika binary SMS
                        TeltonikaSmsParser.SmsParseResult parseResult = TeltonikaSmsParser.parse(userData);
                        if (parseResult.isSuccess()) {
                            AppLogger.i(TAG, "Teltonika SMS parsed successfully! IMEI: " + parseResult.getImei() +
                                    ", Elements: " + parseResult.getElementCount());
                            forwardTeltonikaAvlToTraccar(context, sender, parseResult);
                        } else {
                            AppLogger.w(TAG, "Failed to parse Teltonika binary SMS: " + parseResult.getError() + ". Forwarding raw bytes.");
                            forwardToTraccar(context, sender, userData);
                        }
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
     * Forward Teltonika parsed binary SMS converted to TCP AVL Data Packet
     */
    private void forwardTeltonikaAvlToTraccar(Context context, String sender, TeltonikaSmsParser.SmsParseResult parseResult) {
        String traccarHost = PreferenceManager.getTraccarHost(context);
        int traccarPort = PreferenceManager.getTraccarPort(context);

        String imei = parseResult.getImei();
        if (imei == null || imei.isEmpty()) {
            imei = PreferenceManager.getDeviceId(context, sender);
        }

        final String targetImei = imei;

        new Thread(() -> {
            try {
                byte[] avlTcpPacket = TeltonikaAvlConverter.convertToCodec8TcpPacket(parseResult);
                TraccarTCPClient.getInstance().sendTeltonikaAvlData(traccarHost, traccarPort, targetImei, avlTcpPacket);
                AppLogger.d(TAG, "Teltonika AVL data packet sent to Traccar (" + traccarHost + ":" + traccarPort + ") for IMEI: " + targetImei);
            } catch (Exception e) {
                AppLogger.e(TAG, "Error sending Teltonika AVL packet to Traccar: " + e.getMessage(), e);
            }
        }).start();
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
