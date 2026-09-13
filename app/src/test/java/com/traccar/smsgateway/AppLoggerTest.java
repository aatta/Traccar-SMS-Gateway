package com.traccar.smsgateway;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppLoggerTest {

    @Before
    public void setUp() {
        AppLogger.resetLogFile();
    }

    @Test
    public void testLoggingAndRetrieval() {
        AppLogger.d("TestTag", "Debug message");
        AppLogger.i("TestTag", "Info message");
        AppLogger.w("TestTag", "Warning message");
        AppLogger.e("TestTag", "Error message");

        List<AppLogger.LogEntry> logs = AppLogger.getLogs();
        assertEquals(4, logs.size());

        assertEquals("D", logs.get(0).getLevel());
        assertEquals("Debug message", logs.get(0).getMessage());

        assertEquals("I", logs.get(1).getLevel());
        assertEquals("Info message", logs.get(1).getMessage());

        assertEquals("W", logs.get(2).getLevel());
        assertEquals("Warning message", logs.get(2).getMessage());

        assertEquals("E", logs.get(3).getLevel());
        assertEquals("Error message", logs.get(3).getMessage());

        String formattedLogs = AppLogger.getFormattedLogs();
        assertTrue(formattedLogs.contains("[D/TestTag]: Debug message"));
        assertTrue(formattedLogs.contains("[I/TestTag]: Info message"));
        assertTrue(formattedLogs.contains("[W/TestTag]: Warning message"));
        assertTrue(formattedLogs.contains("[E/TestTag]: Error message"));
    }

    @Test
    public void testClearLogs() {
        AppLogger.i("TestTag", "Sample log");
        assertEquals(1, AppLogger.getLogs().size());

        AppLogger.clearLogs();
        assertEquals(0, AppLogger.getLogs().size());
        assertEquals("", AppLogger.getFormattedLogs());
    }

    @Test
    public void testQueryFiltering() {
        AppLogger.d("SMSReceiver", "Received SMS from +1234567890");
        AppLogger.i("Parser", "IMEI: 352848025020328 parsed successfully");
        AppLogger.e("TraccarTCPClient", "Connection timed out");

        List<AppLogger.LogEntry> errorLogs = AppLogger.queryLogs(null, null, null, "E", null, 100, 0);
        assertEquals(1, errorLogs.size());
        assertEquals("TraccarTCPClient", errorLogs.get(0).getTag());

        List<AppLogger.LogEntry> imeiLogs = AppLogger.queryLogs(null, null, "352848025020328", "ALL", null, 100, 0);
        assertEquals(1, imeiLogs.size());
        assertEquals("Parser", imeiLogs.get(0).getTag());
    }
}
