package com.traccar.smsgateway;

import android.content.Context;
import android.content.ContextWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DatabaseExporterTest {

    private File tempDir;
    private File dbFile;
    private File cacheDir;

    @Before
    public void setUp() throws IOException {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "db_export_test_" + System.currentTimeMillis());
        tempDir.mkdirs();

        cacheDir = new File(tempDir, "cache");
        cacheDir.mkdirs();

        File dbDir = new File(tempDir, "databases");
        dbDir.mkdirs();

        dbFile = new File(dbDir, "traccar_sms_gateway.db");
        try (FileOutputStream out = new FileOutputStream(dbFile)) {
            out.write("SQLITE TEST HEADER CONTENT".getBytes());
        }
    }

    @After
    public void tearDown() {
        deleteRecursive(tempDir);
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir != null && fileOrDir.exists()) {
            if (fileOrDir.isDirectory()) {
                File[] children = fileOrDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            fileOrDir.delete();
        }
    }

    @Test
    public void testExportDatabaseFileCopying() throws IOException {
        TestContext testContext = new TestContext(dbFile, cacheDir);

        File exportedFile = DatabaseExporter.exportDatabase(testContext);

        assertTrue("Exported file should exist", exportedFile.exists());
        assertEquals("Exported file name should match constant", DatabaseExporter.EXPORT_FILE_NAME, exportedFile.getName());
        assertEquals("Exported file length should match source db length", dbFile.length(), exportedFile.length());
        assertEquals("Exported file path should be inside exports cache directory",
                new File(cacheDir, DatabaseExporter.EXPORT_DIR_NAME).getAbsolutePath(),
                exportedFile.getParentFile().getAbsolutePath());
    }

    private static class TestContext extends ContextWrapper {
        private final File mockDbFile;
        private final File mockCacheDir;

        public TestContext(File mockDbFile, File mockCacheDir) {
            super(null);
            this.mockDbFile = mockDbFile;
            this.mockCacheDir = mockCacheDir;
        }

        @Override
        public File getDatabasePath(String name) {
            return mockDbFile;
        }

        @Override
        public File getCacheDir() {
            return mockCacheDir;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }
    }
}
