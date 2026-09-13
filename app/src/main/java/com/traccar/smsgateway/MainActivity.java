package com.traccar.smsgateway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private EditText editTraccarHost;
    private EditText editTraccarPort;
    private Switch switchEnabled;
    private Switch switchAutoStart;
    private EditText editPhoneNumber;
    private EditText editDeviceId;
    private Switch switchBinarySms;
    private Button buttonMapDevice;
    private Button buttonSave;
    private Button buttonTest;
    private TextView textStatus;
    private TextView textInfo;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        requestPermissions();
        loadConfiguration();
        setupListeners();
    }

    private void initializeViews() {
        editTraccarHost = findViewById(R.id.editTraccarHost);
        editTraccarPort = findViewById(R.id.editTraccarPort);
        switchEnabled = findViewById(R.id.switchEnabled);
        switchAutoStart = findViewById(R.id.switchAutoStart);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        editDeviceId = findViewById(R.id.editDeviceId);
        switchBinarySms = findViewById(R.id.switchBinarySms);
        buttonMapDevice = findViewById(R.id.buttonMapDevice);
        buttonSave = findViewById(R.id.buttonSave);
        buttonTest = findViewById(R.id.buttonTest);
        textStatus = findViewById(R.id.textStatus);
        textInfo = findViewById(R.id.textInfo);
    }

    private void loadConfiguration() {
        editTraccarHost.setText(PreferenceManager.getTraccarHost(this));
        editTraccarPort.setText(String.valueOf(PreferenceManager.getTraccarPort(this)));
        switchEnabled.setChecked(PreferenceManager.isEnabled(this));
        switchAutoStart.setChecked(PreferenceManager.isAutoStartEnabled(this));
        loadDeviceConfigForPhoneNumber(editPhoneNumber.getText().toString().trim());
    }

    private void loadDeviceConfigForPhoneNumber(String phoneNumber) {
        if (phoneNumber.isEmpty()) {
            editDeviceId.setText("");
            switchBinarySms.setChecked(false);
        } else {
            editDeviceId.setText(PreferenceManager.getDeviceId(this, phoneNumber));
            switchBinarySms.setChecked(PreferenceManager.isBinarySmsEnabled(this, phoneNumber));
        }
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveConfiguration());
        buttonTest.setOnClickListener(v -> testConnection());
        buttonMapDevice.setOnClickListener(v -> mapDevice());

        editPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadDeviceConfigForPhoneNumber(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void mapDevice() {
        String phoneNumber = editPhoneNumber.getText().toString().trim();
        String deviceId = editDeviceId.getText().toString().trim();
        boolean binarySms = switchBinarySms.isChecked();

        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Please enter a tracker phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (deviceId.isEmpty()) {
            deviceId = PreferenceManager.cleanPhoneNumber(phoneNumber);
            editDeviceId.setText(deviceId);
        }

        PreferenceManager.setDeviceId(this, phoneNumber, deviceId);
        PreferenceManager.setBinarySmsEnabled(this, phoneNumber, binarySms);

        updateStatus("✓ Device mapped: " + phoneNumber + " -> " + deviceId + (binarySms ? " (Binary)" : ""));
        Toast.makeText(this, "Device mapped successfully", Toast.LENGTH_SHORT).show();
    }

    private void saveConfiguration() {
        try {
            String host = editTraccarHost.getText().toString().trim();
            String portStr = editTraccarPort.getText().toString().trim();
            boolean enabled = switchEnabled.isChecked();
            boolean autoStart = switchAutoStart.isChecked();

            if (host.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all server fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int port = Integer.parseInt(portStr);

            PreferenceManager.setTraccarHost(this, host);
            PreferenceManager.setTraccarPort(this, port);
            PreferenceManager.setEnabled(this, enabled);
            PreferenceManager.setAutoStart(this, autoStart);

            String phoneNumber = editPhoneNumber.getText().toString().trim();
            if (!phoneNumber.isEmpty()) {
                mapDevice();
            } else {
                updateStatus("✓ Configuration saved successfully!");
                Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
        }
    }

    private void testConnection() {
        try {
            String host = editTraccarHost.getText().toString().trim();
            String portStr = editTraccarPort.getText().toString().trim();

            if (host.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Please configure server settings first", Toast.LENGTH_SHORT).show();
                return;
            }

            int port = Integer.parseInt(portStr);
            updateStatus("Testing connection...");

            new Thread(() -> {
                try {
                    TraccarTCPClient client = TraccarTCPClient.getInstance();
                    client.connect(host, port);
                    
                    if (client.isConnected()) {
                        updateStatus("✓ Connection successful! Ready to receive SMS.");
                        runOnUiThread(() -> 
                            Toast.makeText(MainActivity.this, "Connected to Traccar server", 
                                Toast.LENGTH_SHORT).show()
                        );
                        client.disconnect();
                    } else {
                        updateStatus("✗ Connection failed");
                    }
                } catch (Exception e) {
                    updateStatus("✗ Error: " + e.getMessage());
                    runOnUiThread(() -> 
                        Toast.makeText(MainActivity.this, "Connection error: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show()
                    );
                }
            }).start();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> textStatus.setText(message));
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };

            boolean needsPermission = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    needsPermission = true;
                    break;
                }
            }

            if (needsPermission) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}