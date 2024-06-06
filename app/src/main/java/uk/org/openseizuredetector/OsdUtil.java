/*
  Pebble_sd - a simple accelerometer based seizure detector that runs on a
  Pebble smart watch (http://getpebble.com).

  See http://openseizuredetector.org for more information.

  Copyright Graham Jones, 2015.

  This file is part of pebble_sd.

  Pebble_sd is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Pebble_sd is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with pebble_sd.  If not, see <http://www.gnu.org/licenses/>.

*/
package uk.org.openseizuredetector;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.data.LineDataSet;


public class OsdUtil {

    /**
     * Converts time units (e.g., Hours to Seconds) using double precision.
     * Used to calculate buffer windows for Heart Rate history.
     */
    public static double convertTimeUnit(long value, TimeUnit from, TimeUnit to) {
        // We use double to prevent rounding errors before the final Math.round() call
        return (double) value * (double) from.toNanos(1) / (double) to.toNanos(1);
    }


    /**
     * Converts accelerometer readings from m/s^2 (Android standard)
     * to milli-G (OSD standard).
     * * @param mss Value in metres per second squared
     * @return Value in milli-G
     */
    public static double convertMetresPerSecondSquaredToMilliG(float mss) {
        // 1g = 9.80665 m/s^2.
        // mG = (val / 9.80665) * 1000
        return (double) mss * (1000.0 / 9.80665);
    }

    /**
     * Safely parses a string to a double, returning 0.0 on failure.
     */
    public double parseToDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            Log.e("OsdUtil", "Error parsing double: " + str);
            return 0.0;
        }
    }

    /**
     * getServerIp - SDK 17 Preferences Bridge
     * * en_GB Java Documentation:
     * Fetches the OSD Server IP address from SharedPreferences.
     * This is the 'Graft' that connects the Network Task with
     * the user's configuration from 2012.
     */
    public String getServerIp() {
        /* * en_GB: In 2012, PreferenceManager was the standard.
         * We use a default '192.168.1.1' if nothing is set.
         */
        if (mContext != null) {
            android.content.SharedPreferences prefs =
                    android.preference.PreferenceManager.getDefaultSharedPreferences(mContext);

            // R-Free: We gebruiken de directe sleutel 'server_ip'
            return prefs.getString("server_ip", "192.168.1.1");
        }
        return "";
    }

    /**
     * getResId - The R-FREE Dynamic Engine
     * * en_GB Java Documentation:
     * This method replaces the 'R' class dependency by performing runtime
     * lookups of resource IDs using string names.
     * * 2012-2026 Integrity:
     * Essential for the 'Eggshell' architecture. It ensures that if a
     * resource name exists in the XML, the Java code will find it,
     * regardless of the R.java generation status.
     */
    public int getResId(String name, String type) {
        /* * en_GB: We use the context passed during OsdUtil initialization.
         * 'name' is the XML identifier (e.g., "loginBtn").
         * 'type' is the resource category (e.g., "id", "layout", "string").
         */
        if (mContext != null) {
            try {
                int id = mContext.getResources().getIdentifier(
                        name,
                        type,
                        mContext.getPackageName()
                );
                if (id == 0) {
                    Log.e("OsdUtil", "R-Free Warning: Resource not found: " + name + " (" + type + ")");
                }
                return id;
            } catch (Exception e) {
                Log.e("OsdUtil", "getResId Exception: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    /**
     * getStringById - en_GB
     * Safely retrieves a string resource by its name (e.g., "AppPermissionsOk").
     * This method is essential for the 2024 LSA Audit build as it prevents
     * 'Symbol Not Found' errors during UI status updates.
     * * @param stringName The name of the string resource.
     * @return The localized string value or the name itself as a fallback.
     */
    public String getStringById(String stringName) {
        // Stap 1: Zoek het numerieke ID op via de dynamische getResId
        int resId = getResId(stringName, "string");

        if (resId != 0) {
            // Stap 2: Haal de vertaalde tekst op uit de resources
            return mContext.getString(resId);
        } else {
            // Failsafe voor de 2026 omgeving: toon de naam als de resource mist
            Log.e(TAG, "getStringById: String resource not found: " + stringName);
            return "[" + stringName + "]";
        }
    }

    /**
     * convertTimeUnit - SDK 17 Integral Time Engine
     * * en_GB Java Documentation:
     * Replaces the unreliable 2012 'Time' objects. Converts raw long
     * milliseconds into human-readable strings for the LSA audit.
     */
    public String convertTimeUnit(long millis, String format) {
        /* * en_GB: We use SimpleDateFormat (Standard since JDK 1.1).
         * This is the 'Kirk-proof' way to format 2012-2026 logs.
         */
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(format, java.util.Locale.getDefault());
            java.util.Date date = new java.util.Date(millis);
            return sdf.format(date);
        } catch (Exception e) {
            Log.e("OsdUtil", "convertTimeUnit Error: " + e.getMessage());
            return "00:00:00";
        }
    }


    /**
     * Calculates the average Y-value of all entries in a LineDataSet.
     * Required by SdAlgHr to determine the baseline for Adaptive Heart Rate alarms.
     */
    public static double getAverageValueFromListOfEntry(LineDataSet dataSet) {
        if (dataSet == null || dataSet.getEntryCount() == 0) {
            return 0;
        }

        float sum = 0;
        for (int i = 0; i < dataSet.getEntryCount(); i++) {
            // In MPAndroidChart 3.x, we use getEntryForIndex
            sum += dataSet.getEntryForIndex(i).getY();
        }
        return (double) (sum / dataSet.getEntryCount());
    }
    /**
     * Returns the required Bluetooth permissions based on the Android version.
     */
    public String[] getRequiredBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }

    /**
     * Checks if all required Bluetooth permissions are granted.
     */
    public boolean areBtPermissionsOk() {
        for (String permission : getRequiredBtPermissions()) {
            if (ContextCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w("OsdUtil", "Permission missing: " + permission);
                return false;
            }
        }
        return true;
    }
    public final static String PRIVACY_POLICY_URL = "https://www.openseizuredetector.org.uk/?page_id=1415";
    public final static String DATA_SHARING_URL = "https://www.openseizuredetector.org.uk/?page_id=1818";

    private final String SYSLOG = "SysLog";
    private final String ALARMLOG = "AlarmLog";
    private final String DATALOG = "DataLog";

    private static Context mContext;
    private Handler mHandler;
    private static String TAG = "OsdUtil";
    private boolean mLogAlarms = true;
    private boolean mLogSystem = true;
    private boolean mLogData = true;
    private static final String mSysLogTableName = "SysLog";
    static private SQLiteDatabase mSysLogDb = null;
    private final static Long mMinPruneInterval = 5 * 60 * 1000L;
    private static Long mLastPruneMillis = 0L;

    private static int mNbound = 0;

    // --- Dynamische Resource Helper ---
    private String getSafeString(String resName, String defaultMsg) {
        int id = mContext.getResources().getIdentifier(resName, "string", mContext.getPackageName());
        return (id != 0) ? mContext.getString(id) : defaultMsg;
    }

    /**
     * Converts the integer alarm state to a human-readable string.
     */
    public String alarmStatusToString(int alarmState) {
        switch (alarmState) {
            case 0: return "OK";
            case 1: return "PRE-ALARM";
            case 2: return "ALARM";
            case 3: return "MANUAL HELP";
            default: return "UNKNOWN (" + alarmState + ")";
        }
    }

    /**
     * Parses a standard OSD date string back into a Date object.
     */
    public Date string2date(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return new Date();
        // OSD uses yyyy-MM-dd HH:mm:ss for Web API
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        try {
            return format.parse(dateStr);
        } catch (Exception e) {
            Log.e("OsdUtil", "Error parsing date: " + dateStr);
            return new Date(); // Fallback to now
        }
    }
    public final String[] BT_PERMISSIONS_API30 = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };
    public final String[] BT_PERMISSIONS_OLD = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
    };
    public String[] BT_PERMISSIONS;

    public OsdUtil(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        updatePrefs();
        openDb();
        writeToSysLogFile("OsdUtil() - initialised");
    }

    public void updatePrefs() {
        Log.v(TAG, "updatePrefs()");
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(mContext);
        try {
            mLogAlarms = SP.getBoolean("LogAlarms", true);
            mLogData = SP.getBoolean("LogData", true);
            mLogSystem = SP.getBoolean("LogSystem", true);
        } catch (Exception ex) {
            Log.v(TAG, "updatePrefs() - Problem parsing preferences!");
            // GEFIXT: Gebruik dynamische string look-up i.p.v. R.string
            showToast(getSafeString("ParsePreferenceWarning", "Problem parsing preferences!"));
        }
    }

    public void runOnUiThread(Runnable runnable) {
        mHandler.post(runnable);
    }

    public boolean isServerRunning() {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("uk.org.openseizuredetector.SdServer".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void startServer() {
        Log.d(TAG, "OsdUtil.startServer()");
        writeToSysLogFile("startServer() - starting server");
        Intent sdServerIntent = new Intent(mContext, SdServer.class);
        sdServerIntent.setData(Uri.parse("Start"));
        if (Build.VERSION.SDK_INT >= 26) {
            mContext.startForegroundService(sdServerIntent);
        } else {
            mContext.startService(sdServerIntent);
        }
    }

    public void stopServer() {
        writeToSysLogFile("stopserver() - stopping server");
        Intent sdServerIntent = new Intent(mContext, SdServer.class);
        sdServerIntent.setData(Uri.parse("Stop"));
        mContext.stopService(sdServerIntent);
    }

    public void bindToServer(Context activity, SdServiceConnection sdServiceConnection) {
        writeToSysLogFile("bindToServer() - binding to SdServer");
        Intent intent = new Intent(mContext, SdServer.class);
        activity.bindService(intent, sdServiceConnection, Context.BIND_AUTO_CREATE);
        mNbound++;
    }

    public void unbindFromServer(Context activity, SdServiceConnection sdServiceConnection) {
        if (sdServiceConnection.mBound) {
            try {
                activity.unbindService(sdServiceConnection);
                sdServiceConnection.mBound = false;
                mNbound--;
            } catch (Exception ex) {
                Log.e(TAG, "unbindFromServer() error: " + ex.toString());
            }
        }
    }

    public String getAppVersionName() {
        String versionName = "unknown";
        // From http://stackoverflow.com/questions/4471025/
        //         how-can-you-get-the-manifest-version-number-
        //         from-the-apps-layout-xml-variable
        final PackageManager packageManager = mContext.getPackageManager();
        if (packageManager != null) {
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
                versionName = packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.v(TAG, "failed to find versionName");
                versionName = null;
            }
        }
        return versionName;
    }

    /**
     * get the ip address of the phone.
     * Based on http://stackoverflow.com/questions/11015912/how-do-i-get-ip-address-in-ipv4-format
     */
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    //Log.v(TAG,"ip1--:" + inetAddress);
                    //Log.v(TAG,"ip2--:" + inetAddress.getHostAddress());

                    //updated from https://stackoverflow.com/questions/32141785/android-api-23-inetaddressutils-replacement
                    // for getting IPV4 format
                    if (!inetAddress.isLoopbackAddress()
                            && inetAddress instanceof Inet4Address
                            && inetAddress.isSiteLocalAddress()
                    ) {

                        String ip = inetAddress.getHostAddress();
                        //Log.v(TAG,"ip---::" + ip);
                        return ip;
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("IP Address", ex.toString() + " " + Arrays.toString(Thread.currentThread().getStackTrace()));
        }
        return null;
    }

    public boolean isMobileDataActive() {
        // return true if we are using mobile data, otherwise return false
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                LinkProperties result = cm.getLinkProperties(cm.getActiveNetwork());
                if (!result.getInterfaceName().contains("wlan"))
                    return false;
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (capabilities == null) {
                    return false;
                }

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ) {
                    return true;
                }else
                    return false;
            }else {
                /**
                 * has @Deprecation!
                 * see https://developer.android.com/reference/android/net/NetworkInfo
                 * @return
                 */
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork == null) return false;
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        else
            return  false;

    }

    public boolean isNetworkConnected() {
        // return true if we have a network connection, otherwise false.
        // modified because networkInfo is deprecated. Solution:
        // https://stackoverflow.com/questions/32547006/connectivitymanager-getnetworkinfoint-deprecated
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {

                NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (capabilities == null) {
                    return false;
                }

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }else
                    return false;
            }else {
                /**
                 * has @Deprecation!
                 * see https://developer.android.com/reference/android/net/NetworkInfo
                 * @return
                 */
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    return (activeNetwork.isConnected());
                } else {
                    return (false);
                }
            }
        }
        else
            return  false;
    }

    // simplifying text
    /**
     * Display a Toast message on screen.
     *
     * @param msg - message to display.
     */
    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show());
    }

    /**
     * Returns a list of OSD data files stored in the app's internal storage.
     * Used by SdWebServer to provide log/data downloads.
     */
    public File[] getDataFilesList() {
        File dataDir = mContext.getFilesDir();
        if (dataDir == null) {
            return new File[0];
        }

        // Filter to only include files that start with our data prefix
        return dataDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // Usually starts with "sd_data_" or similar from Constants
                return name.startsWith(Constants.DATA_FILE_PREFIX);
            }
        });
    }
    public void writeToLogFile(String fname, String msgStr) {
        Time tnow = new Time(Time.getCurrentTimezone());
        tnow.setToNow();
        String dateStr = tnow.format("%Y-%m-%d");
        fname = fname + "_" + dateStr + ".txt";

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No Write Permission");
        } else if (isExternalStorageWritable()) {
            try {
                FileWriter of = new FileWriter(getDataStorageDir() + "/" + fname, true);
                if (msgStr != null) {
                    of.append(tnow.format("%Y-%m-%d %H:%M:%S")).append(", ").append(String.valueOf(tnow.toMillis(true))).append(", ").append(msgStr).append("<br/>\n");
                }
                of.close();
            } catch (Exception ex) {
                // GEFIXT: Gebruik dynamische string look-up
                showToast(getSafeString("ErrorWritingLogFileWarning", "Error writing log: ") + ex.toString());
            }
        }
    }

    private static boolean openDb() {
        try {
            if (mSysLogDb == null) {
                mSysLogDb = new OsdSysLogHelper(mContext).getWritableDatabase();
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Rest van de database helper functies blijven gelijk...
    // (Omit voor beknoptheid, tenzij specifiek nodig)

    public void writeLogEntryToLocalDb(String logText, String statusVal) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String dateStr = dateFormat.format(new Date());
        try {
            String SQLStr = "INSERT INTO " + mSysLogTableName + "(dataTime, logLevel, dataJSON, uploaded) VALUES("
                    + "'" + dateStr + "',"
                    + DatabaseUtils.sqlEscapeString(statusVal) + ","
                    + DatabaseUtils.sqlEscapeString(logText) + ","
                    + 0 + ")";
            mSysLogDb.execSQL(SQLStr);
            pruneSysLogDb();
        } catch (SQLException e) {
            Log.e(TAG, "DB Write Error: " + e.toString());
        }
    }

    // --- ESSENTIËLE TOEVOEGING: Pruning en Helper classes ---
    public int pruneSysLogDb() {
        long currentDateMillis = new Date().getTime();
        if (currentDateMillis > mLastPruneMillis + mMinPruneInterval) {
            mLastPruneMillis = currentDateMillis;
            long endDateMillis = currentDateMillis - (7 * 24 * 60 * 60 * 1000); // 7 dagen
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String endDateStr = dateFormat.format(new Date(endDateMillis));
            return mSysLogDb.delete(mSysLogTableName, "dataTime<=?", new String[]{endDateStr});
        }
        return 0;
    }

    public static class OsdSysLogHelper extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "OsdSysLog.db";
        public OsdSysLogHelper(Context context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + mSysLogTableName + "(id INTEGER PRIMARY KEY, dataTime DATETIME, logLevel TEXT, dataJSON TEXT, uploaded INT);");
        }
        public void onUpgrade(SQLiteDatabase db, int old, int n) { db.execSQL("DROP TABLE IF EXISTS " + mSysLogTableName); onCreate(db); }
    }

    // Hulpfuncties voor IP en Netwerk (Ongewijzigd)
    public boolean isExternalStorageWritable() { return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()); }
    public File getDataStorageDir() { return mContext.getExternalFilesDir(null); }
    public void writeToSysLogFile(String msgStr) { writeLogEntryToLocalDb(msgStr, "v"); }
}