package uk.org.openseizuredetector;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * EditEventActivity - Handles editing of seizure events via the Web API.
 * Refactored for universal SdServiceConnection architecture.
 */
public class EditEventActivity extends AppCompatActivity {
    private static final String TAG = "EditEventActivity";
    private OsdUtil mUtil;
    private WebApiConnection mWac;
    private LogManager mLm;
    private SdServiceConnection mConnection;
    private final Handler serverStatusHandler = new Handler(Looper.getMainLooper());

    private List<String> mEventTypesList = null;
    private HashMap<String, ArrayList<String>> mEventSubTypesHashMap = null;
    private String mEventId;
    private RadioGroup mEventTypeRg;
    private boolean mEventTypesListChanged = false;
    private RadioGroup mEventSubTypeRg;
    private boolean mEventSubTypesListChanged = false;
    private JSONObject mEventObj;

    // --- 1. DYNAMIC RESOURCE HELPERS ---
    private int resId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private View safeFind(String idName) {
        int id = resId(idName, "id");
        return (id != 0) ? findViewById(id) : null;
    }

    // --- 2. LIFECYCLE ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        int layoutId = resId("activity_edit_event", "layout");
        if (layoutId != 0) setContentView(layoutId);

        mUtil = new OsdUtil(getApplicationContext(), serverStatusHandler);

        // FIX: Use the new generic constructor
        mConnection = new SdServiceConnection(this);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mEventId = extras.getString("eventId");
            Log.v(TAG, "onCreate - mEventId=" + mEventId);
        }

        View cancelBtn = safeFind("cancelBtn");
        if (cancelBtn != null) cancelBtn.setOnClickListener(onCancel);

        View okBtn = safeFind("loginBtn"); // Note: OSD uses loginBtn id for OK in some layouts
        if (okBtn != null) okBtn.setOnClickListener(onOK);

        mEventTypeRg = (RadioGroup) safeFind("eventTypeRg");
        if (mEventTypeRg != null) mEventTypeRg.setOnCheckedChangeListener(onEventTypeChange);

        mEventSubTypeRg = (RadioGroup) safeFind("eventSubTypeRg");
        if (mEventSubTypeRg != null) mEventSubTypeRg.setOnCheckedChangeListener(onEventSubTypeChange);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart()");
        if (mConnection != null && !mConnection.mBound) {
            mConnection.doBindService();
        }
        waitForConnection();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop() - Cleaning up the 2012 Eggshell");

        /* en_GB Java Explanation:
         * We don't use 'getService()' here; that was for the Binder.
         * We use the parked mSdServer to ensure the LogManager (mLm)
         * flushes all data to the disk for your LSA records.
         */
        if (mConnection != null && mConnection.mBound) {
            if (mConnection.mSdServer != null && mConnection.mSdServer.mLm != null) {
                // Zorg dat de 2012 data veilig wordt weggeschreven
                mConnection.mSdServer.mLm.stop();
                Log.d(TAG, "onStop: LogManager flushed successfully.");
            }

            // De 'Kirk' manier om de verbinding los te laten
            // unbindService(mConnection); // Alleen als je de verbinding echt wilt verbreken
        }
    }

    // --- 3. CONNECTION LOGIC ---
    private void waitForConnection() {
        if (mConnection != null && mConnection.mBound && mConnection.mSdService != null) {
            Log.v(TAG, "waitForConnection - Bound!");
            initialiseServiceConnection();
        } else {
            Log.v(TAG, "waitForConnection - waiting...");
            new Handler(Looper.getMainLooper()).postDelayed(this::waitForConnection, 100);
        }
    }

    private void initialiseServiceConnection() {
        // FIX: Cast to AndroidSdService to access mLm
        if (mConnection.mSdService instanceof AndroidSdService) {
            mLm = ((AndroidSdService) mConnection.mSdService).mLm;
            if (mLm != null) {
                mWac = mLm.mWac;
            }
        }

        if (mWac == null) {
            Log.e(TAG, "initialiseServiceConnection: WebApiConnection is NULL");
            return;
        }

        // Fetch Event Types
        mWac.getEventTypes(eventTypesObj -> {
            Log.v(TAG, "onEventTypesReceived");
            if (eventTypesObj == null) {
                mUtil.showToast("Error Retrieving Event Types from Server");
            } else {
                parseEventTypes(eventTypesObj);
                updateUi();
            }
        });

        // Fetch Specific Event
        try {
            mWac.getEvent(mEventId, eventObj -> {
                if (eventObj != null) {
                    mEventObj = eventObj;
                    updateUi();
                } else {
                    mUtil.showToast("Failed to Retrieve Event");
                    finish();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error fetching event: " + e.getMessage());
        }
    }

    private void parseEventTypes(JSONObject eventTypesObj) {
        Iterator<String> keys = eventTypesObj.keys();
        mEventTypesList = new ArrayList<>();
        mEventSubTypesHashMap = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            mEventTypesList.add(key);
            try {
                JSONArray eventSubTypes = eventTypesObj.getJSONArray(key);
                ArrayList<String> subList = new ArrayList<>();
                for (int i = 0; i < eventSubTypes.length(); i++) {
                    subList.add(eventSubTypes.getString(i));
                }
                mEventSubTypesHashMap.put(key, subList);
                mEventTypesListChanged = true;
            } catch (JSONException e) {
                Log.e(TAG, "JSON Parse Error: " + e.getMessage());
            }
        }
    }

    // --- 4. UI UPDATE ---
    private void updateUi() {
        Log.v(TAG, "updateUI");
        if (mEventObj == null) return;

        // Populate Event Type RadioGroup
        if (mEventTypesList != null && mEventTypesListChanged) {
            mEventTypeRg.removeAllViews();
            for (String type : mEventTypesList) {
                RadioButton b = new RadioButton(this);
                b.setText(type);
                mEventTypeRg.addView(b);
            }
            mEventTypesListChanged = false;
        }

        try {
            TextView idTv = (TextView) safeFind("eventIdTv");
            if (idTv != null) idTv.setText(mEventId);

            TextView stateTv = (TextView) safeFind("eventAlarmStateTv");
            if (stateTv != null) {
                String stateStr = mEventObj.optString("osdAlarmState", "0");
                stateTv.setText(mUtil.alarmStatusToString(Integer.parseInt(stateStr)));
            }

            TextView notesTv = (TextView) safeFind("eventNotsTv"); // Matches your XML typo 'Nots'
            if (notesTv != null) notesTv.setText(mEventObj.optString("desc", ""));

            TextView dateTv = (TextView) safeFind("eventDateTv");
            if (dateTv != null) {
                String dateStr = mEventObj.optString("dataTime", "");
                Date d = mUtil.string2date(dateStr);
                dateTv.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d));
            }

            // Sync Seizure Type Selection
            String currentType = mEventObj.getString("type");
            for (int i = 0; i < mEventTypeRg.getChildCount(); i++) {
                RadioButton b = (RadioButton) mEventTypeRg.getChildAt(i);
                if (b.getText().toString().equals(currentType)) b.setChecked(true);
            }

            // Populate Sub-Types
            if (mEventSubTypesHashMap != null && mEventSubTypesListChanged) {
                ArrayList<String> subTypes = mEventSubTypesHashMap.get(currentType);
                if (subTypes != null) {
                    mEventSubTypeRg.removeAllViews();
                    for (String sub : subTypes) {
                        RadioButton b = new RadioButton(this);
                        b.setText(sub);
                        mEventSubTypeRg.addView(b);
                    }
                    mEventSubTypesListChanged = false;
                }
            }

            // Sync Sub-Type Selection
            String currentSub = mEventObj.optString("subType", "");
            for (int i = 0; i < mEventSubTypeRg.getChildCount(); i++) {
                RadioButton b = (RadioButton) mEventSubTypeRg.getChildAt(i);
                if (b.getText().toString().equals(currentSub)) b.setChecked(true);
            }

        } catch (Exception e) {
            Log.e(TAG, "UI Update error: " + e.getMessage());
        }
    }

    // --- 5. LISTENERS ---
    private final View.OnClickListener onCancel = v -> finish();

    private final View.OnClickListener onOK = v -> {
        TextView notesTv = (TextView) safeFind("eventNotsTv");
        try {
            mEventObj.put("desc", notesTv != null ? notesTv.getText() : "");
            mEventObj.put("id", mEventId);

            mWac.updateEvent(mEventObj, result -> {
                if (result != null) {
                    mUtil.showToast("Event Updated OK");
                    finish();
                } else {
                    mUtil.showToast("Error Updating Event");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Save Error: " + e.getMessage());
        }
    };

    private final RadioGroup.OnCheckedChangeListener onEventTypeChange = (group, checkedId) -> {
        RadioButton b = findViewById(checkedId);
        if (b != null) {
            try {
                mEventObj.put("type", b.getText().toString());
                mEventSubTypesListChanged = true;
                updateUi();
            } catch (JSONException e) { e.printStackTrace(); }
        }
    };

    private final RadioGroup.OnCheckedChangeListener onEventSubTypeChange = (group, checkedId) -> {
        RadioButton b = findViewById(checkedId);
        if (b != null) {
            try {
                mEventObj.put("subType", b.getText().toString());
            } catch (JSONException e) { e.printStackTrace(); }
        }
    };
}