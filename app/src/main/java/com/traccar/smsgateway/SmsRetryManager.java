package com.traccar.smsgateway;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager for retrying failed or pending SMS transmissions when internet becomes available.
 */
public class SmsRetryManager {

    private static final String TAG = "SmsRetryManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static boolean isRetrying = false;

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Error checking network availability: " + e.getMessage());
        }
        return false;
    }

    public static void retryPendingMessages(Context context) {
        if (context == null) return;
        if (!isNetworkAvailable(context)) {
            AppLogger.d(TAG, "Network not available. Skipping retry.");
            return;
        }

        synchronized (SmsRetryManager.class) {
            if (isRetrying) {
                AppLogger.d(TAG, "Retry operation already in progress.");
                return;
            }
            isRetrying = true;
        }

        executor.execute(() -> {
            try {
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
                List<DatabaseHelper.SmsRecord> pendingRecords = dbHelper.getPendingOrFailedSms();

                if (pendingRecords.isEmpty()) {
                    AppLogger.d(TAG, "No pending or failed SMS to retry.");
                    return;
                }

                AppLogger.i(TAG, "Found " + pendingRecords.size() + " SMS messages to retry.");
                String traccarHost = PreferenceManager.getTraccarHost(context);
                int traccarPort = PreferenceManager.getTraccarPort(context);

                for (DatabaseHelper.SmsRecord record : pendingRecords) {
                    if (!isNetworkAvailable(context)) {
                        AppLogger.w(TAG, "Network lost during retry process. Aborting retry loop.");
                        break;
                    }

                    resendSingleRecord(context, dbHelper, record, traccarHost, traccarPort);
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "Error in retry process: " + e.getMessage(), e);
            } finally {
                synchronized (SmsRetryManager.class) {
                    isRetrying = false;
                }
            }
        });
    }

    private static void resendSingleRecord(Context context, DatabaseHelper dbHelper, DatabaseHelper.SmsRecord record, String host, int port) {
        long smsId = record.getId();
        AppLogger.i(TAG, "Retrying SMS ID: " + smsId + " (Attempt #" + (record.getRetryCount() + 1) + ")");

        try {
            if (record.isBinary()) {
                byte[] rawBytes = hexToBytes(record.getRawData());
                TeltonikaSmsParser.SmsParseResult parseResult = TeltonikaSmsParser.parse(rawBytes);

                if (parseResult.isSuccess()) {
                    String imei = parseResult.getImei();
                    if (imei == null || imei.isEmpty()) {
                        imei = record.getParsedImei();
                    }
                    if (imei == null || imei.isEmpty()) {
                        imei = PreferenceManager.getDeviceId(context, record.getSender());
                    }

                    byte[] avlTcpPacket = TeltonikaAvlConverter.convertToCodec8TcpPacket(parseResult);
                    TraccarTCPClient.getInstance().sendTeltonikaAvlData(host, port, imei, avlTcpPacket);
                    dbHelper.updateSmsSendResult(smsId, "SENT", null, false);
                    AppLogger.i(TAG, "Successfully resent Teltonika AVL SMS ID: " + smsId);
                } else {
                    // Raw binary forwarding fallback
                    TraccarTCPClient.getInstance().sendMessage(host, port, rawBytes);
                    dbHelper.updateSmsSendResult(smsId, "SENT", null, false);
                    AppLogger.i(TAG, "Successfully resent raw binary SMS ID: " + smsId);
                }
            } else {
                // Text SMS forwarding
                TraccarTCPClient.getInstance().sendMessage(host, port, record.getRawData());
                dbHelper.updateSmsSendResult(smsId, "SENT", null, false);
                AppLogger.i(TAG, "Successfully resent text SMS ID: " + smsId);
            }
        } catch (Exception e) {
            String errMsg = "Retry failed: " + e.getMessage();
            dbHelper.updateSmsSendResult(smsId, "FAILED", errMsg, true);
            AppLogger.e(TAG, "Failed retry attempt for SMS ID: " + smsId + " - " + e.getMessage());
        }
    }

    private static byte[] hexToBytes(String hexString) {
        if (hexString == null || hexString.trim().isEmpty()) return new byte[0];
        String cleanHex = hexString.replaceAll("\\s+", "");
        if (cleanHex.length() % 2 != 0) return new byte[0];
        byte[] data = new byte[cleanHex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
        }
        return data;
    }
}
