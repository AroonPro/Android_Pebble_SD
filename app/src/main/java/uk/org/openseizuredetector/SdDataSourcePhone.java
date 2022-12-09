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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

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
    private double mSampleTimeUs;
    private double mConversionSampleFactor;


    private PowerManager.WakeLock mWakeLock;

    /**
     * Calculate the static values of requested mSdData.mSampleFreq, mSampleTimeUs and factorDownSampling  through
     * mSdData.analysisPeriod and mSdData.mDefaultSampleCount .
     */
    private void calculateStaticTimings(){
        // default sampleCount : mSdData.mDefaultSampleCount
        // default sampleTime  : mSdData.analysisPeriod
        // sampleFrequency = sampleCount / sampleTime:
        mSdData.mSampleFreq = (long) mSdData.mDefaultSampleCount / mSdData.analysisPeriod;

        // now we have mSampleFreq in number samples / second (Hz) as default.
        // to calculate sampleTimeUs: (1 / mSampleFreq) * 1000 [1s == 1000000us]
        mSampleTimeUs = (1 / mSdData.mSampleFreq) * 1000;

        // num samples == fixed final 250 (NSAMP)
        // time seconds in default == 10 (SIMPLE_SPEC_FMAX)
        // count samples / time = 25 samples / second == 25 Hz max.
        // 1 Hz == 1 /s
        // 25 Hz == 0,04s
        // 1s == 1.000.000 us (sample interval)
        // sampleTime = 40.000 uS == (SampleTime (s) * 1000)
        double mSDDataSampleTimeUs = (double) (mSdData.mNsamp / mSdData.dT) * 1000f;
        mConversionSampleFactor = mSampleTimeUs / mSDDataSampleTimeUs;
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
        calculateStaticTimings();
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.start()");
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // registering listener with reference to (this).onSensorChanged , mSampleTime in MicroSeconds
        // and bufferingTime , sampleTime * 3 in order to save the battery, calling back to mHandler
        mSensorManager.registerListener(this, mSensor, (int) mSampleTimeUs,(int) mSampleTimeUs * 3, mHandler);
        super.start();
    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SdDataSourcePhone.stop()");
        mSensorManager.unregisterListener(this);

        super.stop();
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
                if (mSdData.mNsamp >= mSdData.mDefaultSampleCount) {
                    Log.v(TAG, "onSensorChanged(): Collected Data = final TimeStamp=" + event.timestamp + ", initial TimeStamp=" + mStartTs);
                    double dT = 1.0e-9 * (event.timestamp - mStartTs);
                    mSdData.mSampleFreq = (int) (mSdData.mNsamp / dT);
                    mSdData.haveSettings = true;
                    Log.v(TAG, "onSensorChanged(): Collected data for " + dT + " sec - calculated sample rate as " + mSampleFreq + " Hz");
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
                if (!Objects.equals(mSdData.rawData, null) && mSdData.rawData.length > 0 && mSdData.rawData3D.length > 0) {
                    mSdData.rawData[mSdData.mNsamp] = sqrt(x * x + y * y + z * z);
                    mSdData.rawData3D[3 * mSdData.mNsamp] = x;
                    mSdData.rawData3D[3 * mSdData.mNsamp + 1] = y;
                    mSdData.rawData3D[3 * mSdData.mNsamp + 2] = z;
                    mSdData.mNsamp++;
                    if (mSdData.mNsamp == mSdData.mNsampDefault) {
                        // Calculate the sample frequency for this sample, but do not change mSampleFreq, which is used for
                        // analysis - this is because sometimes you get a very long delay (e.g. when disconnecting debugger),
                        // which gives a very low frequency which can make us run off the end of arrays in doAnalysis().
                        // FIXME - we should do some sort of check and disregard samples with long delays in them.
                        double dT = 1e-9 * (event.timestamp - mStartTs);
                        int sampleFreq = (int) (mSdData.mNsamp / dT);
                        Log.v(TAG, "onSensorChanged(): Collected " + NSAMP + " data points in " + dT + " sec (=" + sampleFreq + " Hz) - analysing...");
                        // DownSample from the 50Hz received frequency to 25Hz and convert to mg.
                        // FIXME - we should really do this properly rather than assume we are really receiving data at 50Hz.
                        int writePosition = 1;

                        for (int i = 0; i < mSdData.mNsamp; i++) {
                            writePosition = (int) Math.floor((double) i / mConversionSampleFactor);
                            mSdData.rawData[writePosition] = 1000. * mSdData.rawData[i] / SensorManager.GRAVITY_EARTH;
                            mSdData.rawData3D[writePosition] = 1000. * mSdData.rawData3D[i] / SensorManager.GRAVITY_EARTH;
                            mSdData.rawData3D[writePosition + 1] = 1000. * mSdData.rawData3D[i + 1] / SensorManager.GRAVITY_EARTH;
                            mSdData.rawData3D[writePosition + 2] = 1000. * mSdData.rawData3D[i + 2] / SensorManager.GRAVITY_EARTH;
                            //Log.v(TAG,"i="+i+", rawData="+mSdData.rawData[i]+","+mSdData.rawData[i/2]);
                        }
                        mSdData.mNsamp /= (int) mConversionSampleFactor;
                        doAnalysis();
                        mSdData.mNsamp = 0;
                        mStartTs = event.timestamp;
                    } else if (mSdData.mNsamp > NSAMP) {
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





