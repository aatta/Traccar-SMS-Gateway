package com.traccar.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

/**
 * SMS Receiver - Stores raw SMS into DB immediately upon receipt,
 * parses messages, and forwards them to Traccar via TCP while tracking parse & send status.
 */
public class SMSReceiver extends BroadcastReceiver {

    private static final String TAG = "TraccarSMS";
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private static final String PDU_EXTRA_NAME = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        AppLogger.init(context);
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);

        if (intent != null && intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED_ACTION)) {
            Object[] pdus = (Object[]) intent.getExtras().get(PDU_EXTRA_NAME);
            
            if (pdus != null && pdus.length > 0) {
                for (Object pdu : pdus) {
                    SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, "3gpp");
                    if (message == null) continue;
                    
                    String sender = message.getOriginatingAddress();
                    byte[] userData = message.getUserData();

                    boolean isBinary = (userData != null && userData.length > 0 && (userData[0] & 0xFF) == 4)
                            || PreferenceManager.isBinarySmsEnabled(context, sender);

                    String rawData;
                    if (isBinary) {
                        rawData = bytesToHex(userData);
                    } else {
                        String body = message.getMessageBody();
                        rawData = (body != null) ? body : bytesToHex(userData);
                    }

                    // 1. Immediately store incoming raw SMS in DB as PENDING so it is never lost
                    long smsId = dbHelper.insertSms(sender, rawData, isBinary);
                    AppLogger.i(TAG, "Stored raw incoming SMS in DB (ID: " + smsId + ", Sender: " + sender + ", Binary: " + isBinary + ")");

                    // 2. Process and attempt to forward
                    if (isBinary) {
                        processBinarySms(context, dbHelper, smsId, sender, userData);
                    } else {
                        processTextSms(context, dbHelper, smsId, sender, rawData);
                    }
                }
            }
        }
    }

    private void processBinarySms(Context context, DatabaseHelper dbHelper, long smsId, String sender, byte[] userData) {
        AppLogger.d(TAG, "Processing Binary SMS (ID: " + smsId + ") from: " + sender);

        TeltonikaSmsParser.SmsParseResult parseResult = TeltonikaSmsParser.parse(userData);

        if (parseResult.isSuccess()) {
            String imei = parseResult.getImei();
            if (imei == null || imei.isEmpty()) {
                imei = PreferenceManager.getDeviceId(context, sender);
            }

            String payloadSummary = "CodecId: " + parseResult.getCodecId() +
                    ", Elements: " + parseResult.getElementCount() +
                    ", BaseTimeMillis: " + parseResult.getBaseTimestampMillis();

            dbHelper.updateSmsParseResult(smsId, "SUCCESS", imei, payloadSummary, null);
            AppLogger.i(TAG, "Binary SMS parsed successfully! SMS ID: " + smsId + ", IMEI: " + imei);

            forwardTeltonikaAvlToTraccar(context, dbHelper, smsId, sender, parseResult, imei);
        } else {
            String errorMsg = parseResult.getError() != null ? parseResult.getError() : "Failed to parse binary SMS";
            String fallbackImei = PreferenceManager.getDeviceId(context, sender);
            dbHelper.updateSmsParseResult(smsId, "FAILED", fallbackImei, null, errorMsg);
            AppLogger.w(TAG, "Failed to parse binary SMS (ID: " + smsId + "): " + errorMsg + ". Forwarding raw bytes.");

            forwardRawToTraccar(context, dbHelper, smsId, userData);
        }
    }

    private void processTextSms(Context context, DatabaseHelper dbHelper, long smsId, String sender, String textBody) {
        AppLogger.d(TAG, "Processing Text SMS (ID: " + smsId + ") from: " + sender);

        String mappedImei = PreferenceManager.getDeviceId(context, sender);
        dbHelper.updateSmsParseResult(smsId, "SUCCESS", mappedImei, textBody, null);

        forwardRawToTraccar(context, dbHelper, smsId, textBody);
    }

    private void forwardTeltonikaAvlToTraccar(Context context, DatabaseHelper dbHelper, long smsId, String sender, TeltonikaSmsParser.SmsParseResult parseResult, String targetImei) {
        String traccarHost = PreferenceManager.getTraccarHost(context);
        int traccarPort = PreferenceManager.getTraccarPort(context);

        new Thread(() -> {
            try {
                byte[] avlTcpPacket = TeltonikaAvlConverter.convertToCodec8TcpPacket(parseResult);
                TraccarTCPClient.getInstance().sendTeltonikaAvlData(traccarHost, traccarPort, targetImei, avlTcpPacket);

                dbHelper.updateSmsSendResult(smsId, "SENT", null, false);
                AppLogger.i(TAG, "Teltonika AVL packet sent successfully to Traccar for SMS ID: " + smsId + " (IMEI: " + targetImei + ")");
            } catch (Exception e) {
                String errMsg = "Error sending Teltonika AVL packet: " + e.getMessage();
                dbHelper.updateSmsSendResult(smsId, "FAILED", errMsg, true);
                AppLogger.e(TAG, "SMS ID " + smsId + " send error: " + e.getMessage(), e);
            }
        }).start();
    }

    private void forwardRawToTraccar(Context context, DatabaseHelper dbHelper, long smsId, Object smsData) {
        String traccarHost = PreferenceManager.getTraccarHost(context);
        int traccarPort = PreferenceManager.getTraccarPort(context);

        new Thread(() -> {
            try {
                if (smsData instanceof byte[]) {
                    TraccarTCPClient.getInstance().sendMessage(traccarHost, traccarPort, (byte[]) smsData);
                } else {
                    TraccarTCPClient.getInstance().sendMessage(traccarHost, traccarPort, (String) smsData);
                }

                dbHelper.updateSmsSendResult(smsId, "SENT", null, false);
                AppLogger.i(TAG, "SMS ID " + smsId + " forwarded successfully to Traccar (" + traccarHost + ":" + traccarPort + ")");
            } catch (Exception e) {
                String errMsg = "Error forwarding raw SMS: " + e.getMessage();
                dbHelper.updateSmsSendResult(smsId, "FAILED", errMsg, true);
                AppLogger.e(TAG, "SMS ID " + smsId + " send error: " + e.getMessage(), e);
            }
        }).start();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
