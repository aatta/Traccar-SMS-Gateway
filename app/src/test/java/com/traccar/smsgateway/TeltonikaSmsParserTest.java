package com.traccar.smsgateway;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class TeltonikaSmsParserTest {

    @Test
    public void testParseTeltonikaSms() {
        String testHex = "04C0743932C00000804CEC6B018E0200000140E9D530E7A8";
        TeltonikaSmsParser.SmsParseResult result = TeltonikaSmsParser.parseHex(testHex);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getCodecId());
        assertEquals("352848025020328", result.getImei());
        assertEquals(24, result.getElementCount());
        assertEquals(24, result.getElements().size());

        // Elements 0 to 22 should be invalid
        for (int i = 0; i < 23; i++) {
            assertFalse("Element " + i + " should be invalid", result.getElements().get(i).isValid());
        }

        // Element 23 should be valid
        TeltonikaSmsParser.GpsElement lastElem = result.getElements().get(23);
        assertTrue(lastElem.isValid());
        assertFalse(lastElem.isDifferential());
        assertEquals(24.961905, lastElem.getLatitudeDeg(), 0.0001);
        assertEquals(67.067187, lastElem.getLongitudeDeg(), 0.0001);
        assertEquals(0, lastElem.getSpeedKmh());
        // Verify element 23 timestamp matches base timestamp (header timestamp)
        assertEquals(result.getBaseTimestampMillis(), lastElem.getTimestampMillis());
    }

    @Test
    public void testTeltonikaAvlConverter() throws IOException {
        String testHex = "04C0743932C00000804CEC6B018E0200000140E9D530E7A8";
        TeltonikaSmsParser.SmsParseResult result = TeltonikaSmsParser.parseHex(testHex);

        byte[] imeiMsg = TeltonikaAvlConverter.buildImeiMessage(result.getImei());
        assertNotNull(imeiMsg);
        assertEquals(2 + result.getImei().length(), imeiMsg.length);
        assertEquals(15, (imeiMsg[0] << 8) | imeiMsg[1]);

        byte[] tcpPacket = TeltonikaAvlConverter.convertToCodec8TcpPacket(result);
        assertNotNull(tcpPacket);
        assertTrue(tcpPacket.length > 12);

        // Check 4 preamble zero bytes
        assertEquals(0, tcpPacket[0]);
        assertEquals(0, tcpPacket[1]);
        assertEquals(0, tcpPacket[2]);
        assertEquals(0, tcpPacket[3]);

        // Length of AVL data
        int dataLen = ((tcpPacket[4] & 0xFF) << 24) | ((tcpPacket[5] & 0xFF) << 16) | ((tcpPacket[6] & 0xFF) << 8) | (tcpPacket[7] & 0xFF);
        assertEquals(tcpPacket.length - 12, dataLen);

        // Codec ID in AVL data (at index 8)
        assertEquals(0x08, tcpPacket[8]);

        // Record count (at index 9) should be 1 valid record
        assertEquals(1, tcpPacket[9] & 0xFF);
    }

    @Test
    public void testParseCodec8Sms() throws IOException {
        String testHex = "0801000001A09C315FDC0227F922400EE092C0002601050B0000F00101F00100000001000140E9D530E7A8";
        TeltonikaSmsParser.SmsParseResult result = TeltonikaSmsParser.parseHex(testHex);

        assertTrue(result.isSuccess());
        assertEquals(8, result.getCodecId());
        assertEquals("352848025020328", result.getImei());
        assertEquals(1, result.getElementCount());
        assertEquals(1, result.getElements().size());

        TeltonikaSmsParser.GpsElement elem = result.getElements().get(0);
        assertTrue(elem.isValid());
        assertEquals(1789326876636L, elem.getTimestampMillis());
        assertEquals(24.9598656, elem.getLatitudeDeg(), 0.000001);
        assertEquals(67.0638656, elem.getLongitudeDeg(), 0.000001);
        assertEquals(0, elem.getSpeedKmh());

        // Test AVL TCP packet generation for Codec 8 SMS result
        byte[] tcpPacket = TeltonikaAvlConverter.convertToCodec8TcpPacket(result);
        assertNotNull(tcpPacket);
        assertTrue(tcpPacket.length > 12);
        assertEquals(0x08, tcpPacket[8]);
        assertEquals(1, tcpPacket[9] & 0xFF);
    }

    @Test
    public void testInvalidCodecId() {
        String invalidHex = "05C0743932C00000804CEC6B018E0200000140E9D530E7A8";
        TeltonikaSmsParser.SmsParseResult result = TeltonikaSmsParser.parseHex(invalidHex);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    public void testProcessAndFilterElementsSortingAndDeduplication() {
        long now = System.currentTimeMillis();
        long t1 = now - 7200000L; // -2 hours
        long t2 = now - 3600000L; // -1 hour
        long t3 = now;            // current

        // Create elements out of order with duplicates and invalid elements
        TeltonikaSmsParser.GpsElement elemInvalid = new TeltonikaSmsParser.GpsElement(0, false, false, 0, 0, 0, 0, 0, t1 - 1000);
        TeltonikaSmsParser.GpsElement elemT2 = new TeltonikaSmsParser.GpsElement(1, true, false, 0, 0, 24.96, 67.06, 30, t2);
        TeltonikaSmsParser.GpsElement elemT1 = new TeltonikaSmsParser.GpsElement(2, true, false, 0, 0, 24.95, 67.05, 20, t1);
        TeltonikaSmsParser.GpsElement elemT2Dup = new TeltonikaSmsParser.GpsElement(3, true, false, 0, 0, 24.96, 67.06, 30, t2);
        TeltonikaSmsParser.GpsElement elemT3 = new TeltonikaSmsParser.GpsElement(4, true, false, 0, 0, 24.97, 67.07, 40, t3);

        java.util.List<TeltonikaSmsParser.GpsElement> rawList = java.util.Arrays.asList(
                elemInvalid, elemT2, elemT1, elemT2Dup, elemT3
        );

        java.util.List<TeltonikaSmsParser.GpsElement> processed = TeltonikaAvlConverter.processAndFilterElements(rawList, 150.0, true);

        assertEquals(3, processed.size());
        assertEquals(t1, processed.get(0).getTimestampMillis());
        assertEquals(t2, processed.get(1).getTimestampMillis());
        assertEquals(t3, processed.get(2).getTimestampMillis());
    }

    @Test
    public void testProcessAndFilterElementsImpossibleJump() {
        long now = System.currentTimeMillis();
        long t1 = now - 3600000L; // 1 hour ago
        long t2 = now;            // now

        // Distance between (0, 0) and (10, 10) is ~1500 km, impossible in 1 hour at 150 km/h max speed
        TeltonikaSmsParser.GpsElement elem1 = new TeltonikaSmsParser.GpsElement(0, true, false, 0, 0, 0.0, 0.0, 50, t1);
        TeltonikaSmsParser.GpsElement elem2Jump = new TeltonikaSmsParser.GpsElement(1, true, false, 0, 0, 10.0, 10.0, 50, t2);

        java.util.List<TeltonikaSmsParser.GpsElement> rawList = java.util.Arrays.asList(elem1, elem2Jump);

        java.util.List<TeltonikaSmsParser.GpsElement> filtered = TeltonikaAvlConverter.processAndFilterElements(rawList, 150.0, true);

        assertEquals(1, filtered.size());
        assertEquals(t1, filtered.get(0).getTimestampMillis());
    }
}
