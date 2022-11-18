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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;


import androidx.wear.remote.interactions.RemoteActivityHelper;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;

import org.json.JSONException;

import kotlin.coroutines.CoroutineContext;


/**
 * Abstract class for a seizure detector data source.  Subclasses include a pebble smart watch data source and a
 * network data source.
 */
public class SdDataSourceAw extends SdDataSource implements DataClient.OnDataChangedListener,
        MessageClient.OnMessageReceivedListener,
        CapabilityClient.OnCapabilityChangedListener {


    private final String TAG = "SdDataSourceAw";
    private Context mContext ;
    private Looper mLooper ;
    private Handler mHandler ;
    private Timer mSettingsTimer;
    private Timer mStatusTimer;
    private Time mStatusTime;
    private boolean mWatchAppRunningCheck = false;
    private int mAppRestartTimeout = 10;  // Timeout before re-starting watch app (sec) if we have not received
    // data after mDataUpdatePeriod
    //private Looper mServiceLooper;
    private int mFaultTimerPeriod = 30;  // Fault Timer Period in sec
    private PebbleKit.PebbleDataReceiver msgDataHandler = null;
    private final String wearableAppCheckPayload = "AppOpenWearable";
    private final String wearableAppCheckPayloadReturnACK = "AppOpenWearableACK";
    private Boolean mWearableDeviceConnected = false;

    private String currentAckFromWearForAppOpenCheck = null;
    private final String APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD";

    private final String MESSAGE_ITEM_RECEIVED_PATH = "/message-item-received";

    private final String MESSAGE_ITEM_OSD_TEST = "/testMsg";
    private final String MESSAGE_ITEM_OSD_DATA = "/data";
    private final String MESSAGE_ITEM_OSD_TEST_RECEIVED = "/testMsg-received";
    private final String MESSAGE_ITEM_OSD_DATA_RECEIVED = "/data-received";

    private final String TAG_GET_NODES = "getnodes1";
    private final String TAG_MESSAGE_RECEIVED = "receive1";

    private MessageEvent mMessageEvent = null;
    private String mWearableNodeUri  = null;
    private CapabilityInfo mCapabilitityInfo ;
    private CapabilityClient mCapabilityClientOsdAW;
    private CapabilityClient mCapabilityClientWear;

    private AWSdCommsListener mMessageClient ;
    private NodeClient mNodeClient;
    private RemoteActivityHelper remoteActivityHelper;
    private Set<Node> wearNodesWithApp = null;
    private List<Node> allConnectedNodes = null;
    private Context activityContext;



    private UUID SD_UUID = UUID.fromString("03930f26-377a-4a3d-aa3e-f3b19e421c9d");
    private int NSAMP = 512;   // Number of samples in fft input dataset.

    private int KEY_DATA_TYPE = 1;
    private int KEY_ALARMSTATE = 2;
    private int KEY_MAXVAL = 3;
    private int KEY_MAXFREQ = 4;
    private int KEY_SPECPOWER = 5;
    private int KEY_SETTINGS = 6;
    private int KEY_ALARM_FREQ_MIN = 7;
    private int KEY_ALARM_FREQ_MAX = 8;
    private int KEY_WARN_TIME = 9;
    private int KEY_ALARM_TIME = 10;
    private int KEY_ALARM_THRESH = 11;
    private int KEY_POS_MIN = 12;       // position of first data point in array
    private int KEY_POS_MAX = 13;       // position of last data point in array.
    private int KEY_SPEC_DATA = 14;     // Spectrum data
    private int KEY_ROIPOWER = 15;
    private int KEY_NMIN = 16;
    private int KEY_NMAX = 17;
    private int KEY_ALARM_RATIO_THRESH = 18;
    private int KEY_BATTERY_PC = 19;
    //private int KEY_SET_SETTINGS =20;  // Phone is asking us to update watch app settings.
    private int KEY_FALL_THRESH_MIN = 21;
    private int KEY_FALL_THRESH_MAX = 22;
    private int KEY_FALL_WINDOW = 23;
    private int KEY_FALL_ACTIVE = 24;
    private int KEY_DATA_UPDATE_PERIOD = 25;
    private int KEY_MUTE_PERIOD = 26;
    private int KEY_MAN_ALARM_PERIOD = 27;
    private int KEY_SD_MODE = 28;
    private int KEY_SAMPLE_FREQ = 29;
    private int KEY_RAW_DATA = 30;
    private int KEY_NUM_RAW_DATA = 31;
    private int KEY_DEBUG = 32;
    private int KEY_DISPLAY_SPECTRUM = 33;
    private int KEY_SAMPLE_PERIOD = 34;
    private int KEY_VERSION_MAJOR = 35;
    private int KEY_VERSION_MINOR = 36;
    private int KEY_FREQ_CUTOFF = 37;

    // Values of the KEY_DATA_TYPE entry in a message
    private int DATA_TYPE_RESULTS = 1;   // Analysis Results
    private int DATA_TYPE_SETTINGS = 2;  // Settings
    private int DATA_TYPE_SPEC = 3;      // FFT Spectrum (or part of a spectrum)
    private int DATA_TYPE_RAW = 4;       // raw accelerometer data.

    // Values for SD_MODE
    private int SD_MODE_FFT = 0;     // The original OpenSeizureDetector mode (FFT based)
    private int SD_MODE_FILTER = 2;  // Use digital filter rather than FFT.

    private short mDataUpdatePeriod;
    private short mMutePeriod;
    private short mManAlarmPeriod;
    private short mPebbleSdMode;
    private short mSampleFreq;
    private short mAlarmFreqMin;
    private short mAlarmFreqMax;
    private short mSamplePeriod;
    private short mWarnTime;
    private short mAlarmTime;
    private short mAlarmThresh;
    private short mAlarmRatioThresh;
    private boolean mFallActive;
    private short mFallThreshMin;
    private short mFallThreshMax;
    private short mFallWindow;
    private static Context lckContext;
    // raw data storage for SD_MODE_RAW
    private int MAX_RAW_DATA = 500;
    private double[] rawData = new double[MAX_RAW_DATA];
    private int nRawData = 0;


    public SdDataSourceAw(Context context, Handler handler,
                          SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mContext = context;
        lckContext = context;
        Log.e(TAG,"starting to init cntexts");
        mName = "Android Wear";
        // Set default settings from XML files (mContext is set by super().
        PreferenceManager.setDefaultValues(mContext,
                R.xml.seizure_detector_prefs, true);
    }

    //Function to return Activity from given Context
    public Activity getActivity(Context context)
    {
        if (context == null)
        {
            return null;
        }
        else if (context instanceof ContextWrapper)
        {
            if (context instanceof Activity)
            {
                return (Activity) context;
            }
            else
            {
                return getActivity(((ContextWrapper) context).getBaseContext());
            }
        }

        return null;
    }

    public void findWearDevicesWithApp()
    {
        mNodeClient = Wearable.getNodeClient(mContext);
        try
        {

            Task<List<Node>> connectedNodes = mNodeClient.getConnectedNodes();

            connectedNodes.addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            allConnectedNodes = task.getResult();
                            Log.v(TAG,"Found " + allConnectedNodes.size() + "connected nodes") ;
                            for (Node node: allConnectedNodes
                                 ) {
                                Log.d (TAG,"id "+node.getId() + " " +node.getDisplayName());
                                //initialiseDevicePairing(tempAct);

                            }


                        } else {
                            Log.e(TAG,"Task get connected Nodes failed");
                        }
                        if (task.isCanceled()) {
                            Log.e(TAG,"Task get connected Nodes failed due cancellation");
                        }
                    }
            );
        }catch (CancellationException e) {
            Log.e(TAG,"Task get connected Nodes failed due cancellation in try");
        }catch (Throwable throwable){
            Log.d(TAG,"Node request failed to return any results.");
        }
    }

    private void initialiseDevicePairing(Activity tempAct) {
        boolean[] getNodesResBool = new boolean[0];
        Log.d(TAG,"Current context in initDevsP: " +tempAct.toString());
        try{
            Context lmContext = tempAct.getApplicationContext();

            getNodesResBool = getNodes(lmContext);
        }catch (Exception e){
            e.printStackTrace();
        }
        try {
            if (getNodesResBool[0]) {
                // If message Acknowledgment Received
                if (getNodesResBool[1]) {
                    mWearableDeviceConnected = true;
                    mSdData.watchConnected = true;
                } else {
                    mWearableDeviceConnected = false;
                    mSdData.watchConnected = false;

                }
            } else {
                //no Wearable devices found
                Log.e(TAG, "No Wearables found.");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private boolean[] getNodes(Context context){
        //HashSet<String> nodeResults = new HashSet<String>();
        boolean[] resBool = new boolean[2];
        //resBool[0]: nodePresent
        //resBook[1]: wearableReturnAckReceived
        Task<List<Node>> nodeListTask = mNodeClient.getConnectedNodes();
        try {
            Tasks.await(nodeListTask);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        try{
            if (nodeListTask.isSuccessful())
            {
                for (Node node: nodeListTask.getResult()) {
                    Log.v(TAG, "Task fetched nodes");
                    try {
                        boolean result;
                        String nodeId = node.getId();
                        byte[] payload = wearableAppCheckPayload.getBytes(StandardCharsets.UTF_8);
                        Task<Integer> sendMessageTask = Wearable.getMessageClient(mContext)
                                .sendMessage(nodeId, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload);
                        try {
                            Tasks.await(sendMessageTask);
                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            result = sendMessageTask.isSuccessful();
                            Log.d(TAG, "Send message result: " + sendMessageTask.getResult().toString());
                            resBool[0] = true;

                            if (!Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG_GET_NODES, "ACK thread sleep 1");
                            }
                            if (Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                mWearableNodeUri = nodeId;
                                resBool[1] = true;
                                return resBool;
                            }
                            //Wait 3
                            if (!Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG_GET_NODES, "ACK thread sleep 3");
                            } else if (Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                resBool[1] = true;
                                mWearableNodeUri = nodeId;
                                return resBool;
                            }
                            //Wait 4
                            else if (!Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG_GET_NODES, "ACK thread sleep 4");
                            } else if (Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                resBool[1] = true;
                                mWearableNodeUri = nodeId;
                                return resBool;
                            } else if (!Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                //wait 5

                                try {
                                    Thread.sleep(350);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG_GET_NODES, "ACK thread sleep 5");
                            } else if (Objects.equals(currentAckFromWearForAppOpenCheck, wearableAppCheckPayloadReturnACK)) {
                                resBool[1] = true;
                                mWearableNodeUri = nodeId;
                                return resBool;
                            }
                            //resBool[1] = false; redundant stays false
                            Log.d(
                                    TAG_GET_NODES,
                                    "ACK thread timeout, no message received from the wearable "
                            );


                        } catch (Exception e) {
                            Log.e(TAG, "Exception in getting results: " + e.getLocalizedMessage());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception in getting results: " + e.getLocalizedMessage());
                    }
                }

            }

        }catch (Exception e)
        {
            Log.e(TAG, "Exception in getting results: " + e.getLocalizedMessage());
        }
        Log.e(TAG,"Failed to get reply ");
        return resBool;
    }

    public void findAllWearDevices()
    {
        try
        {
            Task<CapabilityInfo> capabilityInfoOsdAW = mCapabilityClientOsdAW
                    .getCapability(Uri.parse("wear://").toString(),CapabilityClient.FILTER_REACHABLE);

            capabilityInfoOsdAW.addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            wearNodesWithApp = task.getResult().getNodes();
                            Log.v(TAG,"Found " + wearNodesWithApp.size() + "nodes") ;


                        } else {
                            Log.e(TAG,"Task failed to complete to get compatible nodes");

                        }
                        if (task.isCanceled()) {
                            Log.e(TAG,"Task get connected Nodes failed due cancellation");
                        }
                    }
            );
            Task<CapabilityInfo> capabilityInfo = mCapabilityClientOsdAW
                    .getCapability(String.valueOf(CAPABILITY_WEAR_APP), CapabilityClient.FILTER_ALL);

            capabilityInfo.addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            wearNodesWithApp = task.getResult().getNodes();
                            Log.v(TAG,"Found " + wearNodesWithApp.size() + "nodes") ;

                        } else {
                            Log.e(TAG,"Task failed to complete to get compatible nodes");

                        }
                        if (task.isCanceled()) {
                            Log.e(TAG,"Task get connected Nodes failed due cancellation");
                        }
                    }
            );
        }catch (CancellationException e) {
            Log.e(TAG,"Task get connected Nodes failed due cancellation");
        }catch (Throwable throwable){
            Log.d(TAG,"Node request failed to return any results. " + throwable);
        }
    }
    private Activity tempAct;
    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start()
    {

        Log.v(TAG, "start()");

        try
        {
            //if (Objects.equals(mContext,null)){
            //    mContext = (ContextWrapper)this;
            //}
            this.activityContext =  this.mContext;

            Intent mTempIntent = new Intent()
                    .setClass(mContext,this.getClass())
                    .setAction(this.getClass().getName())
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

            tempAct = (Activity)((MainActivity)mContext);
            updatePrefs();

            remoteActivityHelper = new RemoteActivityHelper(
                    mContext,
                    Executors.newSingleThreadExecutor());
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to get initiate remoteActivityHelper" + e);
        }

        try
        {
            if (mContext != null)
            {
                mNodeClient = Wearable.getNodeClient(mContext);
                mCapabilityClientWear = Wearable.getCapabilityClient(mContext);
                mCapabilityClientOsdAW = Wearable.getCapabilityClient(mContext);
                Wearable.getMessageClient(mContext).addListener(this);
                findAllWearDevices();
                if (!mWearableDeviceConnected)
                {
                    if ( !Objects.equals(tempAct,null)) {
                        initialiseDevicePairing(tempAct);
                        findWearDevicesWithApp();
                    }
                    else
                    {
                        Log.e(TAG, "mContext && tmpAct null");
                    }
                }
                mCapabilityClientWear.addListener(this, CAPABILITY_WEAR_APP, CapabilityClient.FILTER_REACHABLE);


                if (!wearNodesWithApp.isEmpty())
                {
                    mWearableDeviceConnected = true;
                    mSdData.watchConnected = true;
                } else if (!allConnectedNodes.isEmpty()) {
                    //TODO installer
                } else {
                    Log.e(TAG, "No nodes detected. Defaulting to Phone");
                    //TODO Default to Phone
                }
                Log.d(TAG, "Passed finding nodes");
            }

        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed executing code Wearable,connect" + e);
        }

        try
        {
            // Start timer to check status of pebble regularly.
            mStatusTime = new Time(Time.getCurrentTimezone());
            // use a timer to check the status of the pebble app on the same frequency
            // as we get app data.
            if (mStatusTimer == null) {
                Log.v(TAG, "start(): starting status timer");
                mUtil.writeToSysLogFile("SdDataSourceAw.start() - starting status timer");
                mStatusTimer = new Timer();
                mStatusTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        getPebbleStatus();
                    }
                }, 0, mDataUpdatePeriod * 1000);
            } else {
                Log.v(TAG, "start(): status timer already running.");
                mUtil.writeToSysLogFile("SdDataSourceAw.start() - status timer already running??");
            }
            if (mWearableDeviceConnected)
            {
                final String nodeId = mMessageEvent.getSourceNodeId();
                //set the data f the message to the butes of the Uri.
                final byte[] payload = "connect_init".getBytes(StandardCharsets.UTF_8);
                final Task<Integer> sendMessageTask = Wearable.getMessageClient(mContext)
                        .sendMessage(nodeId, MESSAGE_ITEM_RECEIVED_PATH, payload);
                try {
                    // Block on a task and get the result synchronously (because this is on a background thread).
                    Tasks.await(sendMessageTask);
                } catch (ExecutionException | InterruptedException ex) {
                    ex.printStackTrace();
                }
                if (sendMessageTask.isSuccessful()) {
                    mWearableDeviceConnected = true;

                } else {
                    Log.e(TAG_MESSAGE_RECEIVED, "Failed to send message " + Arrays.toString(payload));
                }


                Log.d(TAG_MESSAGE_RECEIVED, "");


            }

            // make sure we get some data when we first start.
            getWatchData();
            // Start timer to retrieve pebble settings regularly.
            getWatchSdSettings();
            if (mSettingsTimer == null) {
                Log.v(TAG, "start(): starting settings timer");
                mUtil.writeToSysLogFile("SdDataSourceAw.start() - starting settings timer");
                mSettingsTimer = new Timer();
                // period between requesting settings in seconds.
                int mSettingsPeriod = 60;
                mSettingsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        //mUtil.writeToSysLogFile("SdDataSourceAw.mSettingsTimer timed out.");
                        getWatchSdSettings();
                    }
                }, 0, 1000L * mSettingsPeriod);  // ask for settings less frequently than we get data
            } else {
                Log.v(TAG, "start(): settings timer already running.");
                mUtil.writeToSysLogFile("SdDataSourceAw.start() - settings timer already running??");
            }
        } catch (Exception e) {
            Log.e(TAG, "Start failed completly" + e);
        }
        try
        {
            super.start();
            mUtil.writeToSysLogFile("SdDataSourceAw.start()");
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to load super in class");
        }

    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.v(TAG, "stop()");
        mUtil.writeToSysLogFile("SdDataSourceAw.stop()");
        try {
            // Stop the status timer
            if (mStatusTimer != null) {
                Log.v(TAG, "stop(): cancelling status timer");
                mUtil.writeToSysLogFile("SdDataSourceAw.stop() - cancelling status timer");
                mStatusTimer.cancel();
                mStatusTimer.purge();
                mStatusTimer = null;
            }
            // Stop the settings timer
            if (mSettingsTimer != null) {
                Log.v(TAG, "stop(): cancelling settings timer");
                mUtil.writeToSysLogFile("SdDataSourceAw.stop() - cancelling settings timer");
                mSettingsTimer.cancel();
                mSettingsTimer.purge();
                mSettingsTimer = null;
            }
            // Stop pebble message handler.
            Log.v(TAG, "stop(): stopping pebble server");
            mUtil.writeToSysLogFile("SdDataSourceAw.stop() - stopping pebble server");
            stopPebbleServer();


            Wearable.getDataClient(mContext).removeListener(this);
            Wearable.getMessageClient(mContext).removeListener(this);
            Wearable.getCapabilityClient(mContext).removeListener(this);
            mCapabilityClientWear.removeListener(this, String.valueOf(Uri.parse("wear://")));
            mCapabilityClientOsdAW.removeListener(this, String.valueOf(CAPABILITY_WEAR_APP));

        } catch (Exception e) {
            Log.v(TAG, "Error in stop() - " + e.toString());
            mUtil.writeToSysLogFile("SdDataSourceAw.stop() - error - "+e.toString());
        }
    }

    /**
     * updatePrefs() - update basic settings from the SharedPreferences
     * - defined in res/xml/SdDataSourceAwPrefs.xml
     */
    public void updatePrefs() {
        Log.v(TAG, "updatePrefs()");
        mUtil.writeToSysLogFile("SdDataSourceAw.updatePrefs()");
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        try {
            // Parse the AppRestartTimeout period setting.
            try {
                String appRestartTimeoutStr = SP.getString("AppRestartTimeout", "10");
                mAppRestartTimeout = Integer.parseInt(appRestartTimeoutStr);
                Log.v(TAG, "updatePrefs() - mAppRestartTimeout = " + mAppRestartTimeout);
            } catch (Exception ex) {
                Log.v(TAG, "updatePrefs() - Problem with AppRestartTimeout preference!");
                Toast toast = Toast.makeText(mContext, "Problem Parsing AppRestartTimeout Preference", Toast.LENGTH_SHORT);
                toast.show();
            }

            // Parse the FaultTimer period setting.
            try {
                String faultTimerPeriodStr = SP.getString("FaultTimerPeriod", "30");
                mFaultTimerPeriod = Integer.parseInt(faultTimerPeriodStr);
                Log.v(TAG, "updatePrefs() - mFaultTimerPeriod = " + mFaultTimerPeriod);
            } catch (Exception ex) {
                Log.v(TAG, "updatePrefs() - Problem with FaultTimerPeriod preference!");
                Toast toast = Toast.makeText(mContext, "Problem Parsing FaultTimerPeriod Preference", Toast.LENGTH_SHORT);
                toast.show();
            }


            // Watch Settings
            String prefStr;

            prefStr = SP.getString("PebbleDebug", "SET_FROM_XML");
            short mDebug = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() Debug = " + mDebug);

            prefStr = SP.getString("PebbleDisplaySpectrum", "SET_FROM_XML");
            short mDisplaySpectrum = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() DisplaySpectrum = " + mDisplaySpectrum);

            prefStr = SP.getString("PebbleUpdatePeriod", "SET_FROM_XML");
            mDataUpdatePeriod = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() DataUpdatePeriod = " + mDataUpdatePeriod);

            prefStr = SP.getString("MutePeriod", "SET_FROM_XML");
            mMutePeriod = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() MutePeriod = " + mMutePeriod);

            prefStr = SP.getString("ManAlarmPeriod", "SET_FROM_XML");
            mManAlarmPeriod = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() ManAlarmPeriod = " + mManAlarmPeriod);

            prefStr = SP.getString("PebbleSdMode", "SET_FROM_XML");
            mPebbleSdMode = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() PebbleSdMode = " + mPebbleSdMode);

            prefStr = SP.getString("SampleFreq", "SET_FROM_XML");
            mSampleFreq = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() SampleFreq = " + mSampleFreq);

            prefStr = SP.getString("SamplePeriod", "SET_FROM_XML");
            mSamplePeriod = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AnalysisPeriod = " + mSamplePeriod);

            prefStr = SP.getString("AlarmFreqMin", "SET_FROM_XML");
            mAlarmFreqMin = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmFreqMin = " + mAlarmFreqMin);

            prefStr = SP.getString("AlarmFreqMax", "SET_FROM_XML");
            mAlarmFreqMax = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmFreqMax = " + mAlarmFreqMax);

            prefStr = SP.getString("WarnTime", "SET_FROM_XML");
            mWarnTime = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() WarnTime = " + mWarnTime);

            prefStr = SP.getString("AlarmTime", "SET_FROM_XML");
            mAlarmTime = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmTime = " + mAlarmTime);

            prefStr = SP.getString("AlarmThresh", "SET_FROM_XML");
            mAlarmThresh = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmThresh = " + mAlarmThresh);

            prefStr = SP.getString("AlarmRatioThresh", "SET_FROM_XML");
            mAlarmRatioThresh = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() AlarmRatioThresh = " + mAlarmRatioThresh);

            mFallActive = SP.getBoolean("FallActive", false);
            Log.v(TAG, "updatePrefs() FallActive = " + mFallActive);

            prefStr = SP.getString("FallThreshMin", "SET_FROM_XML");
            mFallThreshMin = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallThreshMin = " + mFallThreshMin);

            prefStr = SP.getString("FallThreshMax", "SET_FROM_XML");
            mFallThreshMax = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallThreshMax = " + mFallThreshMax);

            prefStr = SP.getString("FallWindow", "SET_FROM_XML");
            mFallWindow = (short) Integer.parseInt(prefStr);
            Log.v(TAG, "updatePrefs() FallWindow = " + mFallWindow);

        } catch (Exception ex) {
            Log.v(TAG, "updatePrefs() - Problem parsing preferences!");
            mUtil.writeToSysLogFile("SdDataSourceAw.updatePrefs() - ERROR "+ex.toString());
            Toast toast = Toast.makeText(mContext, "Problem Parsing Preferences - Something won't work - Please go back to Settings and correct it!", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    @Override
    public void onDataChanged( DataEventBuffer dataEventBuffer) {

    }

    @Override
    public void onMessageReceived( MessageEvent messageEvent) {
        Log.d(TAG_MESSAGE_RECEIVED, "onMessageReceived event received");
        final String s = new String( messageEvent.getData(),StandardCharsets.UTF_8);
        final String messageEventPath = messageEvent.getPath();
        Log.d(
                TAG_MESSAGE_RECEIVED,
                "onMessageReceived() A message from watch was received:"
                        + messageEvent.getRequestId()
                        + " "
                        + messageEventPath
                        + " "
                        + s
        );
        //Send back a message back to the source node
        //This acknowledges that the receiver activity is open
        if (Objects.equals(messageEventPath,APP_OPEN_WEARABLE_PAYLOAD_PATH)) {
            // Get the node id of the node that created the data item from the host portion of
            // the uri.
            final String nodeId = messageEvent.getSourceNodeId();
            // Set the data of the message to be the bytes of the Uri.
            final String returnPayloadAck = wearableAppCheckPayloadReturnACK;
            final byte[] payload = (wearableAppCheckPayloadReturnACK.getBytes(StandardCharsets.UTF_8));

            Log.d(
                    TAG_MESSAGE_RECEIVED,
                    "Acknowledgement message successfully with payload : " + returnPayloadAck
            );
            mMessageEvent = messageEvent;
            mWearableNodeUri = messageEvent.getSourceNodeId();
            try
            {

                Log.d(TAG_MESSAGE_RECEIVED, "");
            } catch (Exception e) {

            }
        }
        else if (!messageEventPath.isEmpty() && messageEventPath.equals(MESSAGE_ITEM_OSD_DATA)){

            try {
                    mSdData.fromJSON(s);
                    mSdDataReceiver.onSdDataReceived(mSdData);
                }
             catch (Exception e) {
                Log.e(TAG,"Exception in updating msddata");
            }
        }

    }
    private Boolean CheckIsWearClient(CapabilityInfo capabilityInfo){
        Log.v(TAG,"In checking if there is a wear client");
        if (capabilityInfo.equals(Uri.parse("wear://"))) {
            CapabilityInfo mMobileNodesWithCompatibility = capabilityInfo;
            if (mMobileNodesWithCompatibility.getNodes().isEmpty()) {
                Wearable.getMessageClient(mContext).removeListener(this);
                mWearableDeviceConnected = false;
            } else {
                Wearable.getMessageClient(mContext).addListener(this);
                mWearableDeviceConnected = true;
            }
        }
        return mWearableDeviceConnected;
    }
    @Override
    public void onCapabilityChanged( CapabilityInfo capabilityInfo) {
        try {
            CheckIsWearClient(capabilityInfo);
        } catch (Exception e)
        {
            Log.e(TAG,"Failed with " + e + " in check is Wear Client") ;
        }
        mCapabilitityInfo = capabilityInfo;
        findAllWearDevices();
    }


    class AWSdCommsListener extends WearableListenerService {
        String TAG = "AWSdCommsListener";
        public String SERVICE_CALLED_WEAR = "WearListClicked";

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            super.onMessageReceived(messageEvent);

            String event = messageEvent.getPath();

            Log.d(TAG, event);

            String [] message = event.split("--");

            if (message[0].equals(SERVICE_CALLED_WEAR)) {
                Log.d(TAG,"message detected");
                Log.v(TAG, "Setting mWatchAppRunningCheck to true");
                mWatchAppRunningCheck = true;
            }
        }
    }


    /**
     * Set this server to receive pebble data by registering it as
     * A PebbleDataReceiver
     */
    private void startPebbleServer() {
        Log.v(TAG, "StartPebbleServer()");
        mUtil.writeToSysLogFile("SdDataSourceAw.startPebbleServer()");
        final Handler handler = new Handler();
        msgDataHandler = new PebbleKit.PebbleDataReceiver(SD_UUID) {
            @Override
            public void receiveData(final Context context,
                                    final int transactionId,
                                    final PebbleDictionary data) {

            }
        };
        PebbleKit.registerReceivedDataHandler(mContext, msgDataHandler);
        // We struggle to connect to pebble time if app is already running,
        // so stop app so we can re-connect to it.
        //stopWatchApp();
        startWatchApp();
    }

    /**
     * De-register this server from receiving pebble data
     */
    public void stopPebbleServer() {
        Log.v(TAG, "stopServer(): Stopping Pebble Server");
        Log.v(TAG, "stopServer(): msgDataHandler = " + msgDataHandler.toString());
        mUtil.writeToSysLogFile("SdDataSourceAw.stopServer()");
        try {
            mContext.unregisterReceiver(msgDataHandler);
            stopWatchApp();
        } catch (Exception e) {
            Log.v(TAG, "stopServer() - error " + e.toString());
            mUtil.writeToSysLogFile("SdDataSourceAw.stopServer() - error " + e.toString());
        }
    }

    /**
     * Attempt to start the pebble_sd watch app on the pebble watch.
     */
    public void startWatchApp() {
        Log.v(TAG, "startWatchApp() - closing app first");
        mUtil.writeToSysLogFile("SdDataSourceAw.startWatchApp() - closing app first");
        // first close the watch app if it is running.

        Log.v(TAG, "startWatchApp() - starting watch app after 5 seconds delay...");
	// Wait 5 seconds then start the app.
        Timer appStartTimer = new Timer();
        appStartTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.v(TAG, "startWatchApp() - starting watch app...");
                mUtil.writeToSysLogFile("SdDataSourceAw.startWatchApp() - starting watch app");
                // FIXME - Make this work with Android Wear

            }
        }, 5000);
    }

    /**
     * stop the pebble_sd watch app on the pebble watch.
     */
    public void stopWatchApp() {
        Log.v(TAG, "stopWatchApp()");
        mUtil.writeToSysLogFile("SdDataSourceAw.stopWatchApp()");
        // FIXME - Make this work with Android Wear
    }


    /**
     * Send our latest settings to the watch, then request Pebble App to send
     * us its latest settings so we can check it has been set up correctly..
     * Will be received as a message by the receiveData handler
     */
    public void getWatchSdSettings() {
        Log.v(TAG, "getWatchSdSettings() - sending required settings to pebble");
        mUtil.writeToSysLogFile("SdDataSourceAw.getWatchSdSettings()");
        sendWatchSdSettings();
        //Log.v(TAG, "getWatchSdSettings() - requesting settings from pebble");
        //mUtil.writeToSysLogFile("SdDataSourceAw.getWatchSdSettings() - and request settings from pebble");

        // FIXME - make this work with Android Wear
    }

    /**
     * Send the watch settings that are stored as class member
     * variables to the watch.
     */
    public void sendWatchSdSettings() {
        Log.v(TAG, "sendWatchSdSettings() - preparing settings dictionary.. mSampleFreq=" + mSampleFreq);
        mUtil.writeToSysLogFile("SdDataSourceAw.sendWatchSdSettings()");

        byte[] payload = mSdData.toSettingsJSON().getBytes(StandardCharsets.UTF_8);
        Task<Integer> sendMessageTask = Wearable.getMessageClient(mContext)
                .sendMessage(mWearableNodeUri, APP_OPEN_WEARABLE_PAYLOAD_PATH, payload);
        try {
            Tasks.await(sendMessageTask);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        try {
            Log.v(TAG,"Message send with state: + " + sendMessageTask.isSuccessful());
        }
        catch(Exception e){

        }
    }


    /**
     * Compares the watch settings retrieved from the watch (stored in mSdData)
     * to the required settings stored as member variables to this class.
     *
     * @return true if they are all the same, or false if there are discrepancies.
     */
    public boolean checkWatchSettings() {
        boolean settingsOk = true;
        if (mDataUpdatePeriod != mSdData.mDataUpdatePeriod) {
            Log.v(TAG, "checkWatchSettings - mDataUpdatePeriod Wrong");
            settingsOk = false;
        }
        if (mMutePeriod != mSdData.mMutePeriod) {
            Log.v(TAG, "checkWatchSettings - mMutePeriod Wrong");
            settingsOk = false;
        }
        if (mManAlarmPeriod != mSdData.mManAlarmPeriod) {
            Log.v(TAG, "checkWatchSettings - mManAlarmPeriod Wrong");
            settingsOk = false;
        }
        if (mSamplePeriod != mSdData.analysisPeriod) {
            Log.v(TAG, "checkWatchSettings - mSamplePeriod Wrong");
            settingsOk = false;
        }
        if (mAlarmFreqMin != mSdData.alarmFreqMin) {
            Log.v(TAG, "checkWatchSettings - mAlarmFreqMin Wrong");
            settingsOk = false;
        }
        if (mAlarmFreqMax != mSdData.alarmFreqMax) {
            Log.v(TAG, "checkWatchSettings - mAlarmFreqMax Wrong");
            settingsOk = false;
        }
        if (mWarnTime != mSdData.warnTime) {
            Log.v(TAG, "checkWatchSettings - mWarnTime Wrong");
            settingsOk = false;
        }
        if (mAlarmTime != mSdData.alarmTime) {
            Log.v(TAG, "checkWatchSettings - mAlarmTime Wrong");
            settingsOk = false;
        }
        if (mAlarmThresh != mSdData.alarmThresh) {
            Log.v(TAG, "checkWatchSettings - mAlarmThresh Wrong");
            settingsOk = false;
        }
        if (mAlarmRatioThresh != mSdData.alarmRatioThresh) {
            Log.v(TAG, "checkWatchSettings - mAlarmRatioThresh Wrong");
            settingsOk = false;
        }
        if (mFallActive != mSdData.mFallActive) {
            Log.v(TAG, "checkWatchSettings - mFallActive Wrong");
            settingsOk = false;
        }
        if (mFallThreshMin != mSdData.mFallThreshMin) {
            Log.v(TAG, "checkWatchSettings - mFallThreshMin Wrong");
            settingsOk = false;
        }
        if (mFallThreshMax != mSdData.mFallThreshMax) {
            Log.v(TAG, "checkWatchSettings - mFallThreshMax Wrong");
            settingsOk = false;
        }
        if (mFallWindow != mSdData.mFallWindow) {
            Log.v(TAG, "checkWatchSettings - mFallWindow Wrong");
            settingsOk = false;
        }

        return settingsOk;
    }

    /**
     * Request Watch App to send us its latest data.
     * Will be received as a message by the receiveData handler
     */
    public void getWatchData() {
        Log.v(TAG, "getData() - requesting data from watch");
        mUtil.writeToSysLogFile("SdDataSourceAw.getData() - requesting data from Android Wear");

        // FIXME - make this work with Android Wear
    }


    /**
     * Checks the status of the connection to the pebble watch,
     * and sets class variables for use by other functions.
     * If the watch app is not running, it attempts to re-start it.
     */
    public void getPebbleStatus() {
        Time tnow = new Time(Time.getCurrentTimezone());
        long tdiff;
        tnow.setToNow();
        // get time since the last data was received from the Pebble watch.
        tdiff = (tnow.toMillis(false) - mStatusTime.toMillis(false));
        Log.v(TAG, "getStatus() - mWatchAppRunningCheck=" + mWatchAppRunningCheck + " tdiff=" + tdiff);
        // Check we are actually connected to the pebble.
        if (mCapabilitityInfo != null) {
            mSdData.watchConnected = CheckIsWearClient(mCapabilitityInfo);
            if (!mSdData.watchConnected) mWatchAppRunningCheck = false;
            // And is the pebble_sd app running?
            // set mWatchAppRunningCheck has been false for more than 10 seconds
            // the app is not talking to us
            // mWatchAppRunningCheck is set to true in the receiveData handler.
            if (!mWatchAppRunningCheck &&
                    (tdiff > (mDataUpdatePeriod + mAppRestartTimeout) * 1000L)) {
                Log.v(TAG, "getStatus() - tdiff = " + tdiff);
                mSdData.watchAppRunning = false;
                //Log.v(TAG, "getStatus() - Pebble App Not Running - Attempting to Re-Start");
                //mUtil.writeToSysLogFile("SdDataSourceAw.getStatus() - Pebble App not Running - Attempting to Re-Start");
                //startWatchApp();
                //mStatusTime = tnow;  // set status time to now so we do not re-start app repeatedly.
                //getWatchSdSettings();
                // Only make audible warning beep if we have not received data for more than mFaultTimerPeriod seconds.
                if (tdiff > (mDataUpdatePeriod + mFaultTimerPeriod) * 1000L) {
                    Log.v(TAG, "getStatus() - Watch App Not Running - Attempting to Re-Start");
                    mUtil.writeToSysLogFile("SdDataSourceAw.getStatus() - Pebble App not Running - Attempting to Re-Start");
                    startWatchApp();
                    mStatusTime.setToNow();
                    mSdDataReceiver.onSdDataFault(mSdData);
                } else {
                    Log.v(TAG, "getStatus() - Waiting for mFaultTimerPeriod before issuing audible warning...");
                }
            } else {
                mSdData.watchAppRunning = true;
            }

            // if we have confirmation that the app is running, reset the
            // status time to now and initiate another check.
            if (mWatchAppRunningCheck) {
                mWatchAppRunningCheck = false;
                mStatusTime.setToNow();
            }

            if (!mSdData.haveSettings) {
                Log.v(TAG, "getStatus() - no settings received yet - requesting");
                getWatchSdSettings();
                getWatchData();
            }

            // Send raw, unprocessed data to the phone.
            int SD_MODE_RAW = 1;
            if (mPebbleSdMode == SD_MODE_RAW) {
                analyseRawData();
            }
        }
        else{

        }
    }

    /**
     * analyseRawData() - called when raw data is received.
     * FIXME - this does not do anything at the moment so raw data is
     * ignored!
     */
    private void analyseRawData() {
        Log.v(TAG, "analyserawData()");
        //DoubleFFT_1D fft = new DoubleFFT_1D(MAX_RAW_DATA);
        //fft.realForward(rawData);
        // FIXME - rawData should really be a circular buffer.
        nRawData = 0;
    }


    // Name of capability listed in Phone app's wear.xml.
    // IMPORTANT NOTE: This should be named differently than your Wear app's capability.
    static final Uri CAPABILITY_WEAR_APP = Uri.parse("wear://");

    // Links to install mobile app for both Android (Play Store) and iOS.
    // TODO: Replace with your links/packages.
    static final String ANDROID_MARKET_APP_URI =
            "market://details?id=com.example.android.wearable.wear.wearverifyremoteapp";

    // TODO: Replace with your links/packages.
    static final String APP_STORE_APP_URI =
            "https://itunes.apple.com/us/app/android-wear/id986496028?mt=8";


}







