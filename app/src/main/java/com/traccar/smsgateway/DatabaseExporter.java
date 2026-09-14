package com.traccar.smsgateway;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helper class for exporting and sharing the application database
 * using Android system FileProvider and Share Chooser Sheet.
 */
public class DatabaseExporter {

    private static final String TAG = "DatabaseExporter";
    public static final String EXPORT_DIR_NAME = "exports";
    public static final String EXPORT_FILE_NAME = "traccar_sms_gateway.db";

    /**
     * Exports the database to the app's cache export directory and returns the exported file.
     *
     * @param context Application context
     * @return Exported database File
     * @throws IOException If file copying fails
     */
    public static File exportDatabase(Context context) throws IOException {
        // Ensure DatabaseHelper flushes any pending WAL data
        try {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
            if (dbHelper != null) {
                Cursor cursor = dbHelper.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL);", null);
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Throwable t) {
            // Ignored if SQLite database or Android context is uninitialized/stubbed in unit tests
        }

        File dbFile = context.getDatabasePath("traccar_sms_gateway.db");
        if (!dbFile.exists()) {
            throw new IOException("Database file does not exist");
        }

        File exportDir = new File(context.getCacheDir(), EXPORT_DIR_NAME);
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Failed to create export directory: " + exportDir.getAbsolutePath());
        }

        File destFile = new File(exportDir, EXPORT_FILE_NAME);

        try (InputStream in = new FileInputStream(dbFile);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();
        }

        return destFile;
    }

    /**
     * Exports the database and opens the Android system share sheet (chooser dialog)
     * allowing the user to save or share the file (e.g. My Files, Drive, etc.)
     * without demanding file system/storage access permissions.
     *
     * @param context Application/Activity context
     */
    public static void exportAndShareDatabase(Context context) {
        try {
            File exportFile = exportDatabase(context);
            String authority = context.getPackageName() + ".fileprovider";
            Uri contentUri = FileProvider.getUriForFile(context, authority, exportFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setClipData(ClipData.newRawUri("Database Export", contentUri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooserIntent = Intent.createChooser(shareIntent, "Export Database");
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (!(context instanceof android.app.Activity)) {
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(chooserIntent);
            AppLogger.i(TAG, "Database export share chooser launched successfully");
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to export database: " + e.getMessage(), e);
            Toast.makeText(context, "Failed to export database: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
