package com.traccar.smsgateway;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Converter for converting parsed Teltonika SMS entries into standard Teltonika Codec 8 AVL TCP Data Packets.
 */
public class TeltonikaAvlConverter {

    /** Codec 8 ID */
    public static final byte CODEC_8 = 0x08;

    /**
     * Build IMEI identification message required when connecting to Teltonika TCP server.
     * Format: 2-byte length (big-endian) followed by ASCII bytes of IMEI.
     */
    public static byte[] buildImeiMessage(String imei) {
        if (imei == null) {
            imei = "";
        }
        byte[] imeiBytes = imei.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[2 + imeiBytes.length];
        result[0] = (byte) ((imeiBytes.length >> 8) & 0xFF);
        result[1] = (byte) (imeiBytes.length & 0xFF);
        System.arraycopy(imeiBytes, 0, result, 2, imeiBytes.length);
        return result;
    }

    /**
     * Compute Teltonika CRC16 for the given data bytes (Polynomial 0xA001).
     */
    public static int calculateCrc16(byte[] data) {
        int crc = 0x0000;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Convert parsed Teltonika SMS result into a complete Teltonika Codec 8 TCP AVL Data Packet.
     */
    public static byte[] convertToCodec8TcpPacket(TeltonikaSmsParser.SmsParseResult result) throws IOException {
        List<TeltonikaSmsParser.GpsElement> validElements = new ArrayList<>();
        if (result != null && result.getElements() != null) {
            for (TeltonikaSmsParser.GpsElement elem : result.getElements()) {
                if (elem.isValid()) {
                    validElements.add(elem);
                }
            }
        }
        return buildCodec8TcpPacket(validElements);
    }

    /**
     * Build a Teltonika Codec 8 TCP AVL Data Packet from a list of valid GPS elements.
     */
    public static byte[] buildCodec8TcpPacket(List<TeltonikaSmsParser.GpsElement> elements) throws IOException {
        byte[] avlDataArray = buildCodec8DataArray(elements);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 4 zero bytes preamble
        dos.writeInt(0);

        // Data length (4 bytes integer)
        dos.writeInt(avlDataArray.length);

        // AVL Data Array
        dos.write(avlDataArray);

        // CRC (4 bytes integer - upper 2 bytes 0, lower 2 bytes 16-bit CRC)
        int crc16 = calculateCrc16(avlDataArray);
        dos.writeInt(crc16);

        dos.flush();
        return baos.toByteArray();
    }

    /**
     * Build the AVL Data Array byte array for Codec 8.
     */
    public static byte[] buildCodec8DataArray(List<TeltonikaSmsParser.GpsElement> elements) throws IOException {
        if (elements == null) {
            elements = new ArrayList<>();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Codec ID = 0x08
        dos.writeByte(CODEC_8);

        // Number of Data 1 (1 byte)
        int recordCount = Math.min(elements.size(), 255);
        dos.writeByte(recordCount);

        for (int i = 0; i < recordCount; i++) {
            TeltonikaSmsParser.GpsElement elem = elements.get(i);

            // Timestamp (8 bytes)
            dos.writeLong(elem.getTimestampMillis());

            // Priority (1 byte, 0 = Low)
            dos.writeByte(0);

            // Longitude (4 bytes, degree * 10^7)
            int lonScaled = (int) Math.round(elem.getLongitudeDeg() * 10000000.0);
            dos.writeInt(lonScaled);

            // Latitude (4 bytes, degree * 10^7)
            int latScaled = (int) Math.round(elem.getLatitudeDeg() * 10000000.0);
            dos.writeInt(latScaled);

            // Altitude (2 bytes, meters)
            dos.writeShort(0);

            // Angle (2 bytes, degrees)
            dos.writeShort(0);

            // Satellites (1 byte)
            dos.writeByte(0);

            // Speed (2 bytes, km/h)
            dos.writeShort(elem.getSpeedKmh());

            // IO Elements:
            // Event IO ID (1 byte)
            dos.writeByte(0);
            // Total N of Properties (1 byte)
            dos.writeByte(0);
            // N1 of 1-byte properties (1 byte)
            dos.writeByte(0);
            // N2 of 2-byte properties (1 byte)
            dos.writeByte(0);
            // N4 of 4-byte properties (1 byte)
            dos.writeByte(0);
            // N8 of 8-byte properties (1 byte)
            dos.writeByte(0);
        }

        // Number of Data 2 (1 byte)
        dos.writeByte(recordCount);

        dos.flush();
        return baos.toByteArray();
    }
}
