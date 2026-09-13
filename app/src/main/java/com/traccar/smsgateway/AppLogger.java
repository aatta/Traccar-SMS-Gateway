package com.traccar.smsgateway;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static DatabaseHelper dbHelper = null;
    private static final List<LogEntry> inMemoryFallbackLogs = new ArrayList<>();

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

    public static synchronized void init(Context context) {
        if (context == null) return;
        dbHelper = DatabaseHelper.getInstance(context);
    }

    public static synchronized void setDbHelper(DatabaseHelper helper) {
        dbHelper = helper;
    }

    public static synchronized void resetLogFile() {
        dbHelper = null;
        synchronized (inMemoryFallbackLogs) {
            inMemoryFallbackLogs.clear();
        }
    }

    private static void addEntry(String level, String tag, String message) {
        long ts = System.currentTimeMillis();
        if (dbHelper != null) {
            try {
                dbHelper.insertLog(ts, level, tag, message);
                return;
            } catch (Exception ignored) {}
        }
        synchronized (inMemoryFallbackLogs) {
            inMemoryFallbackLogs.add(new LogEntry(ts, level, tag, message));
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
        if (dbHelper != null) {
            try {
                return dbHelper.queryLogs(null, null, null, "ALL", null, 500, 0);
            } catch (Exception ignored) {}
        }
        synchronized (inMemoryFallbackLogs) {
            return new ArrayList<>(inMemoryFallbackLogs);
        }
    }

    public static List<LogEntry> queryLogs(Long startTimestamp, Long endTimestamp,
                                           String tagOrImei, String level,
                                           String searchText, int limit, int offset) {
        if (dbHelper != null) {
            try {
                return dbHelper.queryLogs(startTimestamp, endTimestamp, tagOrImei, level, searchText, limit, offset);
            } catch (Exception ignored) {}
        }
        synchronized (inMemoryFallbackLogs) {
            List<LogEntry> filtered = new ArrayList<>();
            for (LogEntry entry : inMemoryFallbackLogs) {
                if (startTimestamp != null && entry.getTimestamp() < startTimestamp) continue;
                if (endTimestamp != null && entry.getTimestamp() > endTimestamp) continue;
                if (level != null && !level.equalsIgnoreCase("ALL") && !entry.getLevel().equalsIgnoreCase(level)) continue;
                if (tagOrImei != null && !tagOrImei.trim().isEmpty()) {
                    String query = tagOrImei.trim().toLowerCase();
                    boolean tagMatch = entry.getTag() != null && entry.getTag().toLowerCase().contains(query);
                    boolean msgMatch = entry.getMessage() != null && entry.getMessage().toLowerCase().contains(query);
                    if (!tagMatch && !msgMatch) continue;
                }
                if (searchText != null && !searchText.trim().isEmpty()) {
                    if (entry.getMessage() == null || !entry.getMessage().toLowerCase().contains(searchText.trim().toLowerCase())) {
                        continue;
                    }
                }
                filtered.add(entry);
            }
            return filtered;
        }
    }

    public static String getFormattedLogs() {
        StringBuilder sb = new StringBuilder();
        List<LogEntry> logs = getLogs();
        for (LogEntry entry : logs) {
            sb.append(entry.getFormatted()).append("\n");
        }
        return sb.toString();
    }

    public static void clearLogs() {
        if (dbHelper != null) {
            try {
                dbHelper.clearLogs();
            } catch (Exception ignored) {}
        }
        synchronized (inMemoryFallbackLogs) {
            inMemoryFallbackLogs.clear();
        }
    }

    public static int getMaxLogEntries() {
        return 500;
    }
}
