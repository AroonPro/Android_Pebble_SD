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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;

import java.util.List;
import java.util.UUID;


/**
 * A data source that registers for BLE GATT notifications from a device and
 * waits to be notified of data being available.
 */
public class SdDataSourceBLE extends SdDataSource {
    private int MAX_RAW_DATA = 125;  // 5 seconds at 25 Hz.
    private String TAG = "SdDataSourceBLE";
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private int nRawData = 0;
    private double[] rawData = new double[MAX_RAW_DATA];
    private boolean waitForDescriptorWrite = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";


    public static String SERV_DEV_INFO = "0000180a-0000-1000-8000-00805f9b34fb";
    public static String SERV_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String SERV_OSD = "000085e9-0000-1000-8000-00805f9b34fb";
    public static String CHAR_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CHAR_MANUF_NAME = "00002a29-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_ACC_DATA = "000085ea-0000-1000-8000-00805f9b34fb";
    public static String CHAR_OSD_BATT_DATA = "000085eb-0000-1000-8000-00805f9b34fb";

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(CHAR_HEART_RATE_MEASUREMENT);
    private BluetoothGattCharacteristic mBattChar;
    private BluetoothGattCharacteristic mOsdChar;


    public SdDataSourceBLE(Context context, Handler handler,
                           SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "BLE";
        // Set default settings from XML files (mContext is set by super().
        PreferenceManager.setDefaultValues(mContext,
                R.xml.network_passive_datasource_prefs, true);
    }


    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        super.start();
        mUtil.writeToSysLogFile("SdDataSourceBLE.start() - mBleDeviceAddr=" + mBleDeviceAddr);

        if (mBleDeviceAddr == "" || mBleDeviceAddr == null) {
            final Intent intent = new Intent(this.mContext, BLEScanActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
        Log.i(TAG, "mBLEDevice is " + mBleDeviceName + ", Addr=" + mBleDeviceAddr);

        bleConnect();

    }

    private void bleConnect() {
        mSdData.watchConnected = false;
        mSdData.watchAppRunning = false;
        mBluetoothGatt = null;
        mConnectionState = STATE_DISCONNECTED;
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "bleConnect(): Unable to initialize BluetoothManager.");
                return;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "bleConnect(): Unable to obtain a BluetoothAdapter.");
            return;
        }

        if (mBluetoothAdapter == null || mBleDeviceAddr == null) {
            Log.w(TAG, "bleConnect(): BluetoothAdapter not initialized or unspecified address.");
            return;
        }

        BluetoothDevice device;
        try {
            device = mBluetoothAdapter.getRemoteDevice(mBleDeviceAddr);
        } catch (Exception e) {
            Log.w(TAG, "bleConnect(): Error connecting to device address "+mBleDeviceAddr+".");
            device = null;
        }
        if (device == null) {
            Log.w(TAG, "bleConnect(): Device not found.  Unable to connect.");
            return;
        } else {
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            mBluetoothGatt = device.connectGatt(mContext, true, mGattCallback);
            Log.d(TAG, "bleConnect(): Trying to create a new connection.");
            String mBluetoothDeviceAddress = mBleDeviceAddr;
            mConnectionState = STATE_CONNECTING;
        }
    }

    private void bleDisconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        // Un-register for BLE Notifications.
        if (mOsdChar != null) {
            setCharacteristicNotification(mOsdChar, false);
        }

        mBluetoothGatt.disconnect();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        mSdData.watchAppRunning = false;
        mSdData.watchConnected = false;
        mConnectionState = STATE_DISCONNECTED;

    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SDDataSourceBLE.stop()");

        bleDisconnect();
        super.stop();
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic, final boolean enabled) {
        Log.w(TAG, "setCharacteristicNotification " + characteristic.getUuid());

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        if (waitForDescriptorWrite) {
            // Apparently if you try to write multiple descriptors too quickly then only
            // one is processed, hence why this waiting logic is necessary
            Log.w(TAG, "waitForDescriptor " + characteristic.getUuid());
            mHandler.postDelayed(() -> {
                Log.w(TAG, "delayed");
                setCharacteristicNotification(characteristic, enabled);
            }, 500);
            return;
        }

        if (enabled) {
            Log.v(TAG, "setCharacteristicNotification - Requesting notifications");
            mBluetoothGatt.setCharacteristicNotification(characteristic, true);

            // Tell the device we want notifications?   The sample from Google said we only need this for Heart Rate, but the
            // BangleJS widget did not work without it so do it for everything.
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        } else {
            Log.v(TAG, "setCharacteristicNotification - De-registering notifications");
            mBluetoothGatt.setCharacteristicNotification(characteristic, false);

            // Tell the device we want notifications?   The sample from Google said we only need this for Heart Rate, but the
            // BangleJS widget did not work without it so do it for everything.
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }

        waitForDescriptorWrite = true;
    }    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                mSdData.watchConnected = true;
                Log.i(TAG, "onConnectionStateChange(): Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "onConnectionStateChange(): Attempting to start service discovery:");
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                mSdData.watchConnected = false;
                Log.i(TAG, "onConnectionStateChange(): Disconnected from GATT server - reconnecting after delay...");
                //bleDisconnect();  // Tidy up connections
                // Wait 2 seconds to give the server chance to shutdown, then re-start it
                mHandler.postDelayed(() -> bleConnect(), 2000);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            boolean foundOsdService = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "Services discovered");
                List<BluetoothGattService> serviceList = mBluetoothGatt.getServices();
                for (int i = 0; i < serviceList.size(); i++) {
                    String uuidStr = serviceList.get(i).getUuid().toString();
                    Log.v(TAG, "Service " + uuidStr);
                    List<BluetoothGattCharacteristic> gattCharacteristics =
                            serviceList.get(i).getCharacteristics();
                    if (uuidStr.equals(SERV_DEV_INFO)) {
                        Log.v(TAG, "Device Info Service Discovered");
                    } else if (uuidStr.equals(SERV_HEART_RATE)) {
                        Log.v(TAG, "Heart Rate Service Discovered");
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                            String charUuidStr = gattCharacteristic.getUuid().toString();
                            if (charUuidStr.equals(CHAR_HEART_RATE_MEASUREMENT)) {
                                Log.v(TAG, "Subscribing to Heart Rate Measurement Change Notifications");
                                setCharacteristicNotification(gattCharacteristic, true);
                            }
                        }
                    } else if (uuidStr.equals(SERV_OSD)) {
                        Log.v(TAG, "OpenSeizureDetector Service Discovered");
                        foundOsdService = true;
                        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                            String charUuidStr = gattCharacteristic.getUuid().toString();
                            if (charUuidStr.equals(CHAR_OSD_ACC_DATA)) {
                                Log.v(TAG, "Subscribing to Acceleration Data Change Notifications");
                                mOsdChar = gattCharacteristic;
                                setCharacteristicNotification(gattCharacteristic,true);
                            }
                            else if (charUuidStr.equals(CHAR_OSD_BATT_DATA)) {
                                Log.v(TAG,"Saving battery characteristic for later");
                                Log.v(TAG, "Subscribing to battery change Notifications");
                                setCharacteristicNotification(gattCharacteristic,true);
                            }
                        }
                    }
                }
                if (foundOsdService) {
                } else {
                    Log.v(TAG, "device is not offering the OSD Gatt Service - re-trying connection");
                    bleDisconnect();
                    // Wait 1 second to give the server chance to shutdown, then re-start it
                    mHandler.postDelayed(() -> bleConnect(), 1000);
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        public void onDataReceived(BluetoothGattCharacteristic characteristic) {
            Log.v(TAG, "onDataReceived uuid" + characteristic.getUuid().toString());

            // FIXME - collect data until we have enough to do analysis, then use onDataReceived to process it.
            //Log.v(TAG,"onDataReceived: Characteristic="+characteristic.getUuid().toString());
            if (characteristic.getUuid().toString().equals(CHAR_HEART_RATE_MEASUREMENT)) {
                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                    Log.d(TAG, "Heart rate format UINT16.");
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                    Log.d(TAG, "Heart rate format UINT8.");
                }
                final int heartRate = characteristic.getIntValue(format, 1);
                mSdData.mHR = (short) heartRate;
                Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            }
            else if (characteristic.getUuid().toString().equals(CHAR_OSD_ACC_DATA)) {
                //Log.v(TAG,"Received OSD ACC DATA"+characteristic.getValue());
                byte[] rawDataBytes = characteristic.getValue();
                Log.v(TAG, "CHAR_OSD_ACC_DATA: numSamples = " + rawDataBytes.length+" nRawData="+nRawData);
                for (int i = 0; i < rawDataBytes.length;i++) {
                    if (nRawData < MAX_RAW_DATA) {
                        rawData[nRawData] = 1000 * rawDataBytes[i] / 64;   // Scale to mg
                        nRawData++;
                    } else {
                        Log.i(TAG, "RawData Buffer Full - processing data");
                        // Re-start collecting raw data.
                        mSdData.watchAppRunning = true;
                        for (i = 0; i < rawData.length; i++) {
                            mSdData.rawData[i] = rawData[i];
                            //Log.v(TAG,"onDataReceived() i="+i+", "+rawData[i]);
                        }
                        mSdData.mNsamp = rawData.length;
                        //mNSamp = accelVals.length();
                        mWatchAppRunningCheck = true;
                        mDataStatusTime = new Time(Time.getCurrentTimezone());
                        doAnalysis();
                        nRawData = 0;
                    }
                }
            }
            else if (characteristic.getUuid().toString().equals(CHAR_OSD_BATT_DATA)) {
                byte batteryPc = characteristic.getValue()[0];
                mSdData.batteryPc = batteryPc;
                Log.v(TAG,"Received Battery Data" + String.format("%d", batteryPc));
                mSdData.haveSettings = true;
            }
            else {
                Log.v(TAG,"Unrecognised Characteristic Updated "+
                        characteristic.getUuid().toString());
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.v(TAG,"onCharacteristicRead");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onDataReceived(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.v(TAG,"onCharacteristicChanged(): Characteristic "+characteristic.getUuid()+" changed");
            onDataReceived(characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.v(TAG,"onDescriptorWrite(): Characteristic " + descriptor.getUuid() + " changed");
            waitForDescriptorWrite = false;
        }
    };



    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


}
