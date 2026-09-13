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
    public void testInvalidCodecId() {
        String invalidHex = "05C0743932C00000804CEC6B018E0200000140E9D530E7A8";
        TeltonikaSmsParser.SmsParseResult result = TeltonikaSmsParser.parseHex(invalidHex);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }
}
