package uk.org.openseizuredetector;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * ReportSeizureActivity - SDK 17 Integral PURE R-FREE
 * * en_GB Java Documentation:
 * Fully decoupled from the generated R class. This ensures that the
 * 2012 'Eggshell' logic remains portable across different build environments
 * without resource-ID collisions during the 2024 LSA audit.
 */
public class ReportSeizureActivity extends AppCompatActivity {
    private String TAG = "ReportSeizureActivity";
    private Context mContext;
    private SdServiceConnection mConnection;
    private OsdUtil mUtil;
    private LogManager mLm;
    private WebApiConnection mWac;

    private int mYear, mMonth, mDay, mHour, mMinute;
    private RadioGroup mEventTypeRg;
    private RadioGroup mEventSubTypeRg;
    private List<String> mEventTypesList = null;
    private HashMap<String, ArrayList<String>> mEventSubTypesHashMap = null;
    private boolean mRedrawEventTypesList = false;
    private boolean mRedrawEventSubTypesList = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        // Gebruik de centrale handler voor UI updates
        mUtil = new OsdUtil(this, new Handler());

        /* * THE R-FREE FIX:
         * In plaats van setContentView(R.layout.activity_report_seizure)
         * zoeken we de integer ID dynamisch op via de String-naam.
         */
        int layoutId = mUtil.getResId("activity_report_seizure", "layout");
        if (layoutId != 0) {
            setContentView(layoutId);
        } else {
            Log.e(TAG, "CRITICAL: activity_report_seizure layout not found!");
            finish();
            return;
        }

        mConnection = new SdServiceConnection(getApplicationContext());
        setupButtons();
        setCurrentTime();
    }

    private void setupButtons() {
        // Alle views worden via mUtil.getResId gevonden (Geen R.id meer)
        mEventTypeRg = (RadioGroup) findViewById(mUtil.getResId("eventTypeRg", "id"));
        mEventSubTypeRg = (RadioGroup) findViewById(mUtil.getResId("eventSubTypeRg", "id"));

        if (mEventTypeRg != null) mEventTypeRg.setOnCheckedChangeListener(onEventTypeChange);
        if (mEventSubTypeRg != null) mEventSubTypeRg.setOnCheckedChangeListener(onEventSubTypeChange);

        bindButton("loginBtn", onOk);
        bindButton("cancelBtn", onCancel);
        bindButton("select_date_button", onSelectDate);
        bindButton("select_time_button", onSelectTime);
    }

    private void bindButton(String name, View.OnClickListener listener) {
        View v = findViewById(mUtil.getResId(name, "id"));
        if (v != null) v.setOnClickListener(listener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // SDK 17: Geen Objects.nonNull, maar de klassieke veilige check
        if (mConnection != null && !mConnection.mBound) {
            mUtil.bindToServer(this, mConnection);
        }
        waitForConnection();
    }

    private void waitForConnection() {
        if (mConnection != null && mConnection.mBound && mConnection.mSdServer != null) {
            initialiseServiceConnection();
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() { waitForConnection(); }
            }, 250);
        }
    }

    private void initialiseServiceConnection() {
        mLm = mConnection.mSdServer.mLm;
        mWac = mLm.mWac;

        if (mWac.isLoggedIn()) {
            mWac.getEventTypes(new WebApiConnection.JSONObjectCallback() {
                @Override
                public void accept(JSONObject eventTypesObj) {
                    if (eventTypesObj != null) {
                        parseEventTypes(eventTypesObj);
                        updateUi();
                    }
                }
            });
        }
    }

    private void parseEventTypes(JSONObject obj) {
        mEventTypesList = new ArrayList<String>();
        mEventSubTypesHashMap = new HashMap<String, ArrayList<String>>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            mEventTypesList.add(key);
            try {
                JSONArray subs = obj.getJSONArray(key);
                ArrayList<String> subList = new ArrayList<String>();
                for (int i = 0; i < subs.length(); i++) subList.add(subs.getString(i));
                mEventSubTypesHashMap.put(key, subList);
            } catch (JSONException e) { Log.e(TAG, "JSON Error", e); }
        }
        mRedrawEventTypesList = true;
        mRedrawEventSubTypesList = true;
    }

    private void updateUi() {
        // R-FREE: Update alle tekst-labels via dynamische ID's
        updateLabel("date_day_tv", String.format("%02d", mDay));
        updateLabel("date_mon_tv", String.format("%02d", mMonth + 1));
        updateLabel("date_year_tv", String.format("%04d", mYear));
        updateLabel("time_hh_tv", String.format("%02d", mHour));
        updateLabel("time_mm_tv", String.format("%02d", mMinute));

        if (mEventTypesList != null && mRedrawEventTypesList) {
            populateRadioGroup(mEventTypeRg, mEventTypesList);
            mRedrawEventTypesList = false;
        }

        handleSubTypes();
    }

    private void updateLabel(String name, String text) {
        int id = mUtil.getResId(name, "id");
        TextView tv = (TextView) findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private void handleSubTypes() {
        if (mEventTypeRg == null) return;
        int checkedId = mEventTypeRg.getCheckedRadioButtonId();
        RadioButton b = (RadioButton) findViewById(checkedId);
        if (b != null && mEventSubTypesHashMap != null) {
            String type = b.getText().toString();
            ArrayList<String> subs = mEventSubTypesHashMap.get(type);
            if (subs != null && mRedrawEventSubTypesList && mEventSubTypeRg != null) {
                populateRadioGroup(mEventSubTypeRg, subs);
                mRedrawEventSubTypesList = false;
            }
        }
    }

    private void populateRadioGroup(RadioGroup rg, List<String> items) {
        if (rg == null) return;
        rg.removeAllViews();
        for (String s : items) {
            RadioButton rb = new RadioButton(this);
            rb.setText(s);
            rg.addView(rb);
        }
    }

    View.OnClickListener onOk = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String dateStr = String.format("%4d-%02d-%02d %02d:%02d:00", mYear, mMonth + 1, mDay, mHour, mMinute);
            String type = getRadioText(mEventTypeRg);
            String subType = getRadioText(mEventSubTypeRg);

            TextView notesTv = (TextView) findViewById(mUtil.getResId("eventNotesTv", "id"));
            String notes = notesTv != null ? notesTv.getText().toString() : "";

            if (mLm != null) {
                mLm.createLocalEvent(dateStr, 5, type, subType, notes, mConnection.mSdServer.mSdData.toSettingsJSON());
                mUtil.showToast("Manual Seizure Logged - LSA Audit Trail Updated");
                finish();
            }
        }
    };

    private String getRadioText(RadioGroup rg) {
        if (rg == null) return "None";
        RadioButton b = (RadioButton) findViewById(rg.getCheckedRadioButtonId());
        return (b != null) ? b.getText().toString() : "Unknown";
    }

    View.OnClickListener onCancel = new View.OnClickListener() {
        @Override
        public void onClick(View v) { finish(); }
    };

    private void setCurrentTime() {
        final Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);
    }

    View.OnClickListener onSelectDate = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new DatePickerDialog(mContext, new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(DatePicker v, int y, int m, int d) {
                    mYear = y; mMonth = m; mDay = d; updateUi();
                }
            }, mYear, mMonth, mDay).show();
        }
    };

    View.OnClickListener onSelectTime = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new TimePickerDialog(mContext, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker v, int h, int min) {
                    mHour = h; mMinute = min; updateUi();
                }
            }, mHour, mMinute, true).show();
        }
    };

    RadioGroup.OnCheckedChangeListener onEventTypeChange = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup g, int id) { mRedrawEventSubTypesList = true; updateUi(); }
    };

    RadioGroup.OnCheckedChangeListener onEventSubTypeChange = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup g, int id) { updateUi(); }
    };

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection != null) {
            mUtil.unbindFromServer(getApplicationContext(), mConnection);
        }
    }
}