package com.traccar.smsgateway;

import android.util.Log;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TraccarTCPClient {
    
    private static final String TAG = "TraccarTCPClient";
    private static TraccarTCPClient instance;
    private Socket socket;
    private OutputStream outputStream;
    private boolean isConnected = false;

    // Singleton pattern
    public static synchronized TraccarTCPClient getInstance() {
        if (instance == null) {
            instance = new TraccarTCPClient();
        }
        return instance;
    }

    private TraccarTCPClient() {
    }

    /**
     * Connect to Traccar server
     */
    public void connect(String host, int port) throws Exception {
        if (isConnected && socket != null && socket.isConnected()) {
            Log.d(TAG, "Already connected to " + host + ":" + port);
            return;
        }

        try {
            socket = new Socket(host, port);
            outputStream = socket.getOutputStream();
            isConnected = true;
            Log.d(TAG, "Connected to Traccar server: " + host + ":" + port);
        } catch (Exception e) {
            isConnected = false;
            Log.e(TAG, "Failed to connect to Traccar server: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send raw message to Traccar server
     */
    public void sendMessage(String host, int port, String message) throws Exception {
        // Ensure we're connected
        if (!isConnected || socket == null || !socket.isConnected()) {
            connect(host, port);
        }

        try {
            // Add newline if not present (Traccar expects line-terminated messages)
            if (!message.endsWith("\n")) {
                message = message + "\n";
            }

            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            outputStream.write(data);
            outputStream.flush();
            
            Log.d(TAG, "Sent to Traccar: " + message.trim());
        } catch (Exception e) {
            isConnected = false;
            disconnect();
            Log.e(TAG, "Error sending message: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send location update in Traccar protocol format
     * Supports: NMEA, Osmand, GT06, etc.
     */
    public void sendLocationUpdate(String host, int port, String deviceId, 
                                   double latitude, double longitude, 
                                   double speed, double course) throws Exception {
        // Format as NMEA GPRMC sentence
        // $GPRMC,hhmmss.ss,ddmm.mmmm,N/S,dddmm.mmmm,E/W,speed,course,ddmmyy,...*checksum
        
        String latDMS = convertDecimalToDMS(Math.abs(latitude), latitude >= 0 ? "N" : "S");
        String lonDMS = convertDecimalToDMS(Math.abs(longitude), longitude >= 0 ? "E" : "W");
        
        String gprmc = String.format("$GPRMC,000000.00,%s,%s,%.2f,%.2f,010101,000.0,W*00",
                latDMS, lonDMS, speed, course);
        
        sendMessage(host, port, gprmc);
    }

    /**
     * Send location in Osmand format (compatible with Traccar)
     * Format: ?id=deviceId&lat=latitude&lon=longitude&timestamp=timestamp
     * (Note: Osmand protocol is typically HTTP, but can be adapted for TCP)
     */
    public void sendLocationUpdateOsmand(String host, int port, String deviceId,
                                         double latitude, double longitude,
                                         double speed, double course, long timestamp) throws Exception {
        String message = String.format("?id=%s&lat=%.6f&lon=%.6f&speed=%.2f&course=%.2f&timestamp=%d",
                deviceId, latitude, longitude, speed, course, timestamp);
        
        sendMessage(host, port, message);
    }

    /**
     * Send raw NMEA sentence
     */
    public void sendNMEA(String host, int port, String nmeaSentence) throws Exception {
        sendMessage(host, port, nmeaSentence);
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
            isConnected = false;
            Log.d(TAG, "Disconnected from Traccar server");
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected();
    }

    /**
     * Convert decimal degrees to DMS format
     * Returns: "DDMM.MMMM"
     */
    private String convertDecimalToDMS(double decimal, String direction) {
        int degrees = (int) decimal;
        double minutesDecimal = (decimal - degrees) * 60;
        
        return String.format("%02d%06.3f,%s", degrees, minutesDecimal, direction);
    }
}