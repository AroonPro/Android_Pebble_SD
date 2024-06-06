package uk.org.openseizuredetector;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;

import java.util.ArrayList;

/**
 * BLEScanActivity - Scans for Bluetooth LE devices.
 * Updated for en_GB standards and dynamic resource lookup.
 */
public class BLEScanActivity extends ListActivity {
    private static final String TAG = "BLEScanActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mScanning;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OsdUtil mUtil;
    private boolean mPermissionsRequested = false;

    private final int okColour = Color.BLUE;
    private final int warnColour = Color.MAGENTA;
    private final int alarmColour = Color.RED;
    private final int okTextColour = Color.WHITE;
    private final int warnTextColour = Color.WHITE;
    private final int alarmTextColour = Color.BLACK;

    // --- 1. DYNAMIC RESOURCE HELPERS ---
    private int resId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private View safeFind(String idName) {
        int id = resId(idName, "id");
        return (id != 0) ? findViewById(id) : null;
    }

    private String getStr(String name) {
        int id = resId(name, "string");
        return (id != 0) ? getString(id) : name;
    }

    // --- 2. LIFECYCLE ---
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");

        int layoutId = resId("ble_scan_activity", "layout");
        if (layoutId != 0) setContentView(layoutId);

        setTitle(getStr("title_devices"));
        mUtil = new OsdUtil(this, mHandler);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, getStr("ble_not_supported"), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, getStr("error_bluetooth_not_supported"), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mPermissionsRequested = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);

        TextView currentDevTv = (TextView) safeFind("current_ble_device_tv");
        if (currentDevTv != null) {
            String bleAddr = SP.getString("BLE_Device_Addr", "none");
            String bleName = SP.getString("BLE_Device_Name", "none");
            currentDevTv.setText("Current Device=" + bleName + " (" + bleAddr + ")");
            currentDevTv.setTextColor(okTextColour);
            currentDevTv.setBackgroundColor(okColour);
        }

        TextView presentTv = (TextView) safeFind("ble_present_tv");
        if (presentTv != null) {
            if (mBluetoothAdapter == null) {
                presentTv.setText("ERROR - Bluetooth Adapter Not Present");
                presentTv.setTextColor(alarmTextColour);
                presentTv.setBackgroundColor(alarmColour);
            } else {
                presentTv.setText("Bluetooth Adapter Present - OK");
                presentTv.setTextColor(okTextColour);
                presentTv.setBackgroundColor(okColour);
            }
        }

        TextView enabledTv = (TextView) safeFind("ble_adapter_tv");
        if (enabledTv != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                enabledTv.setText("ERROR - Bluetooth NOT Enabled");
                enabledTv.setTextColor(alarmTextColour);
                enabledTv.setBackgroundColor(alarmColour);
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            } else {
                enabledTv.setText("Bluetooth Adapter Enabled OK");
                enabledTv.setTextColor(okTextColour);
                enabledTv.setBackgroundColor(okColour);
            }
        }

        if (!mUtil.areBtPermissionsOk()) {
            requestBTPermissions(this);
        }

        TextView permTv = (TextView) safeFind("ble_perm1_tv");
        if (permTv != null) {
            if (mUtil.areBtPermissionsOk()) {
                permTv.setText("Bluetooth Permissions Granted OK");
                permTv.setBackgroundColor(okColour);
                permTv.setTextColor(okTextColour);
            } else {
                permTv.setText("ERROR: Bluetooth Permissions Missing!");
                permTv.setBackgroundColor(warnColour);
                permTv.setTextColor(warnTextColour);
            }
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    // --- 3. SCAN LOGIC ---
    private void scanLeDevice(final boolean enable) {
        TextView statusTv = (TextView) safeFind("ble_scan_status_tv");
        Button scanBtn = (Button) safeFind("startScanButton");

        if (enable) {
            mHandler.postDelayed(() -> {
                stopScan();
                invalidateOptionsMenu();
                if (statusTv != null) {
                    statusTv.setText("Stopped");
                    statusTv.setTextColor(okTextColour);
                    statusTv.setBackgroundColor(okColour);
                }
                if (scanBtn != null) scanBtn.setEnabled(true);
            }, SCAN_PERIOD);

            startScan();
            if (statusTv != null) {
                statusTv.setText("Scanning");
                statusTv.setTextColor(warnTextColour);
                statusTv.setBackgroundColor(warnColour);
            }
            if (scanBtn != null) scanBtn.setEnabled(false);
        } else {
            stopScan();
            if (statusTv != null) statusTv.setText("Stopped");
            if (scanBtn != null) scanBtn.setEnabled(true);
        }
        invalidateOptionsMenu();
    }

    private void startScan() {
        if (!mUtil.areBtPermissionsOk()) return;
        mScanning = true;
        try {
            if (mBluetoothLeScanner != null) mBluetoothLeScanner.startScan(mLeScanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
        }
    }

    private void stopScan() {
        mScanning = false;
        try {
            if (mBluetoothLeScanner != null) mBluetoothLeScanner.stopScan(mLeScanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException stopping: " + e.getMessage());
        }
    }

    // --- 4. CALLBACKS & ADAPTER ---
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        if (mScanning) stopScan();

        SharedPreferences.Editor SPE = PreferenceManager.getDefaultSharedPreferences(this).edit();
        try {
            SPE.putString("BLE_Device_Addr", device.getAddress());
            SPE.putString("BLE_Device_Name", device.getName());
            SPE.apply();
        } catch (SecurityException ex) {
            Log.e(TAG, "Permission error saving device name");
        }

        Intent i = new Intent(this, StartupActivity.class);
        startActivity(i);
        finish();
    }

    public void requestBTPermissions(Activity activity) {
        if (mPermissionsRequested) return;

        String[] btPermissions = mUtil.getRequiredBtPermissions();
        boolean showRationale = false;
        for (String p : btPermissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, p)) showRationale = true;
        }

        if (showRationale) {
            new AlertDialog.Builder(this)
                    .setTitle(getStr("permissions_required"))
                    .setMessage("Bluetooth scanning requires additional permissions.")
                    .setPositiveButton("OK", (dialog, id) -> {
                        ActivityCompat.requestPermissions(activity, btPermissions, 42);
                        mPermissionsRequested = true;
                    })
                    .setNegativeButton(getStr("closeBtnTxt"), (dialog, id) -> finish())
                    .show();
        } else {
            ActivityCompat.requestPermissions(activity, btPermissions, 42);
            mPermissionsRequested = true;
        }
    }

    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mLeDeviceListAdapter.addDevice(result.getDevice());
            mLeDeviceListAdapter.notifyDataSetChanged();
        }
    };

    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices = new ArrayList<>();
        private final LayoutInflater mInflator = BLEScanActivity.this.getLayoutInflater();

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) mLeDevices.add(device);
        }

        public BluetoothDevice getDevice(int i) { return mLeDevices.get(i); }
        public void clear() { mLeDevices.clear(); }
        @Override public int getCount() { return mLeDevices.size(); }
        @Override public Object getItem(int i) { return mLeDevices.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder;
            if (view == null) {
                int itemId = resId("ble_list_item_device", "layout");
                view = mInflator.inflate(itemId, null);
                holder = new ViewHolder();
                holder.deviceAddress = (TextView) view.findViewById(resId("device_address", "id"));
                holder.deviceName = (TextView) view.findViewById(resId("device_name", "id"));
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            try {
                String name = device.getName();
                holder.deviceName.setText(name != null ? name : getStr("unknown_device"));
                holder.deviceAddress.setText(device.getAddress());
            } catch (SecurityException e) {
                holder.deviceName.setText("Permission Error");
            }
            return view;
        }
    }

    static class ViewHolder { TextView deviceName; TextView deviceAddress; }
}