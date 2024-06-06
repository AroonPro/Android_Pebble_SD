package uk.org.openseizuredetector;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * FragmentCommon - SDK 17 Integral Command Center
 * * en_GB Java Documentation:
 * Primary UI fragment for alarm management and algorithm status.
 * Replaces deprecated 'Time' objects with 'long' timestamps for 2024 stability.
 * * R-FREE STRATEGY:
 * Uses mUtil.getResId to remain decoupled from the R-class, ensuring
 * the 2012 codebase survives the 2026 build environment.
 */
public class FragmentCommon extends FragmentOsdBaseClass {
    private String TAG = "FragmentCommon";

    public FragmentCommon() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        int layoutId = mUtil.getResId("fragment_common", "layout");
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Accept Alarm Button - R-Free
        Button acceptBtn = (Button) view.findViewById(mUtil.getResId("acceptAlarmButton", "id"));
        if (acceptBtn != null) {
            acceptBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mConnection.mBound) {
                        if (mConnection.mSdServer.mSmsTimer != null && mConnection.mSdServer.mSmsTimer.mTimeLeft > 0) {
                            // en_GB: Logic for showing the countdown on screen
                            Log.v(TAG, "SMS Countdown active: " + mConnection.mSdServer.mSmsTimer.mTimeLeft);

                            mConnection.mSdServer.stopSmsTimer();
                        } else {
                            mConnection.mSdServer.acceptAlarm();
                        }
                    }
                }
            });
        }

        // Cancel Audible & Manual Alarm (R-Free binding)
        bindButton(view, "cancelAudibleButton", "cancelAudible");
        bindButton(view, "manualAlarmButton", "raiseManualAlarm");
    }

    private void bindButton(View root, String resName, final String action) {
        Button b = (Button) root.findViewById(mUtil.getResId(resName, "id"));
        if (b != null) {
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mConnection.mBound) {
                        if (action.equals("cancelAudible")) mConnection.mSdServer.cancelAudible();
                        if (action.equals("raiseManualAlarm")) mConnection.mSdServer.raiseManualAlarm();
                    }
                }
            });
        }
    }

    @Override
    protected void updateUi() {
        if (mRootView == null || !isAdded() || !isVisible()) return;

        if (mUtil.isServerRunning() && mConnection.mBound) {
            updateStatusSection();
            updateAlgorithmSection();
            updateDataSourceSection();
        } else {
            showServerStopped();
        }
    }

    private void updateStatusSection() {
        TextView tvStatus = (TextView) mRootView.findViewById(mUtil.getResId("serverStatusTv", "id"));
        if (tvStatus != null) {
            tvStatus.setText("Server Running OK");
            tvStatus.setBackgroundColor(okColour);
        }

        // THE TIME FIX: No more android.text.format.Time
        TextView tvTime = (TextView) mRootView.findViewById(mUtil.getResId("data_time_tv", "id"));
        if (tvTime != null) {
            long now = System.currentTimeMillis();
            // We gebruiken mUtil om de tijd leesbaar te maken (Kirk-proof)
            String timeStr = mUtil.convertTimeUnit(now, "HH:mm:ss");
            tvTime.setText("Time = " + timeStr);
            tvTime.setBackgroundColor(okColour);
        }
    }

    private void updateAlgorithmSection() {
        // Voorbeeld voor OSD Alg - R-Free
        TextView tvOsd = (TextView) mRootView.findViewById(mUtil.getResId("osdAlgTv", "id"));
        if (tvOsd != null) {
            if (mConnection.mSdServer.mSdData.mOsdAlarmActive) {
                tvOsd.setBackgroundColor(okColour);
                tvOsd.setPaintFlags(tvOsd.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tvOsd.setBackgroundColor(warnColour);
                tvOsd.setPaintFlags(tvOsd.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }
    }

    private void updateDataSourceSection() {
        TextView tvSource = (TextView) mRootView.findViewById(mUtil.getResId("dataSourceInfoTv", "id"));
        if (tvSource != null) {
            String source = mConnection.mSdServer.mSdDataSource.mName;
            tvSource.setText("Source: " + source);
            tvSource.setBackgroundColor(okColour);
        }
    }

    private void showServerStopped() {
        TextView tv = (TextView) mRootView.findViewById(mUtil.getResId("serverStatusTv", "id"));
        if (tv != null) {
            tv.setText("SERVER STOPPED");
            tv.setBackgroundColor(warnColour);
        }
    }
}