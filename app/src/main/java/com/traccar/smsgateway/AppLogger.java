package com.traccar.smsgateway;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class AppLogger {

    private static final int MAX_LOG_ENTRIES = 500;
    private static final String LOG_FILE_NAME = "app_logs.txt";
    private static final LinkedList<LogEntry> logEntries = new LinkedList<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File logFile = null;
    private static boolean isInitialized = false;

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
        File dir = context.getFilesDir();
        if (dir != null) {
            setLogFile(new File(dir, LOG_FILE_NAME));
        }
    }

    public static synchronized void setLogFile(File file) {
        logFile = file;
        loadLogsFromFile();
        isInitialized = true;
    }

    private static void loadLogsFromFile() {
        if (logFile == null || !logFile.exists()) return;

        synchronized (logEntries) {
            logEntries.clear();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    LogEntry entry = parseLine(line);
                    if (logEntries.size() >= MAX_LOG_ENTRIES) {
                        logEntries.removeFirst();
                    }
                    logEntries.addLast(entry);
                }
            } catch (Exception ignored) {}
        }
    }

    private static LogEntry parseLine(String line) {
        // Line format: [yyyy-MM-dd HH:mm:ss.SSS] [L/TAG]: Message
        try {
            int timeEnd = line.indexOf(']');
            if (line.startsWith("[") && timeEnd > 1) {
                String timeStr = line.substring(1, timeEnd);
                Date date;
                synchronized (dateFormat) {
                    date = dateFormat.parse(timeStr);
                }
                long time = date != null ? date.getTime() : System.currentTimeMillis();

                int tagStart = line.indexOf('[', timeEnd);
                int tagEnd = line.indexOf(']', tagStart);
                if (tagStart > 0 && tagEnd > tagStart) {
                    String tagLevel = line.substring(tagStart + 1, tagEnd);
                    String[] parts = tagLevel.split("/", 2);
                    String level = parts.length > 0 ? parts[0] : "I";
                    String tag = parts.length > 1 ? parts[1] : "App";

                    int msgStart = line.indexOf(": ", tagEnd);
                    String msg = (msgStart > 0) ? line.substring(msgStart + 2) : "";

                    return new LogEntry(time, level, tag, msg);
                }
            }
        } catch (Exception ignored) {}

        return new LogEntry(System.currentTimeMillis(), "I", "App", line);
    }

    private static void addEntry(String level, String tag, String message) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, tag, message);
        synchronized (logEntries) {
            if (logEntries.size() >= MAX_LOG_ENTRIES) {
                logEntries.removeFirst();
                logEntries.addLast(entry);
                writeAllLogsToFile();
            } else {
                logEntries.addLast(entry);
                appendLogToFile(entry);
            }
        }
    }

    public static synchronized void resetLogFile() {
        synchronized (logEntries) {
            logEntries.clear();
            logFile = null;
            isInitialized = false;
        }
    }

    private static void appendLogToFile(LogEntry entry) {
        if (logFile == null) return;
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
            writer.println(entry.getFormatted());
        } catch (Exception ignored) {}
    }

    private static void writeAllLogsToFile() {
        if (logFile == null) return;
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(logFile, false), StandardCharsets.UTF_8))) {
            for (LogEntry entry : logEntries) {
                writer.println(entry.getFormatted());
            }
        } catch (Exception ignored) {}
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
            if (logFile != null && logFile.exists()) {
                try {
                    // Truncate file
                    new FileOutputStream(logFile, false).close();
                } catch (Exception ignored) {}
            }
        }
    }

    public static int getMaxLogEntries() {
        return MAX_LOG_ENTRIES;
    }
}
