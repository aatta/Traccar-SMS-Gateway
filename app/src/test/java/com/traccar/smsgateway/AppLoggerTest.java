package com.traccar.smsgateway;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppLoggerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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
    public void testMaxLogCapacity() {
        int max = AppLogger.getMaxLogEntries();
        for (int i = 0; i < max + 50; i++) {
            AppLogger.i("CapacityTest", "Message " + i);
        }

        List<AppLogger.LogEntry> logs = AppLogger.getLogs();
        assertEquals(max, logs.size());
        assertEquals("Message 50", logs.get(0).getMessage());
        assertEquals("Message " + (max + 49), logs.get(max - 1).getMessage());
    }

    @Test
    public void testFilePersistenceAndReload() throws IOException {
        File tempFile = tempFolder.newFile("test_logs.txt");
        AppLogger.setLogFile(tempFile);
        AppLogger.clearLogs();

        AppLogger.i("PersistTag", "First persistent log");
        AppLogger.w("PersistTag", "Second persistent log");

        // Simulate app restart by clearing memory list and re-assigning log file
        AppLogger.setLogFile(tempFile);

        List<AppLogger.LogEntry> reloadedLogs = AppLogger.getLogs();
        assertEquals(2, reloadedLogs.size());
        assertEquals("First persistent log", reloadedLogs.get(0).getMessage());
        assertEquals("Second persistent log", reloadedLogs.get(1).getMessage());
    }
}
