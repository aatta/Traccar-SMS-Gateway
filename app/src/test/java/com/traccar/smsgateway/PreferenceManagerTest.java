package com.traccar.smsgateway;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreferenceManagerTest {

    private Context fakeContext;
    private FakeSharedPreferences fakePreferences;

    @Before
    public void setUp() {
        fakePreferences = new FakeSharedPreferences();
        fakeContext = new FakeContext(fakePreferences);
    }

    @Test
    public void testMaxRetryCountPreference() {
        // Default max retry count should be 2
        assertEquals(2, PreferenceManager.getMaxRetryCount(fakeContext));

        // Set custom max retry count
        PreferenceManager.setMaxRetryCount(fakeContext, 5);
        assertEquals(5, PreferenceManager.getMaxRetryCount(fakeContext));

        // Setting a negative value should be clamped to at least 0
        PreferenceManager.setMaxRetryCount(fakeContext, -1);
        assertEquals(0, PreferenceManager.getMaxRetryCount(fakeContext));
    }

    @Test
    public void testCleanPhoneNumber() {
        assertEquals("12345678900", PreferenceManager.cleanPhoneNumber("+1 (234) 567-8900"));
        assertEquals("923001234567", PreferenceManager.cleanPhoneNumber("+92-300-1234567"));
        assertEquals("", PreferenceManager.cleanPhoneNumber(null));
        assertEquals("001", PreferenceManager.cleanPhoneNumber("001"));
    }

    @Test
    public void testPerDeviceBinarySmsSettings() {
        String phoneDevice1 = "+1 (555) 000-1111";
        String phoneDevice2 = "+1 (555) 000-2222";

        // Initially neither device has binary SMS enabled
        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice1));
        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice2));

        // Enable binary SMS for device 1
        PreferenceManager.setBinarySmsEnabled(fakeContext, phoneDevice1, true);

        // Verify device 1 has binary SMS enabled, but device 2 remains disabled
        assertTrue(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice1));
        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice2));

        // Disable binary SMS for device 1, enable for device 2
        PreferenceManager.setBinarySmsEnabled(fakeContext, phoneDevice1, false);
        PreferenceManager.setBinarySmsEnabled(fakeContext, phoneDevice2, true);

        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice1));
        assertTrue(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice2));
    }

    @Test
    public void testPerDeviceBinarySmsFallbackToGlobal() {
        String phoneDevice = "+123456789";

        // Global is false by default
        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice));

        // Set global binary SMS to true
        PreferenceManager.setBinarySmsEnabled(fakeContext, true);

        // Device without explicit setting should fallback to global setting (true)
        assertTrue(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice));

        // Setting explicit per-device value overrides global setting
        PreferenceManager.setBinarySmsEnabled(fakeContext, phoneDevice, false);
        assertFalse(PreferenceManager.isBinarySmsEnabled(fakeContext, phoneDevice));
    }

    @Test
    public void testDeviceIdMappingWithPhoneCleaning() {
        String rawPhone = "+1 (555) 123-4567";
        String expectedCleanPhone = "15551234567";
        String customDeviceId = "tracker_alpha";

        // When device is set with raw phone number formatting
        PreferenceManager.setDeviceId(fakeContext, rawPhone, customDeviceId);

        // It should be retrievable either with raw format or clean format
        assertEquals(customDeviceId, PreferenceManager.getDeviceId(fakeContext, rawPhone));
        assertEquals(customDeviceId, PreferenceManager.getDeviceId(fakeContext, expectedCleanPhone));
    }

    @Test
    public void testGetAllDeviceMappingsAndRemove() {
        String phone1 = "+123456";
        String phone2 = "+654321";

        PreferenceManager.setDeviceId(fakeContext, phone1, "dev_1");
        PreferenceManager.setBinarySmsEnabled(fakeContext, phone1, true);

        PreferenceManager.setDeviceId(fakeContext, phone2, "dev_2");
        PreferenceManager.setBinarySmsEnabled(fakeContext, phone2, false);

        java.util.List<PreferenceManager.DeviceMapping> mappings = PreferenceManager.getAllDeviceMappings(fakeContext);
        assertEquals(2, mappings.size());

        PreferenceManager.removeDeviceMapping(fakeContext, phone1);

        mappings = PreferenceManager.getAllDeviceMappings(fakeContext);
        assertEquals(1, mappings.size());
        assertEquals("654321", mappings.get(0).getPhoneNumber());
        assertEquals("dev_2", mappings.get(0).getDeviceId());
        assertFalse(mappings.get(0).isBinarySms());
    }

    private static class FakeContext extends ContextWrapper {
        private final SharedPreferences prefs;

        public FakeContext(SharedPreferences prefs) {
            super(null);
            this.prefs = prefs;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return prefs;
        }
    }

    private static class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, Object> store = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return store;
        }

        @Override
        public String getString(String key, String defValue) {
            return store.containsKey(key) ? (String) store.get(key) : defValue;
        }

        @Override
        public int getInt(String key, int defValue) {
            return store.containsKey(key) ? (Integer) store.get(key) : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            return store.containsKey(key) ? (Long) store.get(key) : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            return store.containsKey(key) ? (Float) store.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return store.containsKey(key) ? (Boolean) store.get(key) : defValue;
        }

        @Override
        public boolean contains(String key) {
            return store.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor(store);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        private static class FakeEditor implements Editor {
            private final Map<String, Object> store;

            public FakeEditor(Map<String, Object> store) {
                this.store = store;
            }

            @Override
            public Editor putString(String key, String value) {
                store.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                store.put(key, values);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                store.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                store.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                store.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                store.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                store.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                store.clear();
                return this;
            }

            @Override
            public boolean commit() {
                return true;
            }

            @Override
            public void apply() {}
        }
    }
}
