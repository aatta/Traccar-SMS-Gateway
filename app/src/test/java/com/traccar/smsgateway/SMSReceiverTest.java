package com.traccar.smsgateway;

import org.junit.Test;
import static org.junit.Assert.*;

public class SMSReceiverTest {

    @Test
    public void testSmsRecordStateTransitions() {
        DatabaseHelper.SmsRecord record = new DatabaseHelper.SmsRecord(
                1L, "+1234567890", "Test Body", false,
                "PENDING", "12345", null,
                "PENDING", 0, null,
                System.currentTimeMillis(), System.currentTimeMillis()
        );

        assertEquals("PENDING", record.getParseStatus());
        assertEquals("PENDING", record.getSendStatus());
        assertEquals(0, record.getRetryCount());

        record.setParseStatus("SUCCESS");
        record.setSendStatus("SENT");
        assertEquals("SUCCESS", record.getParseStatus());
        assertEquals("SENT", record.getSendStatus());
    }
}
