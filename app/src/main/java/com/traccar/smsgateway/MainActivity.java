package com.traccar.smsgateway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText editTraccarHost;
    private EditText editTraccarPort;
    private Switch switchEnabled;
    private Switch switchAutoStart;
    private Button buttonAddDevice;
    private LinearLayout containerDeviceList;
    private TextView textEmptyDevices;
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
        buttonAddDevice = findViewById(R.id.buttonAddDevice);
        containerDeviceList = findViewById(R.id.containerDeviceList);
        textEmptyDevices = findViewById(R.id.textEmptyDevices);
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
        refreshDeviceMappingsList();
    }

    private void refreshDeviceMappingsList() {
        containerDeviceList.removeAllViews();
        List<PreferenceManager.DeviceMapping> mappings = PreferenceManager.getAllDeviceMappings(this);

        if (mappings.isEmpty()) {
            textEmptyDevices.setVisibility(View.VISIBLE);
            containerDeviceList.addView(textEmptyDevices);
        } else {
            textEmptyDevices.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (PreferenceManager.DeviceMapping mapping : mappings) {
                View itemView = inflater.inflate(R.layout.item_device_mapping, containerDeviceList, false);

                TextView textPhone = itemView.findViewById(R.id.textItemPhone);
                TextView textDeviceId = itemView.findViewById(R.id.textItemDeviceId);
                TextView textMode = itemView.findViewById(R.id.textItemMode);
                Button buttonEdit = itemView.findViewById(R.id.buttonItemEdit);
                Button buttonDelete = itemView.findViewById(R.id.buttonItemDelete);

                textPhone.setText("Phone: " + mapping.getPhoneNumber());
                textDeviceId.setText("Device ID: " + mapping.getDeviceId());
                textMode.setText("Mode: " + (mapping.isBinarySms() ? "Binary SMS" : "Text SMS"));

                buttonEdit.setOnClickListener(v -> showAddEditDeviceDialog(mapping));
                buttonDelete.setOnClickListener(v -> confirmAndDeleteDeviceMapping(mapping));

                containerDeviceList.addView(itemView);
            }
        }
    }

    private void showAddEditDeviceDialog(PreferenceManager.DeviceMapping existingMapping) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_device_mapping, null);
        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        EditText editPhone = dialogView.findViewById(R.id.dialogEditPhoneNumber);
        EditText editDeviceId = dialogView.findViewById(R.id.dialogEditDeviceId);
        Switch switchBinary = dialogView.findViewById(R.id.dialogSwitchBinarySms);

        if (existingMapping != null) {
            dialogTitle.setText("Edit Device Mapping");
            editPhone.setText(existingMapping.getPhoneNumber());
            editPhone.setEnabled(false); // Phone number acts as primary key
            editDeviceId.setText(existingMapping.getDeviceId());
            switchBinary.setChecked(existingMapping.isBinarySms());
        } else {
            dialogTitle.setText("Add Device Mapping");
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            Button saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveBtn.setOnClickListener(v -> {
                String phone = editPhone.getText().toString().trim();
                String deviceId = editDeviceId.getText().toString().trim();
                boolean binary = switchBinary.isChecked();

                if (phone.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (deviceId.isEmpty()) {
                    deviceId = PreferenceManager.cleanPhoneNumber(phone);
                }

                PreferenceManager.setDeviceId(MainActivity.this, phone, deviceId);
                PreferenceManager.setBinarySmsEnabled(MainActivity.this, phone, binary);

                refreshDeviceMappingsList();
                updateStatus("✓ Device mapped: " + phone + " -> " + deviceId + (binary ? " (Binary)" : ""));
                Toast.makeText(MainActivity.this, "Device mapping saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void confirmAndDeleteDeviceMapping(PreferenceManager.DeviceMapping mapping) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Mapping")
                .setMessage("Are you sure you want to remove mapping for phone " + mapping.getPhoneNumber() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    PreferenceManager.removeDeviceMapping(MainActivity.this, mapping.getPhoneNumber());
                    refreshDeviceMappingsList();
                    updateStatus("Removed device mapping for " + mapping.getPhoneNumber());
                    Toast.makeText(MainActivity.this, "Device mapping removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveConfiguration());
        buttonTest.setOnClickListener(v -> testConnection());
        buttonAddDevice.setOnClickListener(v -> showAddEditDeviceDialog(null));
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

            updateStatus("✓ Configuration saved successfully!");
            Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
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