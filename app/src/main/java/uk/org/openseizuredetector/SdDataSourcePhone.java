/*
  Android_Pebble_sd - Android alarm client for openseizuredetector..

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015, 2016

  This file is part of pebble_sd.

  Android_Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Android_Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Android_pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/
package uk.org.openseizuredetector;

import static java.lang.Math.sqrt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.checkerframework.checker.units.qual.Length;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * A data source that uses the accelerometer built into the phone to provide seizure detector data for testing purposes.
 * Note that this is unlikely to be useable as a viable seizure detector because the phone must be firmly attached to the part of the body that
 * will shake during a seizure.
 */
public class SdDataSourcePhone extends SdDataSource implements SensorEventListener {
    private String TAG = "SdDataSourcePhone";


    private final static int NSAMP = 250;
    private SensorManager mSensorManager;
    private int mMode = 0;   // 0=check data rate, 1=running
    private SensorEvent mStartEvent = null;
    private long mStartTs = 0;
    public double mSampleFreq = 0;
    private double mSampleTimeUs = -1;
    private int mCurrentMaxSampleCount = -1;
    private double mConversionSampleFactor;
    private SdData mSdDataSettings ;
    private SdServer sdServer;
    private PowerManager.WakeLock mWakeLock;
    private double accelerationCombined = -1d;
    private double gravityScaleFactor;
    private double miliGravityScaleFactor;

    private int mChargingState = 0;
    private boolean mIsCharging = false;
    private int chargePlug = 0;
    private boolean usbCharge = false;
    private boolean acCharge = false;
    private float batteryPct = -1f;
    private IntentFilter batteryStatusIntentFilter = null;
    private Intent batteryStatusIntent;
    private boolean sensorsActive = false;
    private Intent sdServerIntent = null;
    private List<Double> rawDataList;
    private List<Double> rawDataList3D;

    /**
     * Calculate the static values of requested mSdData.mSampleFreq, mSampleTimeUs and factorDownSampling  through
     * mSdData.analysisPeriod and mSdData.mDefaultSampleCount .
     */
    private void calculateStaticTimings(){
        // default sampleCount : mSdData.mDefaultSampleCount
        // default sampleTime  : mSdData.analysisPeriod
        // sampleFrequency = sampleCount / sampleTime:
        mSdData.mSampleFreq = (long) mCurrentMaxSampleCount / mSdDataSettings.analysisPeriod;

        // now we have mSampleFreq in number samples / second (Hz) as default.
        // to calculate sampleTimeUs: (1 / mSampleFreq) * 1000 [1s == 1000000us]
        mSampleTimeUs = (1d / (double) mSdData.mSampleFreq) * 1e6d;

        // num samples == fixed final 250 (NSAMP)
        // time seconds in default == 10 (SIMPLE_SPEC_FMAX)
        // count samples / time = 25 samples / second == 25 Hz max.
        // 1 Hz == 1 /s
        // 25 Hz == 0,04s
        // 1s == 1.000.000 us (sample interval)
        // sampleTime = 40.000 uS == (SampleTime (s) * 1000)
        if (mSdDataSettings.rawData.length>0 && mSdDataSettings.dT >0d){
            double mSDDataSampleTimeUs = 1d/(double) (Constants.SD_SERVICE_CONSTANTS.defaultSampleCount / Constants.SD_SERVICE_CONSTANTS.defaultSampleTime) * 1.0e6;
            mConversionSampleFactor = mSampleTimeUs / mSDDataSampleTimeUs;
        }
        else
            mConversionSampleFactor = 1d;
        if (accelerationCombined != -1d) {
            gravityScaleFactor = (Math.round(accelerationCombined / SensorManager.GRAVITY_EARTH) % 10d);
        }
        else
        {
            gravityScaleFactor = 1d;
        }
        miliGravityScaleFactor = gravityScaleFactor / 1e3;

    }

    /**
     * SdDataSourcePhone Class. This class handles simulation data for
     * the carrier of the phone.
     * @param context : Android context, usually actual class of application or given
     *                  surroundings of parent.
     * @param handler : Handler handles out-of-activity requests.
     * @param sdDataReceiver : Through this object will the child objects of this
     *                         class be available.
     */
    public SdDataSourcePhone(Context context, Handler handler,
                             SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);


        mName = "Phone";
        // Set default settings from XML files (mContext is set by super().
         PreferenceManager.setDefaultValues(mContext,
                R.xml.network_passive_datasource_prefs, true);
        PreferenceManager.setDefaultValues(mContext,
                R.xml.seizure_detector_prefs, true);
        rawDataList = new ArrayList<>();
        rawDataList3D = new ArrayList<>();
        updatePrefs();
        Log.d(TAG,"logging value of mSdData: "+super.mSdData.mDefaultSampleCount);
        //mSdDataSettings = sdDataReceiver.mSdData;
        sdServer = (SdServer) sdDataReceiver;
        mSdDataSettings = pullSdData();
        sdServerIntent = new Intent(context,SdDataSource.class);
        if (!Objects.equals(mSdDataSettings,null))if (mSdDataSettings.mDefaultSampleCount >0d && mSdDataSettings.analysisPeriod > 0d ) {
            calculateStaticTimings();
        }

    }


    private  void bindSensorListeners(){
        if (mSampleTimeUs <= 0d)
        {
            calculateStaticTimings();
            if (mSampleTimeUs <= 0d)
                mSampleTimeUs = SensorManager.SENSOR_DELAY_NORMAL;
        }
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // registering listener with reference to (this).onSensorChanged , mSampleTime in MicroSeconds
        // and bufferingTime , sampleTime * 3 in order to save the battery, calling back to mHandler
        mSensorManager.registerListener(this, mSensor, (int) mSampleTimeUs,(int) mSampleTimeUs * 3, mHandler);
        sensorsActive = true;

    }

    private void unBindSensorListeners(){
        if (sensorsActive)
            mSensorManager.unregisterListener(this);
        sensorsActive = false;
    }

    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.start()");
        super.start();
        bindSensorListeners();
        mIsRunning = true;
    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.stop()");

        super.stop();
        unBindSensorListeners();

        mIsRunning = false;
    }





    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // we initially start in mMode=0, which calculates the sample frequency returned by the sensor, then enters mMode=1, which is normal operation.
            if (mMode == 0) {
                if (mStartEvent == null) {
                    Log.v(TAG, "onSensorChanged(): mMode=0 - checking Sample Rate - mNSamp = " + mSdData.mNsamp);
                    Log.v(TAG, "onSensorChanged(): saving initial event data");
                    mStartEvent = event;
                    mStartTs = event.timestamp;
                    mSdData.mNsamp = 0;
                } else {
                    mSdData.mNsamp++;
                }
                if (mSdData.mNsamp >= mSdDataSettings.mDefaultSampleCount) {
                    Log.v(TAG, "onSensorChanged(): Collected Data = final TimeStamp=" + event.timestamp + ", initial TimeStamp=" + mStartTs);
                    mSdData.dT = 1.0e-9 * (event.timestamp - mStartTs);
                    mCurrentMaxSampleCount = mSdData.mNsamp;
                    mSdData.mSampleFreq = (int) (mSdData.mNsamp / mSdData.dT);
                    mSdData.haveSettings = true;
                    Log.v(TAG, "onSensorChanged(): Collected data for " + mSdData.dT + " sec - calculated sample rate as " + mSampleFreq + " Hz");
                    accelerationCombined = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
                    calculateStaticTimings();
                    mMode = 1;
                    mSdData.mNsamp = 0;
                    mStartTs = event.timestamp;
                }
            } else if (mMode==1) {
                // mMode=1 is normal operation - collect NSAMP accelerometer data samples, then analyse them by calling doAnalysis().
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                //Log.v(TAG,"Accelerometer Data Received: x="+x+", y="+y+", z="+z);
                if (!Objects.equals(rawDataList, null) ) {
                    rawDataList.add( sqrt(x * x + y * y + z * z));
                    rawDataList3D.add((double) x);
                    rawDataList3D.add((double) y);
                    rawDataList3D.add((double) z);
                    mSdData.mNsamp++;
                    if (mSdData.mNsamp == (mCurrentMaxSampleCount -(1/mConversionSampleFactor)) ) {
                        // Calculate the sample frequency for this sample, but do not change mSampleFreq, which is used for
                        // analysis - this is because sometimes you get a very long delay (e.g. when disconnecting debugger),
                        // which gives a very low frequency which can make us run off the end of arrays in doAnalysis().
                        // FIXME - we should do some sort of check and disregard samples with long delays in them.
                        mSdData.dT = 1e-9 * (event.timestamp - mStartTs);
                        int sampleFreq = (int) (mSdData.mNsamp / mSdData.dT);
                        Log.v(TAG, "onSensorChanged(): Collected " + NSAMP + " data points in " + mSdData.dT + " sec (=" + sampleFreq + " Hz) - analysing...");

                        // DownSample from the **Hz received frequency to 25Hz and convert to mg.
                        // FIXME - we should really do this properly rather than assume we are really receiving data at 50Hz.
                        int readPosition = 1;

                        for (int i = 0; i < Constants.SD_SERVICE_CONSTANTS.defaultSampleCount -1; i++) {
                            readPosition = (int) Math.round((double) i / mConversionSampleFactor);
                            if (readPosition < rawDataList.size() -1){
                                mSdData.rawData[i] = miliGravityScaleFactor * rawDataList.get(readPosition) / SensorManager.GRAVITY_EARTH;
                                mSdData.rawData3D[i] = miliGravityScaleFactor * rawDataList3D.get(readPosition) / SensorManager.GRAVITY_EARTH;
                                mSdData.rawData3D[i + 1] = miliGravityScaleFactor * rawDataList3D.get(readPosition + 1) / SensorManager.GRAVITY_EARTH;
                                mSdData.rawData3D[i + 2] = miliGravityScaleFactor * rawDataList3D.get(readPosition + 2) / SensorManager.GRAVITY_EARTH;
                                //Log.v(TAG,"i="+i+", rawData="+mSdData.rawData[i]+","+mSdData.rawData[i/2]);
                            }
                        }
                        rawDataList.clear();
                        rawDataList3D.clear();
                        mSdData.mNsamp = Constants.SD_SERVICE_CONSTANTS.defaultSampleCount;
                        int scale = ((SdServer)mSdDataReceiver).batteryStatusIntent.getIntExtra("scale",-1);
                        int level = ((SdServer)mSdDataReceiver).batteryStatusIntent.getIntExtra("level",-1);
                        mSdData.batteryPc = (long) 100d*(level/scale);
                        doAnalysis();
                        mSdData.mNsamp = 0;
                        mStartTs = event.timestamp;
                    } else if (mSdData.mNsamp > mCurrentMaxSampleCount - 1) {
                        Log.v(TAG, "onSensorChanged(): Received data during analysis - ignoring sample");
                    }
                } else {
                    Log.v(TAG, "onSensorChanged(): Received empty data during analysis - ignoring sample");
                }

            } else {
                Log.v(TAG,"onSensorChanged(): ERROR - Mode "+mMode+" unrecognised");
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.v(TAG,"onAccuracyChanged()");
    }




}





