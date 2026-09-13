package com.traccar.smsgateway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Database helper for managing SMS messages and application logs.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "traccar_sms_gateway.db";
    private static final int DATABASE_VERSION = 1;

    // Table: sms_messages
    public static final String TABLE_SMS = "sms_messages";
    public static final String COL_SMS_ID = "id";
    public static final String COL_SMS_SENDER = "sender";
    public static final String COL_SMS_RAW_DATA = "raw_data";
    public static final String COL_SMS_IS_BINARY = "is_binary";
    public static final String COL_SMS_PARSE_STATUS = "parse_status"; // PENDING, SUCCESS, FAILED
    public static final String COL_SMS_PARSED_IMEI = "parsed_imei";
    public static final String COL_SMS_PARSED_PAYLOAD = "parsed_payload";
    public static final String COL_SMS_SEND_STATUS = "send_status"; // PENDING, SENT, FAILED
    public static final String COL_SMS_RETRY_COUNT = "retry_count";
    public static final String COL_SMS_ERROR_MESSAGE = "error_message";
    public static final String COL_SMS_CREATED_AT = "created_at";
    public static final String COL_SMS_UPDATED_AT = "updated_at";

    // Table: app_logs
    public static final String TABLE_LOGS = "app_logs";
    public static final String COL_LOG_ID = "id";
    public static final String COL_LOG_TIMESTAMP = "timestamp";
    public static final String COL_LOG_LEVEL = "level";
    public static final String COL_LOG_TAG = "tag";
    public static final String COL_LOG_MESSAGE = "message";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSmsTable = "CREATE TABLE " + TABLE_SMS + " (" +
                COL_SMS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SMS_SENDER + " TEXT, " +
                COL_SMS_RAW_DATA + " TEXT, " +
                COL_SMS_IS_BINARY + " INTEGER, " +
                COL_SMS_PARSE_STATUS + " TEXT, " +
                COL_SMS_PARSED_IMEI + " TEXT, " +
                COL_SMS_PARSED_PAYLOAD + " TEXT, " +
                COL_SMS_SEND_STATUS + " TEXT, " +
                COL_SMS_RETRY_COUNT + " INTEGER DEFAULT 0, " +
                COL_SMS_ERROR_MESSAGE + " TEXT, " +
                COL_SMS_CREATED_AT + " INTEGER, " +
                COL_SMS_UPDATED_AT + " INTEGER" +
                ")";

        String createLogsTable = "CREATE TABLE " + TABLE_LOGS + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOG_TIMESTAMP + " INTEGER, " +
                COL_LOG_LEVEL + " TEXT, " +
                COL_LOG_TAG + " TEXT, " +
                COL_LOG_MESSAGE + " TEXT" +
                ")";

        db.execSQL(createSmsTable);
        db.execSQL(createLogsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    // --- SMS Message Data Model & Database Operations ---

    public static class SmsRecord {
        private long id;
        private String sender;
        private String rawData;
        private boolean isBinary;
        private String parseStatus;
        private String parsedImei;
        private String parsedPayload;
        private String sendStatus;
        private int retryCount;
        private String errorMessage;
        private long createdAt;
        private long updatedAt;

        public SmsRecord() {}

        public SmsRecord(long id, String sender, String rawData, boolean isBinary,
                         String parseStatus, String parsedImei, String parsedPayload,
                         String sendStatus, int retryCount, String errorMessage,
                         long createdAt, long updatedAt) {
            this.id = id;
            this.sender = sender;
            this.rawData = rawData;
            this.isBinary = isBinary;
            this.parseStatus = parseStatus;
            this.parsedImei = parsedImei;
            this.parsedPayload = parsedPayload;
            this.sendStatus = sendStatus;
            this.retryCount = retryCount;
            this.errorMessage = errorMessage;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }

        public String getRawData() { return rawData; }
        public void setRawData(String rawData) { this.rawData = rawData; }

        public boolean isBinary() { return isBinary; }
        public void setBinary(boolean binary) { isBinary = binary; }

        public String getParseStatus() { return parseStatus; }
        public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }

        public String getParsedImei() { return parsedImei; }
        public void setParsedImei(String parsedImei) { this.parsedImei = parsedImei; }

        public String getParsedPayload() { return parsedPayload; }
        public void setParsedPayload(String parsedPayload) { this.parsedPayload = parsedPayload; }

        public String getSendStatus() { return sendStatus; }
        public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public long insertSms(String sender, String rawData, boolean isBinary) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put(COL_SMS_SENDER, sender);
        values.put(COL_SMS_RAW_DATA, rawData);
        values.put(COL_SMS_IS_BINARY, isBinary ? 1 : 0);
        values.put(COL_SMS_PARSE_STATUS, "PENDING");
        values.put(COL_SMS_SEND_STATUS, "PENDING");
        values.put(COL_SMS_RETRY_COUNT, 0);
        values.put(COL_SMS_CREATED_AT, now);
        values.put(COL_SMS_UPDATED_AT, now);
        return db.insert(TABLE_SMS, null, values);
    }

    public boolean updateSmsParseResult(long id, String parseStatus, String imei, String parsedPayload, String errorMessage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SMS_PARSE_STATUS, parseStatus);
        if (imei != null) values.put(COL_SMS_PARSED_IMEI, imei);
        if (parsedPayload != null) values.put(COL_SMS_PARSED_PAYLOAD, parsedPayload);
        if (errorMessage != null) values.put(COL_SMS_ERROR_MESSAGE, errorMessage);
        values.put(COL_SMS_UPDATED_AT, System.currentTimeMillis());
        return db.update(TABLE_SMS, values, COL_SMS_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean updateSmsSendResult(long id, String sendStatus, String errorMessage, boolean incrementRetry) {
        SQLiteDatabase db = this.getWritableDatabase();
        long now = System.currentTimeMillis();
        if (incrementRetry) {
            String sql = "UPDATE " + TABLE_SMS + " SET " +
                    COL_SMS_SEND_STATUS + " = ?, " +
                    COL_SMS_ERROR_MESSAGE + " = ?, " +
                    COL_SMS_RETRY_COUNT + " = " + COL_SMS_RETRY_COUNT + " + 1, " +
                    COL_SMS_UPDATED_AT + " = ? WHERE " + COL_SMS_ID + " = ?";
            db.execSQL(sql, new Object[]{sendStatus, errorMessage, now, id});
            return true;
        } else {
            ContentValues values = new ContentValues();
            values.put(COL_SMS_SEND_STATUS, sendStatus);
            if (errorMessage != null) values.put(COL_SMS_ERROR_MESSAGE, errorMessage);
            values.put(COL_SMS_UPDATED_AT, now);
            return db.update(TABLE_SMS, values, COL_SMS_ID + "=?", new String[]{String.valueOf(id)}) > 0;
        }
    }

    public SmsRecord getSmsById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SMS, null, COL_SMS_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        SmsRecord record = null;
        if (cursor != null && cursor.moveToFirst()) {
            record = cursorToSmsRecord(cursor);
            cursor.close();
        }
        return record;
    }

    public List<SmsRecord> getPendingOrFailedSms() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<SmsRecord> list = new ArrayList<>();
        Cursor cursor = db.query(TABLE_SMS, null, COL_SMS_SEND_STATUS + "!=?", new String[]{"SENT"}, null, null, COL_SMS_CREATED_AT + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToSmsRecord(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public List<SmsRecord> querySms(String imei, Long startTimestamp, Long endTimestamp,
                                    String sendStatus, String parseStatus,
                                    boolean sortAscending, int limit, int offset) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        if (imei != null && !imei.trim().isEmpty()) {
            selection.append(" AND ").append(COL_SMS_PARSED_IMEI).append(" LIKE ?");
            selectionArgs.add("%" + imei.trim() + "%");
        }
        if (startTimestamp != null) {
            selection.append(" AND ").append(COL_SMS_CREATED_AT).append(" >= ?");
            selectionArgs.add(String.valueOf(startTimestamp));
        }
        if (endTimestamp != null) {
            selection.append(" AND ").append(COL_SMS_CREATED_AT).append(" <= ?");
            selectionArgs.add(String.valueOf(endTimestamp));
        }
        if (sendStatus != null && !sendStatus.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_SMS_SEND_STATUS).append(" = ?");
            selectionArgs.add(sendStatus.toUpperCase());
        }
        if (parseStatus != null && !parseStatus.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_SMS_PARSE_STATUS).append(" = ?");
            selectionArgs.add(parseStatus.toUpperCase());
        }

        String orderBy = COL_SMS_CREATED_AT + (sortAscending ? " ASC" : " DESC");
        String limitOffset = limit > 0 ? (offset + ", " + limit) : null;

        Cursor cursor = db.query(TABLE_SMS, null, selection.toString(),
                selectionArgs.toArray(new String[0]), null, null, orderBy, limitOffset);

        List<SmsRecord> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursorToSmsRecord(cursor));
            }
            cursor.close();
        }
        return list;
    }

    public int getSmsCount(String imei, Long startTimestamp, Long endTimestamp,
                           String sendStatus, String parseStatus) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        if (imei != null && !imei.trim().isEmpty()) {
            selection.append(" AND ").append(COL_SMS_PARSED_IMEI).append(" LIKE ?");
            selectionArgs.add("%" + imei.trim() + "%");
        }
        if (startTimestamp != null) {
            selection.append(" AND ").append(COL_SMS_CREATED_AT).append(" >= ?");
            selectionArgs.add(String.valueOf(startTimestamp));
        }
        if (endTimestamp != null) {
            selection.append(" AND ").append(COL_SMS_CREATED_AT).append(" <= ?");
            selectionArgs.add(String.valueOf(endTimestamp));
        }
        if (sendStatus != null && !sendStatus.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_SMS_SEND_STATUS).append(" = ?");
            selectionArgs.add(sendStatus.toUpperCase());
        }
        if (parseStatus != null && !parseStatus.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_SMS_PARSE_STATUS).append(" = ?");
            selectionArgs.add(parseStatus.toUpperCase());
        }

        Cursor cursor = db.query(TABLE_SMS, new String[]{"COUNT(*)"}, selection.toString(),
                selectionArgs.toArray(new String[0]), null, null, null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    private SmsRecord cursorToSmsRecord(Cursor cursor) {
        return new SmsRecord(
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_SMS_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_SENDER)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_RAW_DATA)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_SMS_IS_BINARY)) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_PARSE_STATUS)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_PARSED_IMEI)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_PARSED_PAYLOAD)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_SEND_STATUS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_SMS_RETRY_COUNT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SMS_ERROR_MESSAGE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_SMS_CREATED_AT)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_SMS_UPDATED_AT))
        );
    }

    // --- App Logs Data Model & Database Operations ---

    public void insertLog(long timestamp, String level, String tag, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LOG_TIMESTAMP, timestamp);
        values.put(COL_LOG_LEVEL, level);
        values.put(COL_LOG_TAG, tag);
        values.put(COL_LOG_MESSAGE, message);
        db.insert(TABLE_LOGS, null, values);
    }

    public List<AppLogger.LogEntry> queryLogs(Long startTimestamp, Long endTimestamp,
                                             String tagOrImei, String level,
                                             String searchText, int limit, int offset) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        if (startTimestamp != null) {
            selection.append(" AND ").append(COL_LOG_TIMESTAMP).append(" >= ?");
            selectionArgs.add(String.valueOf(startTimestamp));
        }
        if (endTimestamp != null) {
            selection.append(" AND ").append(COL_LOG_TIMESTAMP).append(" <= ?");
            selectionArgs.add(String.valueOf(endTimestamp));
        }
        if (tagOrImei != null && !tagOrImei.trim().isEmpty()) {
            selection.append(" AND (").append(COL_LOG_TAG).append(" LIKE ? OR ")
                    .append(COL_LOG_MESSAGE).append(" LIKE ?)");
            selectionArgs.add("%" + tagOrImei.trim() + "%");
            selectionArgs.add("%" + tagOrImei.trim() + "%");
        }
        if (level != null && !level.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_LOG_LEVEL).append(" = ?");
            selectionArgs.add(level.toUpperCase());
        }
        if (searchText != null && !searchText.trim().isEmpty()) {
            selection.append(" AND ").append(COL_LOG_MESSAGE).append(" LIKE ?");
            selectionArgs.add("%" + searchText.trim() + "%");
        }

        String orderBy = COL_LOG_TIMESTAMP + " DESC";
        String limitOffset = limit > 0 ? (offset + ", " + limit) : null;

        Cursor cursor = db.query(TABLE_LOGS, null, selection.toString(),
                selectionArgs.toArray(new String[0]), null, null, orderBy, limitOffset);

        List<AppLogger.LogEntry> list = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long ts = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_TIMESTAMP));
                String lvl = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_LEVEL));
                String tg = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_TAG));
                String msg = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_MESSAGE));
                list.add(new AppLogger.LogEntry(ts, lvl, tg, msg));
            }
            cursor.close();
        }
        return list;
    }

    public int getLogsCount(Long startTimestamp, Long endTimestamp,
                            String tagOrImei, String level, String searchText) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        if (startTimestamp != null) {
            selection.append(" AND ").append(COL_LOG_TIMESTAMP).append(" >= ?");
            selectionArgs.add(String.valueOf(startTimestamp));
        }
        if (endTimestamp != null) {
            selection.append(" AND ").append(COL_LOG_TIMESTAMP).append(" <= ?");
            selectionArgs.add(String.valueOf(endTimestamp));
        }
        if (tagOrImei != null && !tagOrImei.trim().isEmpty()) {
            selection.append(" AND (").append(COL_LOG_TAG).append(" LIKE ? OR ")
                    .append(COL_LOG_MESSAGE).append(" LIKE ?)");
            selectionArgs.add("%" + tagOrImei.trim() + "%");
            selectionArgs.add("%" + tagOrImei.trim() + "%");
        }
        if (level != null && !level.equalsIgnoreCase("ALL")) {
            selection.append(" AND ").append(COL_LOG_LEVEL).append(" = ?");
            selectionArgs.add(level.toUpperCase());
        }
        if (searchText != null && !searchText.trim().isEmpty()) {
            selection.append(" AND ").append(COL_LOG_MESSAGE).append(" LIKE ?");
            selectionArgs.add("%" + searchText.trim() + "%");
        }

        Cursor cursor = db.query(TABLE_LOGS, new String[]{"COUNT(*)"}, selection.toString(),
                selectionArgs.toArray(new String[0]), null, null, null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public void clearLogs() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LOGS, null, null);
    }
}
