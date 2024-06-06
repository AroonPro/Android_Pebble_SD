/*
 * OpenSeizureDetector - FragmentSystem
 * SDK 17 (Android 4.2) Compliant + 2024/2026 Modernised Logic.
 */

package uk.org.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.widget.LinearLayoutCompat;

public class FragmentSystem extends FragmentOsdBaseClass {
    private static final String TAG = "FragmentSystem";

    public FragmentSystem() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // R-STRIP: Dynamic lookup works perfectly in SDK 17
        int layoutId = getContext().getResources().getIdentifier(
                "fragment_system", "layout", getContext().getPackageName());

        if (layoutId == 0) return new View(getContext());
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int settingsBtnId = getViewId("settingsButton");
        if (settingsBtnId != 0) {
            ImageButton button = (ImageButton) view.findViewById(settingsBtnId);
            // SDK 17 COMPLIANT: No Lambdas, use Anonymous Class
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent prefsIntent = new Intent(mContext, PrefActivity.class);
                        mContext.startActivity(prefsIntent);
                    } catch (Exception ex) {
                        Log.e(TAG, "Exception: " + ex.toString());
                    }
                }
            });
        }
    }

    @Override
    protected void updateUi() {
        if (mRootView == null || mContext == null) return;

        // SDK 17 COMPLIANT: Use Runnable instead of Lambda
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    performUiUpdate();
                } catch (Exception e) {
                    Log.e(TAG, "UpdateUi Error: " + e.getMessage());
                }
            }
        });
    }

    private void performUiUpdate() {
        // Battery logic - casting 'long' to 'int' for SDK 17 TextViews
        TextView battTv = (TextView) mRootView.findViewById(getViewId("battTv"));
        if (battTv != null && mConnection.mSdService != null) {
            int watchBatt = (int) mConnection.mSdService.mSdData.batteryPc;
            int phoneBatt = mConnection.mSdService.mSdData.phoneBatteryPc;
            // String.format is safe in SDK 17
            battTv.setText("W: " + watchBatt + "% / P: " + phoneBatt + "%");
        }
        updateAlarmDisplay();
    }

    private void updateAlarmDisplay() {
        TextView alarmTv = (TextView) mRootView.findViewById(getViewId("alarmTv"));
        if (alarmTv == null || mConnection.mSdService == null) return;

        SdData data = mConnection.mSdService.mSdData;
        // Fallback for missing fallAlarmStanding variable
        boolean isFall = (data.alarmState == 5);

        if (data.alarmStanding) {
            alarmTv.setText("ALARM");
            alarmTv.setBackgroundColor(alarmColour);
        } else if (isFall) {
            alarmTv.setText("FALL");
            alarmTv.setBackgroundColor(alarmColour);
        } else {
            alarmTv.setText("OK");
            alarmTv.setBackgroundColor(okColour);
        }
    }

    private int getViewId(String name) {
        return mContext.getResources().getIdentifier(name, "id", mContext.getPackageName());
    }
}