package com.traccar.smsgateway;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class LogViewerActivity extends AppCompatActivity {

    private EditText editFilterTagImei;
    private Spinner spinnerLogLevel;
    private EditText editSearchText;
    private Button buttonStartDate;
    private Button buttonEndDate;
    private Button buttonApplyFilters;
    private Button buttonResetFilters;
    private Button buttonClearLogs;
    private Button buttonBack;

    private TextView textLogsContent;
    private Button buttonPrevPage;
    private Button buttonNextPage;
    private TextView textLogCountInfo;

    private DatabaseHelper dbHelper;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 50;
    private int totalCount = 0;

    private Long startTimestamp = null;
    private Long endTimestamp = null;

    private final SimpleDateFormat datePickerFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        dbHelper = DatabaseHelper.getInstance(this);

        initViews();
        setupSpinners();
        setupListeners();
        loadLogs();
    }

    private void initViews() {
        editFilterTagImei = findViewById(R.id.editFilterTagImei);
        spinnerLogLevel = findViewById(R.id.spinnerLogLevel);
        editSearchText = findViewById(R.id.editSearchText);
        buttonStartDate = findViewById(R.id.buttonStartDate);
        buttonEndDate = findViewById(R.id.buttonEndDate);
        buttonApplyFilters = findViewById(R.id.buttonApplyFilters);
        buttonResetFilters = findViewById(R.id.buttonResetFilters);
        buttonClearLogs = findViewById(R.id.buttonClearLogs);
        buttonBack = findViewById(R.id.buttonBack);

        textLogsContent = findViewById(R.id.textLogsContent);
        buttonPrevPage = findViewById(R.id.buttonPrevPage);
        buttonNextPage = findViewById(R.id.buttonNextPage);
        textLogCountInfo = findViewById(R.id.textLogCountInfo);
    }

    private void setupSpinners() {
        String[] levels = {"ALL", "D", "I", "W", "E"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, levels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLogLevel.setAdapter(adapter);
    }

    private void setupListeners() {
        buttonBack.setOnClickListener(v -> finish());

        buttonClearLogs.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Logs")
                    .setMessage("Are you sure you want to clear all system logs from the database?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        AppLogger.clearLogs();
                        currentPage = 1;
                        loadLogs();
                        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        buttonStartDate.setOnClickListener(v -> showDatePicker(true));
        buttonEndDate.setOnClickListener(v -> showDatePicker(false));

        buttonApplyFilters.setOnClickListener(v -> {
            currentPage = 1;
            loadLogs();
        });

        buttonResetFilters.setOnClickListener(v -> {
            editFilterTagImei.setText("");
            spinnerLogLevel.setSelection(0);
            editSearchText.setText("");
            startTimestamp = null;
            endTimestamp = null;
            buttonStartDate.setText("Start Date");
            buttonEndDate.setText("End Date");
            currentPage = 1;
            loadLogs();
        });

        buttonPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                loadLogs();
            }
        });

        buttonNextPage.setOnClickListener(v -> {
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
            if (currentPage < totalPages) {
                currentPage++;
                loadLogs();
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

    private void loadLogs() {
        String tagOrImei = editFilterTagImei.getText().toString().trim();
        String level = spinnerLogLevel.getSelectedItem().toString();
        String searchText = editSearchText.getText().toString().trim();

        totalCount = dbHelper.getLogsCount(startTimestamp, endTimestamp, tagOrImei, level, searchText);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));
        if (currentPage > totalPages) currentPage = totalPages;

        int offset = (currentPage - 1) * PAGE_SIZE;
        List<AppLogger.LogEntry> logs = AppLogger.queryLogs(startTimestamp, endTimestamp, tagOrImei, level, searchText, PAGE_SIZE, offset);

        if (logs.isEmpty()) {
            textLogsContent.setText("No log entries match the selected filters.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (AppLogger.LogEntry log : logs) {
                sb.append(log.getFormatted()).append("\n");
            }
            textLogsContent.setText(sb.toString());
        }

        textLogCountInfo.setText("Page " + currentPage + " of " + totalPages + " (" + totalCount + " logs)");
        buttonPrevPage.setEnabled(currentPage > 1);
        buttonNextPage.setEnabled(currentPage < totalPages);
    }
}
