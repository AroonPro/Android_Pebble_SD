/*
  OpenSeizureDetector - SdData.java
  Integral en_GB Version - The Central Data Container
  Tailored for Bram-Profile: C-PTSD & Fibromyalgia friendly stability.
  Copyright Graham Jones, 2015-2024.
*/

package uk.org.openseizuredetector;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.Time;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SdData implements Parcelable {
    public boolean mWatchOnBody = false;
    private final static String TAG = "SdData";
    private final static int N_RAW_DATA = 125;  // 5 seconds at 25 Hz.

    // Seizure Detection Algorithm Selection
    public boolean mOsdAlarmActive;
    public boolean mCnnAlarmActive;

    /* Analysis settings */
    public String phoneAppVersion = "";
    public boolean haveSettings = false;
    public boolean haveData = false;
    public short mDataUpdatePeriod;
    public short mMutePeriod;
    public short mManAlarmPeriod;
    public boolean mFallActive;
    public short mFallThreshMin;
    public short mFallThreshMax;
    public short mFallWindow;
    public long mSdMode;
    public long mSampleFreq;
    public long analysisPeriod;
    public long alarmFreqMin;
    public long alarmFreqMax;
    public long alarmThresh;
    public long alarmRatioThresh;
    public long batteryPc;
    public int phoneBatteryPc;

    /* Heart Rate & Oxygen Settings */
    public boolean mHRAlarmActive = false;
    public double mHRThreshMin = 40.0;
    public double mHRThreshMax = 150.0;
    public boolean mO2SatAlarmActive = false;
    public double mO2SatThreshMin = 80.0;

    /* Watch Metadata */
    public String dataSourceName = "";
    public String watchManuf = "";
    public String watchSerNo = "";
    public String watchFwVersion = "";
    public String watchSdVersion = "";

    /* Raw Data Buffers */
    public double[] rawData;
    public double[] rawData3D;
    public int[] simpleSpec;
    public int mNsamp = 0;

    /* Analysis results */
    public Time dataTime = null;
    public float timeDiff = 0f;
    public long alarmState;
    public String alarmPhrase = "";
    public String alarmCause = "";
    public boolean alarmStanding = false;
    public boolean fallAlarmStanding = false; // Add this line to kill the red error
    public boolean watchConnected = false;
    public boolean watchAppRunning = false;
    public boolean serverOK = false;

    public double mHR = 0;
    public double mO2Sat = 0;
    public double mPseizure = 0.0;
    public long maxVal, maxFreq, specPower, roiPower;
    public long lastUpdateMs = 0;

    public String watchSdName = ""; // Voor Firmware/App naam (bv. 'OSD-Garmin')
    public String watchPartNo = ""; // Voor Model nummer (bv. 'Venu 2 Plus')
    public int mDefaultSampleCount = 50;
    public double dT = 0;

    // Buffers for 24h graphing (Requires CircBuf.java)
    public CircBuf watchBattBuff = new CircBuf(24 * 3600 / 5, -1);

    public SdData() {
        simpleSpec = new int[10];
        rawData = new double[N_RAW_DATA];
        rawData3D = new double[N_RAW_DATA * 3];
        dataTime = new Time(Time.getCurrentTimezone());
        dataTime.setToNow();
        lastUpdateMs = System.currentTimeMillis();
    }


    public String statusText = "Initializing...";

    /* * Initialise this SdData object from a JSON String
     */
    public boolean fromJSON(String jsonStr) {
        try {
            JSONObject jo = new JSONObject(jsonStr);
            Time tnow = new Time();
            tnow.setToNow();
            if (dataTime != null) {
                timeDiff = (tnow.toMillis(false) - dataTime.toMillis(false)) / 1000f;
            }
            dataTime.setToNow();
            this.lastUpdateMs = System.currentTimeMillis();

            maxVal = jo.optLong("maxVal");
            maxFreq = jo.optLong("maxFreq");
            specPower = jo.optLong("specPower");
            roiPower = jo.optLong("roiPower");
            batteryPc = jo.optInt("batteryPc");
            watchBattBuff.add(batteryPc);

            watchConnected = jo.optBoolean("watchConnected", true);
            watchAppRunning = jo.optBoolean("watchAppRunning", true);
            serverOK = jo.optBoolean("serverOK", true);

            alarmState = jo.optInt("alarmState");
            alarmPhrase = jo.optString("alarmPhrase");

            mHR = jo.optDouble("hr", 0.0);
            mO2Sat = jo.optDouble("o2Sat", -1.0);

            JSONArray specArr = jo.optJSONArray("simpleSpec");
            if (specArr != null) {
                for (int i = 0; i < Math.min(specArr.length(), simpleSpec.length); i++) {
                    simpleSpec[i] = specArr.optInt(i);
                }
            }

            haveData = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "fromJSON Error: " + e.getMessage());
            haveData = false;
            return false;
        }
    }

    public String toDatapointJSON() {
        try {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("dataTime", dataTime.format("%d-%m-%Y %H:%M:%S"));
            jsonObj.put("alarmState", alarmState);
            jsonObj.put("hr", mHR);
            jsonObj.put("o2Sat", mO2Sat);

            JSONArray specArr = new JSONArray();
            for (int s : simpleSpec) specArr.put(s);
            jsonObj.put("simpleSpec", specArr);

            return jsonObj.toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("osdAlarmState", this.alarmState);
            json.put("batteryPct", this.batteryPc);
            json.put("status", this.statusText);
            // Add other fields as required by the Web API
        } catch (JSONException e) {
            Log.e("SdData", "toJson Error: " + e.getMessage());
        }
        return json;
    }

    public void updateFromJSON(JSONObject json) {
        try {
            this.alarmState = json.optInt("osdAlarmState", 0);
            this.batteryPc = json.optInt("batteryPct", -1);
            this.statusText = json.optString("status", "Unknown");
            // Voeg hier andere velden toe die uit de API of Wear komen
        } catch (Exception e) {
            Log.e("SdData", "Error parsing JSON: " + e.getMessage());
        }
    }
    public double watchSignalStrength = 0;
    // Assuming a buffer size of 120 (10 minutes if 5s updates)
    public CircBuf watchSignalStrengthBuff = new CircBuf(120,0);

    /**
     * toSettingsJSON - Exports all configuration and device metadata.
     * Essential for syncing phone UI with watch capabilities and alarm thresholds.
     */
    public String toSettingsJSON() {
        String retval;
        try {
            JSONObject jsonObj = new JSONObject();
            if (dataTime != null) {
                jsonObj.put("dataTime", dataTime.format("%d-%m-%Y %H:%M:%S"));
                jsonObj.put("dataTimeStr", dataTime.format("%Y%m%dT%H%M%S"));
            } else {
                jsonObj.put("dataTimeStr", "00000000T000000");
                jsonObj.put("dataTime", "00-00-00 00:00:00");
            }

            // System State
            jsonObj.put("batteryPc", batteryPc);
            jsonObj.put("phoneBatteryPc", phoneBatteryPc);
            jsonObj.put("alarmState", alarmState);
            jsonObj.put("alarmPhrase", alarmPhrase);
            jsonObj.put("alarmCause", alarmCause);

            // Seizure Detection Settings
            jsonObj.put("sdMode", mSdMode);
            jsonObj.put("sampleFreq", mSampleFreq);
            jsonObj.put("analysisPeriod", analysisPeriod);
            jsonObj.put("alarmFreqMin", alarmFreqMin);
            jsonObj.put("alarmFreqMax", alarmFreqMax);
            jsonObj.put("alarmThresh", alarmThresh);
            jsonObj.put("alarmRatioThresh", alarmRatioThresh);
            jsonObj.put("osdAlarmActive", mOsdAlarmActive);
            jsonObj.put("cnnAlarmActive", mCnnAlarmActive);

            // Heart Rate & Oxygen Thresholds (Bram-Profile crucial)
            jsonObj.put("hrAlarmActive", mHRAlarmActive);
            jsonObj.put("hrThreshMin", mHRThreshMin);
            jsonObj.put("hrThreshMax", mHRThreshMax);
            jsonObj.put("o2SatAlarmActive", mO2SatAlarmActive);
            jsonObj.put("o2SatThreshMin", mO2SatThreshMin);

            // Watch Hardware Info
            jsonObj.put("dataSourceName", dataSourceName);
            jsonObj.put("phoneAppVersion", phoneAppVersion);
            jsonObj.put("watchManuf", watchManuf);
            jsonObj.put("watchSerNo", watchSerNo);
            jsonObj.put("watchFwVersion", watchFwVersion);
            jsonObj.put("watchSdVersion", watchSdVersion);

            retval = jsonObj.toString();
        } catch (Exception ex) {
            Log.e(TAG, "toSettingsJSON(): Error - " + ex.toString());
            retval = "{\"error\":\"" + ex.toString() + "\"}";
        }
        return retval;
    }

    public String toCSVString(boolean includeRawData) {
        StringBuilder sb = new StringBuilder();
        sb.append(dataTime != null ? dataTime.format("%d-%m-%Y %H:%M:%S") : "00-00-00 00:00:00");
        sb.append(", ").append(specPower).append(", ").append(roiPower);
        sb.append(", ").append(alarmPhrase).append(", ").append(mHR).append(", ").append(mO2Sat);

        if (includeRawData) {
            for (int i = 0; i < mNsamp; i++) sb.append(", ").append(rawData[i]);
        }
        return sb.toString();
    }

    /**
     * Statistics: Average and Standard Deviation
     */
    public double getAvAcc() {
        if (mNsamp == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < mNsamp; i++) sum += rawData[i];
        return sum / mNsamp;
    }

    public double getSdAcc() {
        if (mNsamp < 2) return 0.0;
        double av = getAvAcc();
        double var = 0.0;
        for (int i = 0; i < mNsamp; i++) {
            var += Math.pow(rawData[i] - av, 2);
        }
        return Math.sqrt(var / (mNsamp - 1));
    }

    // --- Parcelable Implementation (Full) ---

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(alarmState);
        dest.writeString(alarmPhrase);
        dest.writeDouble(mHR);
        dest.writeDouble(mO2Sat);
        dest.writeLong(batteryPc);
        dest.writeInt(serverOK ? 1 : 0);
        dest.writeInt(watchConnected ? 1 : 0);
        dest.writeLong(specPower);
        dest.writeLong(roiPower);
        dest.writeString(alarmCause);
    }

    protected SdData(Parcel in) {
        alarmState = in.readLong();
        alarmPhrase = in.readString();
        mHR = in.readDouble();
        mO2Sat = in.readDouble();
        batteryPc = in.readLong();
        serverOK = in.readInt() == 1;
        watchConnected = in.readInt() == 1;
        specPower = in.readLong();
        roiPower = in.readLong();
        alarmCause = in.readString();
    }

    public static final Parcelable.Creator<SdData> CREATOR = new Parcelable.Creator<SdData>() {
        @Override
        public SdData createFromParcel(Parcel in) { return new SdData(in); }
        @Override
        public SdData[] newArray(int size) { return new SdData[size]; }
    };
}