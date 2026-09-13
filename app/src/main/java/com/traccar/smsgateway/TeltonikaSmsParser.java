package com.traccar.smsgateway;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Teltonika FM2200 24-hour Position SMS Data Protocol (Codec 4).
 * Decodes compressed binary SMS into structured GPS entries.
 */
public class TeltonikaSmsParser {

    /** Base timestamp in milliseconds for 2000-01-01 00:00:00 EET (UTC+2) */
    public static final long BASE_TIMESTAMP_MILLIS = 946677600000L;

    public static class BitStream {
        private final byte[] data;
        private int bitPos;

        public BitStream(byte[] data) {
            this.data = data;
            this.bitPos = 0;
        }

        public long readBits(int numBits) {
            long result = 0;
            for (int i = 0; i < numBits; i++) {
                int byteIdx = bitPos / 8;
                int bitIdx = bitPos % 8;
                if (byteIdx >= data.length) {
                    break;
                }
                long bit = (data[byteIdx] >> bitIdx) & 1;
                result |= (bit << i);
                bitPos++;
            }
            return result;
        }

        public void alignToByte() {
            if (bitPos % 8 != 0) {
                bitPos += 8 - (bitPos % 8);
            }
        }

        public int getBitPos() {
            return bitPos;
        }
    }

    public static class GpsElement {
        private final int index;
        private final boolean valid;
        private final boolean differential;
        private final long longitudeRaw;
        private final long latitudeRaw;
        private final double longitudeDeg;
        private final double latitudeDeg;
        private final int speedKmh;
        private final long timestampMillis;

        public GpsElement(int index, boolean valid, boolean differential,
                          long longitudeRaw, long latitudeRaw,
                          double longitudeDeg, double latitudeDeg,
                          int speedKmh, long timestampMillis) {
            this.index = index;
            this.valid = valid;
            this.differential = differential;
            this.longitudeRaw = longitudeRaw;
            this.latitudeRaw = latitudeRaw;
            this.longitudeDeg = longitudeDeg;
            this.latitudeDeg = latitudeDeg;
            this.speedKmh = speedKmh;
            this.timestampMillis = timestampMillis;
        }

        public int getIndex() { return index; }
        public boolean isValid() { return valid; }
        public boolean isDifferential() { return differential; }
        public long getLongitudeRaw() { return longitudeRaw; }
        public long getLatitudeRaw() { return latitudeRaw; }
        public double getLongitudeDeg() { return longitudeDeg; }
        public double getLatitudeDeg() { return latitudeDeg; }
        public int getSpeedKmh() { return speedKmh; }
        public long getTimestampMillis() { return timestampMillis; }
    }

    public static class SmsParseResult {
        private final boolean success;
        private final String error;
        private final int codecId;
        private final long timestampSeconds;
        private final long baseTimestampMillis;
        private final int elementCount;
        private final String imei;
        private final List<GpsElement> elements;

        public SmsParseResult(boolean success, String error, int codecId,
                              long timestampSeconds, long baseTimestampMillis,
                              int elementCount, String imei, List<GpsElement> elements) {
            this.success = success;
            this.error = error;
            this.codecId = codecId;
            this.timestampSeconds = timestampSeconds;
            this.baseTimestampMillis = baseTimestampMillis;
            this.elementCount = elementCount;
            this.imei = imei;
            this.elements = elements != null ? elements : new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public int getCodecId() { return codecId; }
        public long getTimestampSeconds() { return timestampSeconds; }
        public long getBaseTimestampMillis() { return baseTimestampMillis; }
        public int getElementCount() { return elementCount; }
        public String getImei() { return imei; }
        public List<GpsElement> getElements() { return elements; }
    }

    public static SmsParseResult parseHex(String hexString) {
        if (hexString == null || hexString.trim().isEmpty()) {
            return new SmsParseResult(false, "Hex string is null or empty", 0, 0, 0, 0, null, null);
        }
        String cleanHex = hexString.replaceAll("\\s+", "");
        if (cleanHex.length() % 2 != 0) {
            return new SmsParseResult(false, "Invalid hex string length", 0, 0, 0, 0, null, null);
        }
        byte[] data = new byte[cleanHex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(cleanHex.substring(i * 2, i * 2 + 2), 16);
        }
        return parse(data);
    }

    public static SmsParseResult parse(byte[] data) {
        if (data == null || data.length == 0) {
            return new SmsParseResult(false, "Data is null or empty", 0, 0, 0, 0, null, null);
        }

        BitStream stream = new BitStream(data);

        int codecId = (int) stream.readBits(8);
        if (codecId != 4) {
            return new SmsParseResult(false, "Invalid CodecId: " + codecId + ". Expected 4 for Teltonika 24-hour SMS.",
                    codecId, 0, 0, 0, null, null);
        }

        long timestampSeconds = stream.readBits(35);
        long baseTimestampMillis = BASE_TIMESTAMP_MILLIS + (timestampSeconds * 1000L);

        int elementCount = (int) stream.readBits(5);
        List<GpsElement> elements = new ArrayList<>();

        long prevLongitude = 0;
        long prevLatitude = 0;

        for (int i = 0; i < elementCount; i++) {
            boolean valid = stream.readBits(1) == 1;
            // The timestamp in the SMS header represents the time of the latest (last) element.
            // Earlier elements are offset backwards by 1 hour each.
            long elementTimeMillis = baseTimestampMillis - ((elementCount - 1 - i) * 3600000L);

            if (!valid) {
                elements.add(new GpsElement(i, false, false, 0, 0, 0.0, 0.0, 0, elementTimeMillis));
                continue;
            }

            boolean differential = stream.readBits(1) == 1;
            long longitude;
            long latitude;

            if (differential) {
                long lonDiff = stream.readBits(14);
                long latDiff = stream.readBits(14);
                long offset = (1L << 13) - 1; // 8191
                longitude = prevLongitude - lonDiff + offset;
                latitude = prevLatitude - latDiff + offset;
            } else {
                longitude = stream.readBits(21);
                latitude = stream.readBits(20);
            }

            int speed = (int) stream.readBits(8);

            double lonDeg = (longitude * 360.0) / ((1L << 21) - 1) - 180.0;
            double latDeg = (latitude * 180.0) / ((1L << 20) - 1) - 90.0;

            elements.add(new GpsElement(i, true, differential, longitude, latitude, lonDeg, latDeg, speed, elementTimeMillis));

            prevLongitude = longitude;
            prevLatitude = latitude;
        }

        stream.alignToByte();
        int byteIdx = stream.getBitPos() / 8;
        String imei = null;

        if (byteIdx + 8 <= data.length) {
            long imeiVal = 0;
            for (int b = 0; b < 8; b++) {
                imeiVal = (imeiVal << 8) | (data[byteIdx + b] & 0xFF);
            }
            imei = Long.toUnsignedString(imeiVal);
        }

        return new SmsParseResult(true, null, codecId, timestampSeconds, baseTimestampMillis, elementCount, imei, elements);
    }
}
