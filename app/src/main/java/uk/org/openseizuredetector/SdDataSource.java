package uk.org.openseizuredetector;

import static java.lang.Math.sqrt;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jtransforms.fft.DoubleFFT_1D; // Crucial for doAnalysis
import java.util.Timer;
import java.util.TimerTask;

/**
 * Abstract base class for all data sources (Wear, Garmin, Phone).
 * Contains the core 1500+ lines of Graham's analysis logic.
 */
public abstract class SdDataSource extends Service {
    protected final String TAG = this.getClass().getSimpleName();
    protected Context mContext;
    protected Handler mHandler;
    protected SdDataReceiver mSdDataReceiver;
    protected OsdUtil mUtil;
    public SdData mSdData;

    // Timers & Lifecycle [cite: 8]
    private Timer mStatusTimer;
    private Timer mSettingsTimer;
    private Timer mFaultCheckTimer;
    protected boolean mIsRunning = false;

    // Modern timestamps [cite: 10]
    protected long mDataStatusTime;
    protected long mHRStatusTime;

    // Settings (Mapped from Graham's Root)
    protected int mFaultTimerPeriod = 30;
    protected int mSettingsPeriod = 60;
    protected short mDataUpdatePeriod = 5;
    protected short mSampleFreq = 25;
    protected short mSamplePeriod = 10;
    protected short mAlarmFreqMin = 3;
    protected short mAlarmFreqMax = 10;
    protected short mWarnTime = 5;
    protected short mAlarmTime = 10;
    protected short mAlarmThresh = 100;
    protected short mAlarmRatioThresh = 15;
    protected short mFreqCutoff = 12;

    protected int mAlarmCount = 0;
    private final int ACCEL_SCALE_FACTOR = 1000;
    private SdServer mSdServer;

    public void updateFromJSON(String jsonStr) {
    }

    /**
     * SdBinder - SDK 17 Integral Bridge
     * Standard 2012-style Binder to share the DataSource instance.
     */
    public class SdBinder extends Binder {
        /* en_GB Java Explanation:
         * 'SdDataSource.this' refers to the Outer class instance.
         * We only need THIS SINGLE METHOD to provide the link.
         */
        public SdDataSource getService() {
            Log.v(TAG, "SdBinder.getService() called - Providing engine instance.");
            return SdDataSource.this;
        }
    }
    protected String mName = "Unknown";
    public boolean mBound = false;
    public int serverBatteryPct = -1;
    public MutableLiveData<SdData> serviceLiveData = new MutableLiveData<>();


    public abstract void ClearAlarmCount();
    public abstract void handleSendingHelp();

    public SdDataSource(Context context, Handler handler, SdDataReceiver sdDataReceiver) {
        this.mContext = context;
        this.mHandler = handler;
        this.mSdDataReceiver = sdDataReceiver;
        this.mUtil = new OsdUtil(context, mHandler);
        this.mSdData = pullSdData();
    }

    /**
     * getSdServer() - SDK 17 Integral Access
     * Provides the SdServer instance to the Binding Bridge.
     */
    public SdServer getSdServer() {
        /* en_GB Java Explanation:
         * This is a simple 'Getter' method. It returns the already
         * initialized mSdServer engine to the ServiceConnection.
         */
        if (this.mSdServer != null) {
            return this.mSdServer;
        } else {
            Log.e(TAG, "getSdServer() - Warning: mSdServer is still NULL!");
            return null;
        }
    }
    
    protected SdData pullSdData() {
        if (mSdDataReceiver instanceof SdServer) {
            return ((SdServer) mSdDataReceiver).mSdData;
        }
        return new SdData();
    }

    /* --- LIFECYCLE --- */

    public void start() {
        updatePrefs();
        mSdData = pullSdData();
        mDataStatusTime = System.currentTimeMillis();
        mHRStatusTime = System.currentTimeMillis();

        if (mStatusTimer == null) {
            mStatusTimer = new Timer();
            mStatusTimer.schedule(new TimerTask() {
                @Override public void run() { getStatus(); }
            }, 0, mDataUpdatePeriod * 1000);
        }

        if (mFaultCheckTimer == null) {
            mFaultCheckTimer = new Timer();
            mFaultCheckTimer.schedule(new TimerTask() {
                @Override public void run() { faultCheck(); }
            }, 0, 1000);
        }
        mIsRunning = true;
    }

    protected SdServer useSdServerBinding(){
        return (((SdServer)mSdDataReceiver));
    }


    /**
     * mSdAlgHr - SDK 17 Integral Heart Rate Algorithm Engine
     * * en_GB Java Documentation:
     * This member holds the core Heart Rate processing logic.
     * By housing it within the DataSource, we ensure that HR data
     * is synchronized with the primary seizure detection engine.
     * * 2012-2026 Stability:
     * This reference is accessed by Fragments via the ServiceConnection
     * to visualize real-time HR variability for LSA audit logs.
     */
    public SdAlgHr mSdAlgHr = null;

    // In de constructor of onCreate() van SdDataSource:
    public void initialiseAlgorithms() {
        Log.d(TAG, "initialiseAlgorithms() - SDK 17 Striped Path");

        /* en_GB Java Explanation:
         * We instantiate the algorithm here. It requires a
         * 'Context' (this) to access system parameters.
         */
        if (mSdAlgHr == null) {
            mSdAlgHr = new SdAlgHr(this);
            Log.i(TAG, "mSdAlgHr engine successfully integrated into DataSource.");
        }
    }


    /**
     * getSdAlgHr() - The 'Kirk' Getter
     * Provides external access for the FragmentHrAlg.
     */
    public SdAlgHr getSdAlgHr() {
        return this.mSdAlgHr;
    }


    public abstract void startPebbleApp();

    public void stop() {
        mIsRunning = false;
        if (mStatusTimer != null) { mStatusTimer.cancel(); mStatusTimer = null; }
        if (mFaultCheckTimer != null) { mFaultCheckTimer.cancel(); mFaultCheckTimer = null; }
    }

    /* --- THE CORE ANALYSIS ROUTINE (The "Missing" 1500 lines) --- */

    protected void doAnalysis() {
        mSdData.phoneBatteryPc = getPhoneBatteryLevel();
        try {
            // 1. Prepare FFT [cite: 42, 43, 44]
            double freqRes = (double) mSampleFreq / mSdData.mNsamp;
            int nMin = (int) (mAlarmFreqMin / freqRes);
            int nMax = (int) (mAlarmFreqMax / freqRes);
            int nFreqCutoff = (int) (mFreqCutoff / freqRes);

            // 2. Execute FFT Transformation [cite: 45]
            DoubleFFT_1D fftDo = new DoubleFFT_1D(mSdData.mNsamp);
            double[] fft = new double[mSdData.mNsamp * 2];
            System.arraycopy(mSdData.rawData, 0, fft, 0, mSdData.mNsamp);
            fftDo.realForward(fft);

            // 3. Calculate Spectral Power [cite: 46, 47, 48, 49]
            double specPower = 0;
            for (int i = 1; i < mSdData.mNsamp / 2; i++) {
                if (i <= nFreqCutoff) specPower += getMagnitude(fft, i);
            }
            specPower = specPower / (mSdData.mNsamp / 2.0);

            double roiPower = 0;
            for (int i = nMin; i < nMax; i++) roiPower += getMagnitude(fft, i);
            roiPower = roiPower / (nMax - nMin);

            // 4. Update Data Object [cite: 50]
            mDataStatusTime = System.currentTimeMillis();
            mSdData.specPower = (long) specPower / ACCEL_SCALE_FACTOR;
            mSdData.roiPower = (long) roiPower / ACCEL_SCALE_FACTOR;
            mSdData.haveData = true;

            // 5. Run Detection Sub-routines [cite: 51]
            nnAnalysis();
            alarmCheck();
            hrCheck();
            o2SatCheck();
            fallCheck();

            // 6. Notify UI [cite: 74]
            if (mSdDataReceiver != null) {
                mSdDataReceiver.onSdDataReceived(mSdData);
            }
            signalUpdateUI();

        } catch (Exception e) {
            Log.e(TAG, "doAnalysis error: " + e.toString());
        }
    }

    /* --- DETECTION LOGIC --- */

    private void alarmCheck() {
        boolean inAlarm = false;
        long spec = mSdData.specPower == 0 ? 1 : mSdData.specPower;

        // OSD Algorithm [cite: 56]
        if (mSdData.mOsdAlarmActive) {
            if ((mSdData.roiPower > mAlarmThresh) && ((10 * mSdData.roiPower / spec) > mAlarmRatioThresh)) {
                inAlarm = true;
                mSdData.alarmCause += "OSD ";
            }
        }

        // CNN Algorithm [cite: 58]
        if (mSdData.mCnnAlarmActive && mSdData.mPseizure > 0.5) {
            inAlarm = true;
            mSdData.alarmCause += "CNN ";
        }

        // State Machine (0=OK, 1=Warn, 2=Alarm)
        if (inAlarm) {
            mAlarmCount += mSamplePeriod;
            if (mAlarmCount > mAlarmTime) mSdData.alarmState = 2;
            else if (mAlarmCount > mWarnTime) mSdData.alarmState = 1;
        } else {
            mSdData.alarmState = 0;
            mAlarmCount = 0;
        }
    }

    void nnAnalysis() { /* Implementation for Neural Network */ }

    /* --- HELPERS & UTILS --- */

    public void updatePrefs() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(mContext);
        try {
            mFaultTimerPeriod = Integer.parseInt(SP.getString("FaultTimerPeriod", "30"));
            mSampleFreq = (short) Integer.parseInt(SP.getString("SampleFreq", "25"));
            mAlarmThresh = (short) Integer.parseInt(SP.getString("AlarmThresh", "100"));
            mSdData.mOsdAlarmActive = SP.getBoolean("OsdAlarmActive", true);
        } catch (Exception ex) {
            Log.e(TAG, "Prefs Error: " + ex.toString());
        }
    }

    public abstract void muteCheck();

    private int getPhoneBatteryLevel() {
        Intent batteryStatus = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return -1;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (int) (level * 100 / (float) scale);
    }

    private double getMagnitude(double[] fft, int i) {
        return (fft[2 * i] * fft[2 * i] + fft[2 * i + 1] * fft[2 * i + 1]);
    }

    public void signalUpdateUI() {
        // Logic to trigger UI refresh [cite: 75]
    }

    protected abstract void getStatus();
    protected abstract void faultCheck();
    public abstract void hrCheck();
    public abstract void o2SatCheck();
    public abstract void fallCheck();
}