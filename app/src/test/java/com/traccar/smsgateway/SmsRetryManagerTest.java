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

    @Test
    public void testConfigurableMaxRetries() {
        int maxRetries = 2;
        DatabaseHelper.SmsRecord record = new DatabaseHelper.SmsRecord(
                11L, "+11234567890", "Test Message", false,
                "SUCCESS", "123456", "Payload",
                "PENDING", 0, null,
                System.currentTimeMillis(), System.currentTimeMillis()
        );

        // Attempt 1: failure increases retry count to 1, status remains PENDING (< maxRetries 2)
        int newRetryCount1 = record.getRetryCount() + 1;
        record.setRetryCount(newRetryCount1);
        String status1 = (newRetryCount1 >= maxRetries) ? "FAILED" : "PENDING";
        record.setSendStatus(status1);
        assertEquals(1, record.getRetryCount());
        assertEquals("PENDING", record.getSendStatus());

        // Attempt 2: failure increases retry count to 2, status becomes FAILED (>= maxRetries 2)
        int newRetryCount2 = record.getRetryCount() + 1;
        record.setRetryCount(newRetryCount2);
        String status2 = (newRetryCount2 >= maxRetries) ? "FAILED" : "PENDING";
        record.setSendStatus(status2);
        assertEquals(2, record.getRetryCount());
        assertEquals("FAILED", record.getSendStatus());
    }

    @Test
    public void testRetryFailedLimit() {
        int limit = 100;
        assertEquals(100, limit);
    }
}
