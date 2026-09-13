package com.traccar.smsgateway;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class AppLogger {

    private static final int MAX_LOG_ENTRIES = 500;
    private static final LinkedList<LogEntry> logEntries = new LinkedList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static class LogEntry {
        private final long timestamp;
        private final String level;
        private final String tag;
        private final String message;

        public LogEntry(long timestamp, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getLevel() {
            return level;
        }

        public String getTag() {
            return tag;
        }

        public String getMessage() {
            return message;
        }

        public String getFormatted() {
            String timeStr;
            synchronized (dateFormat) {
                timeStr = dateFormat.format(new Date(timestamp));
            }
            return String.format("[%s] [%s/%s]: %s", timeStr, level, tag, message);
        }
    }

    private static void addEntry(String level, String tag, String message) {
        synchronized (logEntries) {
            if (logEntries.size() >= MAX_LOG_ENTRIES) {
                logEntries.removeFirst();
            }
            logEntries.addLast(new LogEntry(System.currentTimeMillis(), level, tag, message));
        }
    }

    public static void d(String tag, String message) {
        try {
            Log.d(tag, message);
        } catch (Throwable ignored) {}
        addEntry("D", tag, message);
    }

    public static void i(String tag, String message) {
        try {
            Log.i(tag, message);
        } catch (Throwable ignored) {}
        addEntry("I", tag, message);
    }

    public static void w(String tag, String message) {
        try {
            Log.w(tag, message);
        } catch (Throwable ignored) {}
        addEntry("W", tag, message);
    }

    public static void e(String tag, String message) {
        try {
            Log.e(tag, message);
        } catch (Throwable ignored) {}
        addEntry("E", tag, message);
    }

    public static void e(String tag, String message, Throwable tr) {
        String fullMessage = message + (tr != null ? "\n" + Log.getStackTraceString(tr) : "");
        try {
            Log.e(tag, message, tr);
        } catch (Throwable ignored) {}
        addEntry("E", tag, fullMessage);
    }

    public static List<LogEntry> getLogs() {
        synchronized (logEntries) {
            return new ArrayList<>(logEntries);
        }
    }

    public static String getFormattedLogs() {
        StringBuilder sb = new StringBuilder();
        synchronized (logEntries) {
            for (LogEntry entry : logEntries) {
                sb.append(entry.getFormatted()).append("\n");
            }
        }
        return sb.toString();
    }

    public static void clearLogs() {
        synchronized (logEntries) {
            logEntries.clear();
        }
    }

    public static int getMaxLogEntries() {
        return MAX_LOG_ENTRIES;
    }
}
