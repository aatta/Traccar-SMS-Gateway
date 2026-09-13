package com.traccar.smsgateway;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsRetryManagerTest {

    @Test
    public void testSmsRetryLogic() {
        DatabaseHelper.SmsRecord record = new DatabaseHelper.SmsRecord(
                10L, "+19876543210", "Test Retry Body", false,
                "SUCCESS", "990000", "Payload",
                "FAILED", 1, "Connection refused",
                System.currentTimeMillis(), System.currentTimeMillis()
        );

        assertEquals("FAILED", record.getSendStatus());
        assertEquals(1, record.getRetryCount());

        // Simulate retry increment
        record.setRetryCount(record.getRetryCount() + 1);
        record.setSendStatus("SENT");
        assertEquals(2, record.getRetryCount());
        assertEquals("SENT", record.getSendStatus());
    }
}
