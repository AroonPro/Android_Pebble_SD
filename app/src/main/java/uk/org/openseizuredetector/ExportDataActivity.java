package uk.org.openseizuredetector;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.Date;
import uk.org.openseizuredetector.AndroidSdService;

/**
 * ExportDataActivity - Handles CSV data export from the OSD database.
 * Refactored for en_GB standards and ServiceConnection stability.
 */
public class ExportDataActivity extends AppCompatActivity implements View.OnClickListener {

    private String TAG = "ExportDataActivity";
    private static final int FILE_REQUEST_CODE = 1353;

    private Button mDateBtn, mTimeBtn, mExportBtn;
    private EditText mDateTxt, mTimeTxt, mDurationTxt;
    private ProgressBar mProgressBar;

    private int mYear, mMonth, mDay, mHour, mMinute;
    private double mDuration;
    private Date mEndDate;

    private OsdUtil mUtil;
    private SdServiceConnection mConnection;
    private LogManager mLm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // en_GB Fix: Dynamic layout lookup
        setContentView(getResId("activity_dbquery", "layout"));

        mUtil = new OsdUtil(this, new Handler());

        mDateBtn = findViewById(getResId("dateBtn", "id"));
        mTimeBtn = findViewById(getResId("timeBtn", "id"));
        mExportBtn = findViewById(getResId("exportBtn", "id"));
        mDateTxt = findViewById(getResId("endDateText", "id"));
        mTimeTxt = findViewById(getResId("endTimeText", "id"));
        mDurationTxt = findViewById(getResId("durationText", "id"));
        mProgressBar = findViewById(getResId("exportPb", "id"));

        mDateBtn.setOnClickListener(this);
        mTimeBtn.setOnClickListener(this);
        mExportBtn.setOnClickListener(this);
        mExportBtn.setEnabled(false);

        // Initialize with current time
        final Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        mDateTxt.setText(String.format("%02d-%02d-%04d", mDay, mMonth + 1, mYear));
        mTimeTxt.setText(String.format("%02d:%02d:00", mHour, mMinute));
        mDuration = 2.0;
        mDurationTxt.setText(String.format("%03.1f", mDuration));

        mConnection = new SdServiceConnection(getApplicationContext());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the OSD Service
        Intent intent = new Intent(this, AndroidSdService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        waitForConnection();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection != null && mConnection.mBound) {
            unbindService(mConnection);
            mConnection.mBound = false;
        }
    }

    private void waitForConnection() {
        if (mConnection.mBound && mConnection.mSdService != null) {
            Log.v(TAG, "waitForConnection - Bound!");
            // Cast the generic mSdService to the specific SdService class
            // that actually contains the LogManager (mLm)
            if (mConnection.mSdService instanceof AndroidSdService) {
                mLm = ((AndroidSdService) mConnection.mSdService).mLm;
                mExportBtn.setEnabled(true);
            }
        } else {
            new Handler().postDelayed(this::waitForConnection, 500);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == getResId("dateBtn", "id")) {
            new DatePickerDialog(this, (v, y, m, d) -> {
                mYear = y; mMonth = m; mDay = d;
                mDateTxt.setText(String.format("%02d-%02d-%04d", mDay, mMonth + 1, mYear));
            }, mYear, mMonth, mDay).show();
        }
        else if (view.getId() == getResId("timeBtn", "id")) {
            new TimePickerDialog(this, (v, h, min) -> {
                mHour = h; mMinute = min;
                mTimeTxt.setText(String.format("%02d:%02d:00", mHour, mMinute));
            }, mHour, mMinute, true).show();
        }
        else if (view.getId() == getResId("exportBtn", "id")) {
            prepareExport();
        }
    }

    private void prepareExport() {
        mDuration = mUtil.parseToDouble(mDurationTxt.getText().toString());
        String dateTimeStr = String.format("%04d-%02d-%02dT%02d:%02d:00Z", mYear, mMonth + 1, mDay, mHour, mMinute);
        mEndDate = mUtil.string2date(dateTimeStr);

        showProgressBar();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "osd_export_" + System.currentTimeMillis() + ".csv");
        startActivityForResult(intent, FILE_REQUEST_CODE);
    }

    private void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
        mExportBtn.setVisibility(View.INVISIBLE);
    }

    private void hideProgressBar() {
        runOnUiThread(() -> {
            mProgressBar.setVisibility(View.INVISIBLE);
            mExportBtn.setVisibility(View.VISIBLE);
            mExportBtn.setEnabled(true);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && resultData != null) {
            Uri uri = resultData.getData();
            if (uri != null && mLm != null) {
                // Perform the actual export via LogManager
                mLm.exportToCsvFile(mEndDate, mDuration, uri, (Boolean success) -> {
                    hideProgressBar();
                });
            }
        } else {
            hideProgressBar();
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    private int getResId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }
}