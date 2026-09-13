package com.traccar.smsgateway;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsListActivity extends AppCompatActivity {

    private EditText editFilterImei;
    private Spinner spinnerSortOrder;
    private Spinner spinnerSendStatus;
    private Spinner spinnerParseStatus;
    private Button buttonStartDate;
    private Button buttonEndDate;
    private Button buttonApplyFilters;
    private Button buttonResetFilters;
    private Button buttonBack;

    private LinearLayout containerTableRows;
    private Button buttonPrevPage;
    private Button buttonNextPage;
    private TextView textPageInfo;

    private DatabaseHelper dbHelper;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 15;
    private int totalCount = 0;

    private Long startTimestamp = null;
    private Long endTimestamp = null;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final SimpleDateFormat datePickerFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_list);

        dbHelper = DatabaseHelper.getInstance(this);

        initViews();
        setupSpinners();
        setupListeners();
        loadSmsData();
    }

    private void initViews() {
        editFilterImei = findViewById(R.id.editFilterImei);
        spinnerSortOrder = findViewById(R.id.spinnerSortOrder);
        spinnerSendStatus = findViewById(R.id.spinnerSendStatus);
        spinnerParseStatus = findViewById(R.id.spinnerParseStatus);
        buttonStartDate = findViewById(R.id.buttonStartDate);
        buttonEndDate = findViewById(R.id.buttonEndDate);
        buttonApplyFilters = findViewById(R.id.buttonApplyFilters);
        buttonResetFilters = findViewById(R.id.buttonResetFilters);
        buttonBack = findViewById(R.id.buttonBack);

        containerTableRows = findViewById(R.id.containerTableRows);
        buttonPrevPage = findViewById(R.id.buttonPrevPage);
        buttonNextPage = findViewById(R.id.buttonNextPage);
        textPageInfo = findViewById(R.id.textPageInfo);
    }

    private void setupSpinners() {
        String[] sortOptions = {"Newest First", "Oldest First"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSortOrder.setAdapter(sortAdapter);

        String[] sendStatusOptions = {"ALL", "PENDING", "SENT", "FAILED"};
        ArrayAdapter<String> sendAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sendStatusOptions);
        sendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSendStatus.setAdapter(sendAdapter);

        String[] parseStatusOptions = {"ALL", "PENDING", "SUCCESS", "FAILED"};
        ArrayAdapter<String> parseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, parseStatusOptions);
        parseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerParseStatus.setAdapter(parseAdapter);
    }

    private void setupListeners() {
        buttonBack.setOnClickListener(v -> finish());

        buttonStartDate.setOnClickListener(v -> showDatePicker(true));
        buttonEndDate.setOnClickListener(v -> showDatePicker(false));

        buttonApplyFilters.setOnClickListener(v -> {
            currentPage = 1;
            loadSmsData();
        });

        buttonResetFilters.setOnClickListener(v -> {
            editFilterImei.setText("");
            spinnerSortOrder.setSelection(0);
            spinnerSendStatus.setSelection(0);
            spinnerParseStatus.setSelection(0);
            startTimestamp = null;
            endTimestamp = null;
            buttonStartDate.setText("Start Date");
            buttonEndDate.setText("End Date");
            currentPage = 1;
            loadSmsData();
        });

        buttonPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                loadSmsData();
            }
        });

        buttonNextPage.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
            if (currentPage < totalPages) {
                currentPage++;
                loadSmsData();
            }
        });
    }

    private void showDatePicker(boolean isStart) {
        final Calendar c = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            if (isStart) {
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                startTimestamp = c.getTimeInMillis();
                buttonStartDate.setText(datePickerFormat.format(c.getTime()));
            } else {
                c.set(Calendar.HOUR_OF_DAY, 23);
                c.set(Calendar.MINUTE, 59);
                c.set(Calendar.SECOND, 59);
                c.set(Calendar.MILLISECOND, 999);
                endTimestamp = c.getTimeInMillis();
                buttonEndDate.setText(datePickerFormat.format(c.getTime()));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadSmsData() {
        String imeiFilter = editFilterImei.getText().toString().trim();
        boolean sortAsc = spinnerSortOrder.getSelectedItemPosition() == 1; // 0 = Newest, 1 = Oldest
        String sendStatus = spinnerSendStatus.getSelectedItem().toString();
        String parseStatus = spinnerParseStatus.getSelectedItem().toString();

        totalCount = dbHelper.getSmsCount(imeiFilter, startTimestamp, endTimestamp, sendStatus, parseStatus);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));
        if (currentPage > totalPages) currentPage = totalPages;

        int offset = (currentPage - 1) * PAGE_SIZE;
        List<DatabaseHelper.SmsRecord> records = dbHelper.querySms(imeiFilter, startTimestamp, endTimestamp, sendStatus, parseStatus, sortAsc, PAGE_SIZE, offset);

        containerTableRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < records.size(); i++) {
            DatabaseHelper.SmsRecord record = records.get(i);
            View rowView = inflater.inflate(R.layout.item_sms_row, containerTableRows, false);

            if (i % 2 == 1) {
                rowView.setBackgroundColor(Color.parseColor("#F9F9F9"));
            }

            TextView textRowId = rowView.findViewById(R.id.textRowId);
            TextView textRowImei = rowView.findViewById(R.id.textRowImei);
            TextView textRowSender = rowView.findViewById(R.id.textRowSender);
            TextView textRowParseStatus = rowView.findViewById(R.id.textRowParseStatus);
            TextView textRowSendStatus = rowView.findViewById(R.id.textRowSendStatus);
            Button buttonRowView = rowView.findViewById(R.id.buttonRowView);

            textRowId.setText(String.valueOf(record.getId()));
            textRowImei.setText(record.getParsedImei() != null ? record.getParsedImei() : "N/A");
            textRowSender.setText(record.getSender());

            // Parse Status style
            textRowParseStatus.setText(record.getParseStatus());
            if ("SUCCESS".equalsIgnoreCase(record.getParseStatus())) {
                textRowParseStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("FAILED".equalsIgnoreCase(record.getParseStatus())) {
                textRowParseStatus.setTextColor(Color.parseColor("#C62828"));
            } else {
                textRowParseStatus.setTextColor(Color.parseColor("#EF6C00"));
            }

            // Send Status style
            textRowSendStatus.setText(record.getSendStatus() + (record.getRetryCount() > 0 ? " (" + record.getRetryCount() + ")" : ""));
            if ("SENT".equalsIgnoreCase(record.getSendStatus())) {
                textRowSendStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("FAILED".equalsIgnoreCase(record.getSendStatus())) {
                textRowSendStatus.setTextColor(Color.parseColor("#C62828"));
            } else {
                textRowSendStatus.setTextColor(Color.parseColor("#EF6C00"));
            }

            buttonRowView.setOnClickListener(v -> showSmsDetailDialog(record));

            containerTableRows.addView(rowView);
        }

        textPageInfo.setText("Page " + currentPage + " of " + totalPages + " (" + totalCount + " items)");
        buttonPrevPage.setEnabled(currentPage > 1);
        buttonNextPage.setEnabled(currentPage < totalPages);
    }

    private void showSmsDetailDialog(DatabaseHelper.SmsRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(record.getId()).append("\n");
        sb.append("Created At: ").append(dateFormat.format(new Date(record.getCreatedAt()))).append("\n");
        sb.append("Updated At: ").append(dateFormat.format(new Date(record.getUpdatedAt()))).append("\n");
        sb.append("Sender: ").append(record.getSender()).append("\n");
        sb.append("Is Binary: ").append(record.isBinary()).append("\n");
        sb.append("Parsed IMEI: ").append(record.getParsedImei() != null ? record.getParsedImei() : "None").append("\n");
        sb.append("Parse Status: ").append(record.getParseStatus()).append("\n");
        sb.append("Send Status: ").append(record.getSendStatus()).append("\n");
        sb.append("Retry Count: ").append(record.getRetryCount()).append("\n");
        sb.append("\n--- Raw Data ---\n").append(record.getRawData()).append("\n");

        if (record.getParsedPayload() != null) {
            sb.append("\n--- Parsed Payload ---\n").append(record.getParsedPayload()).append("\n");
        }
        if (record.getErrorMessage() != null) {
            sb.append("\n--- Error Log ---\n").append(record.getErrorMessage()).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Complete SMS Record Details")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }
}
