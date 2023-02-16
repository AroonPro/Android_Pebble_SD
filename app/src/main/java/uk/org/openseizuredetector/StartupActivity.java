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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.rohitss.uceh.UCEHandler;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * StartupActivity is shown on app start-up.  It starts the SdServer background service and waits
 * for it to start and to receive data and settings from the seizure detector before exiting and
 * starting the main activity.
 */
public class StartupActivity extends AppCompatActivity {
    private static String TAG = "StartupActivity";
    private int warnColour = Color.MAGENTA;
    private int warnTextColour = Color.BLACK;

    public final String[] REQUIRED_PERMISSIONS = {
            //Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
    };
    public final String[] SMS_PERMISSIONS_1 = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
    };
    public final String[] LOCATION_PERMISSIONS_1 = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            //Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
    };
    public final String[] LOCATION_PERMISSIONS_2 = {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    };
    private OsdUtil mUtil;
    private Timer mUiTimer;
    private SdServiceConnection mConnection;
    private boolean mStartedMainActivity = false;
    private boolean mDialogDisplayed = false;
    private Handler mHandler = new Handler();   // used to update ui from mUiTimer
    private boolean mUsingPebbleDataSource = true;
    private String mPebbleAppPackageName = null;
    private boolean mBatteryOptDialogDisplayed = false;
    private AlertDialog mBatteryOptDialog;
    private boolean mLocationPermissions1Requested;
    private boolean mLocationPermissions2Requested;
    private boolean mSmsPermissionsRequested;
    private boolean mPermissionsRequested;
    private Intent mStartUpActivityIntent;

    /*
     * serverStatusRunnable - called by updateServerStatus - updates the
     * user interface to reflect the current status received from the server.
     * If everything is ok, we close this activity and open the main user interface
     * activity.
     */
    final Runnable serverStatusRunnable = new Runnable() {
        public void run() {
            boolean allOk = true;
            TextView tv;
            ProgressBar pb;
            boolean smsAlarmsActive = true;
            boolean phoneAlarmsActive = true;

            Log.v(TAG, "serverStatusRunnable()");
            SharedPreferences SP = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());
            smsAlarmsActive = SP.getBoolean("SMSAlarm", false);
            phoneAlarmsActive = SP.getBoolean("PhoneCallAlarm", false);

            // Check power management settings
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Log.i(TAG, "Power Management OK - we are ignoring Battery Optimizations");
                if (mBatteryOptDialogDisplayed) {
                    mBatteryOptDialog.cancel();
                    mBatteryOptDialogDisplayed = false;
                }
            }

            // Settings ok
            tv = (TextView) findViewById(R.id.textItem1);
            pb = (ProgressBar) findViewById(R.id.progressBar1);
            int alarmTextColour = Color.BLACK;
            int okTextColour = Color.WHITE;
            int alarmColour = Color.RED;
            int okColour = Color.BLUE;
            if (arePermissionsOK()) {
                if (smsAlarmsActive && !areSMSPermissions1OK()) {
                    Log.i(TAG, "SMS permissions NOT OK");
                    tv.setText(getString(R.string.SmsPermissionWarning));
                    tv.setBackgroundColor(alarmColour);
                    tv.setTextColor(alarmTextColour);
                    //pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                    //pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
                    requestSMSPermissions();
                    allOk = false;
                } else if (smsAlarmsActive && !areLocationPermissions1OK()) {
                    Log.i(TAG,"Location permissions NOT OK");
                    tv.setText(getString(R.string.SmsPermissionWarning));
                    tv.setBackgroundColor(alarmColour);
                    tv.setTextColor(alarmTextColour);
                    requestLocationPermissions1();
                    allOk = false;
                } else if (smsAlarmsActive && !areLocationPermissions2OK()) {
                    Log.i(TAG,"SMS permissions2 NOT OK");
                    tv.setText(getString(R.string.SmsPermissionWarning));
                    tv.setBackgroundColor(alarmColour);
                    tv.setTextColor(alarmTextColour);
                    requestLocationPermissions2();
                    allOk = false;
                } else {
                    tv.setText(getString(R.string.AppPermissionsOk));
                    tv.setBackgroundColor(okColour);
                    tv.setTextColor(okTextColour);
                    pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                    pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
                }
            } else {
                tv.setText(getString(R.string.AppPermissionsWarning));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminate(true);
                allOk = false;
                requestPermissions(StartupActivity.this);
            }

            // If phone alarms are selected, we need to have the uk.org.openseizuredetector.dialler package installed to do the actual dialling.
            if (phoneAlarmsActive && !mUtil.isPackageInstalled("uk.org.openseizuredetector.dialler")) {
                tv.setText(getText(R.string.DiallerNotInstalledWarning));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
                allOk = false;
            }

            // Are we Bound to the Service
            tv = (TextView) findViewById(R.id.textItem2);
            pb = (ProgressBar) findViewById(R.id.progressBar2);
            if (mConnection.mBound) {
                tv.setText(getString(R.string.BoundToServiceOk));
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
                pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
            } else {
                tv.setText(getString(R.string.BindingToService));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminate(true);
                allOk = false;
            }

            // Is Watch Connected?
            tv = (TextView) findViewById(R.id.textItem3);
            pb = (ProgressBar) findViewById(R.id.progressBar3);
            if (mConnection.watchConnected()) {
                tv.setText(getString(R.string.WatchConnectedOk));
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
                pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
            } else {
                tv.setText(getString(R.string.WatchNotConnected));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminate(true);
                allOk = false;
            }


            // Do we have seizure detector data?
            tv = (TextView) findViewById(R.id.textItem5);
            pb = (ProgressBar) findViewById(R.id.progressBar5);
            if (mConnection.hasSdData()) {
                tv.setText(getString(R.string.SeizureDetectorDataReceived));
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
                pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
            } else {
                tv.setText(getString(R.string.WaitingForSeizureDetectorData));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminate(true);
                allOk = false;
            }


            // Do we have seizure detector settings yet?
            tv = (TextView) findViewById(R.id.textItem6);
            pb = (ProgressBar) findViewById(R.id.progressBar6);
            if (mConnection.hasSdSettings()) {
                tv.setText(getString(R.string.SeizureDetectorSettingsReceived));
                tv.setBackgroundColor(okColour);
                tv.setTextColor(okTextColour);
                pb.setIndeterminateDrawable(getResources().getDrawable(R.drawable.start_server));
                pb.setProgressDrawable(getResources().getDrawable(R.drawable.start_server));
            } else {
                tv.setText(getString(R.string.WaitingForSeizureDetectorSettings));
                tv.setBackgroundColor(alarmColour);
                tv.setTextColor(alarmTextColour);
                pb.setIndeterminate(true);
                allOk = false;
            }


            // If all the parameters are ok, close this activity and open the main
            // user interface activity instead.
            if (allOk) {
                if (!mDialogDisplayed && !mBatteryOptDialogDisplayed) {
                    if (!mStartedMainActivity) {
                        Log.i(TAG, "serverStatusRunnable() - starting main activity...");
                        mUtil.writeToSysLogFile("StartupActivity.serverStatusRunnable - all checks ok - starting main activity.");
                        try {
                            Intent intent = new Intent(
                                    getApplicationContext(),
                                    MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                            mStartedMainActivity = true;
                            finish();
                        } catch (Exception ex) {
                            mStartedMainActivity = false;
                            Log.e(TAG, "exception starting main activity " + ex.toString(),ex);
                            mUtil.writeToSysLogFile("StartupActivity.serverStatusRunnable - exception starting main activity " + ex.toString());
                        }
                    } else {
                        Log.v(TAG, "allOk, but already started MainActivity so not doing anything");
                        mUtil.writeToSysLogFile("StartupActivity.serverStatusRunnable - allOk, but already started MainActivity so not doing anything");
                    }
                } else {
                    Log.v(TAG, "allok, but dialog displayted so not starting MainActivity");
                }
            }
        }
    };
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
// remove from here
        PackageManager pm=getPackageManager();
        Intent main=new Intent(Intent.ACTION_MAIN, null);

        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launchables=pm.queryIntentActivities(main, 0);

        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setPackage(Constants.GLOBAL_CONSTANTS.mAppPackageNameWearReceiver);

        ResolveInfo resolveInfo = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = Constants.GLOBAL_CONSTANTS.mAppPackageNameWearReceiver;
        activityInfo.name = "WearReceiver";
        resolveInfo.activityInfo = activityInfo;

        Intent sdWearIntent = new Intent(Intent.ACTION_MAIN);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(sdWearIntent,0 );
        Intent testIntent = packageManager.getLaunchIntentForPackage(Constants.GLOBAL_CONSTANTS.mAppPackageNameWearReceiver);
        //until here
        Log.i(TAG, "onCreate()");
        setContentView(R.layout.startup_activity);
        mContext = this;
        mHandler = new Handler();
        mUtil = new OsdUtil(getApplicationContext(), mHandler);
        if (!Objects.equals(getIntent(),null))if (!Objects.equals(getIntent().getData(),null))
            if (Objects.equals(getIntent().getData() , Uri.parse("PASS")))
        {
            Intent thisReceivedIntent = getIntent();
            Intent originalIntent = null;
            if (!Objects.equals(thisReceivedIntent.getParcelableExtra("originalIntent"),null)) {
                originalIntent = thisReceivedIntent.getParcelableExtra("originalIntent");
                Log.i(TAG,"originalIntent: " + originalIntent);
                Log.i(TAG,"testIntent: " + testIntent);
                mContext.startActivity(originalIntent);
                finish();
                return;
            }
            Log.d(TAG+"_onCreate",getIntent().getData().toString());

            moveTaskToBack(true);
            setIntent(getIntent().setData(Uri.parse("")));
            if (mUtil.isServerRunning()) {
                if (Objects.equals(mConnection,null)) mConnection = new SdServiceConnection(getApplicationContext());
                if (!mConnection.mBound) mUtil.bindToServer(getApplicationContext(), mConnection);

                moveTaskToBack(true);

                if (!Objects.equals(mConnection,null))
                    if (!Objects.equals(mConnection.mSdServer,null))
                        if (!Objects.equals(mConnection.mSdServer.mSdDataSource,null))
                            mConnection.mSdServer.mSdDataSource.updateFromJSON(getIntent().getStringExtra("mSdData"));
            }
            finish();
            return;

        }

        // Set our custom uncaught exception handler to report issues.
        //Thread.setDefaultUncaughtExceptionHandler(new OsdUncaughtExceptionHandler(StartupActivity.this));
        new UCEHandler.Builder(mContext)
                .addCommaSeparatedEmailAddresses("crashreports@openseizuredetector.org.uk,")
                .build();

        // Read the default settings from the xml preferences files, so we do
        // not have to use the hard coded ones in the java files.
        PreferenceManager.setDefaultValues(mContext, R.xml.alarm_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.general_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.network_datasource_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.pebble_datasource_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.seizure_detector_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.network_passive_datasource_prefs, true);
        PreferenceManager.setDefaultValues(mContext, R.xml.logging_prefs, true);

        mUtil.writeToSysLogFile("");
        mUtil.writeToSysLogFile("*******************************");
        mUtil.writeToSysLogFile("* StartUpActivity Started     *");
        mUtil.writeToSysLogFile("*******************************");

        // Force the screen to stay on when the app is running
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        Button b;

        b = (Button) findViewById(R.id.settingsButton);
        b.setOnClickListener(view -> {
            Log.v(TAG, "settings button clicked");
            try {
                mUtil.writeToSysLogFile("Starting Settings Activity");
                Intent intent = new Intent(
                        StartupActivity.this,
                        PrefActivity.class);
                startActivity(intent);
            } catch (Exception ex) {
                Log.v(TAG, "exception starting settings activity " + ex.toString(),ex);
                mUtil.writeToSysLogFile("ERROR Starting Settings Activity");
            }

        });


        b = (Button) findViewById(R.id.installOsdAppButton);
        b.setOnClickListener(view -> {
            Log.v(TAG, "install Osd Watch App button clicked");
            mUtil.writeToSysLogFile("Installing Watch App");
            mConnection.mSdServer.mSdDataSource.installWatchApp();
        });

        mConnection = new SdServiceConnection(mContext);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop() - unbinding from server");
        mUtil.writeToSysLogFile("StartupActivity.onStop() - unbinding from server");
        mUtil.unbindFromServer(getApplicationContext(), mConnection);
        if (mUiTimer != null)
            mUiTimer.cancel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart()");
        mUtil.writeToSysLogFile("StartupActivity.onStart()");
        TextView tv;

        String versionName = mUtil.getAppVersionName();
        tv = (TextView) findViewById(R.id.appNameTv);
        tv.setText("OpenSeizureDetector V" + versionName);

        // Display the DataSource name
        SharedPreferences SP = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        ;
        String dataSourceName = SP.getString("DataSource", "Phone");
        tv = (TextView) findViewById(R.id.dataSourceTextView);
        tv.setText(String.format("%s = %s", getString(R.string.DataSource), dataSourceName));


        if (mUtil.isServerRunning()) {
            Log.i(TAG, "onStart() - server running - stopping it - isServerRunning=" + mUtil.isServerRunning());
            mUtil.writeToSysLogFile("StartupActivity.onStart() - server already running - stopping it.");
            mUtil.stopServer();
        } else {
            Log.i(TAG, "onStart() - server not running - isServerRunning=" + mUtil.isServerRunning());
        }
        // Wait 0.1 second to give the server chance to shutdown in case we have just shut it down below, then start it
        mHandler.postDelayed(() -> {
            mUtil.writeToSysLogFile("StartupActivity.onStart() - starting server after delay - isServerRunning=" + mUtil.isServerRunning());
            Log.i(TAG, "onStart() - starting server after delay -isServerRunning=" + mUtil.isServerRunning());
            mUtil.startServer();
            // Bind to the service.
            Log.i(TAG, "onStart() - binding to server");
            mUtil.writeToSysLogFile("StartupActivity.onStart() - binding to server");
            mUtil.bindToServer(mContext, mConnection);
        }, 100);

        // Check power management settings
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            Log.i(TAG, "Power Management OK - we are ignoring Battery Optimizations");
            mBatteryOptDialogDisplayed = false;
        } else {
            Log.e(TAG, "Power Management Problem - not ignoring Battery Optimisations");
            //mUtil.showToast("WARNING - Phone is Optimising OpenSeizureDetector Battery Usage - this is likely to prevent it working correctly when running on battery!");
            if (!mBatteryOptDialogDisplayed) showBatteryOptimisationWarningDialog();
        }


        // Check to see if this is the first time the app has been run, and display welcome dialog if it is.
        checkFirstRun();

        // start timer to refresh user interface every second.
        mUiTimer = new Timer();
        mUiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(serverStatusRunnable);
                //updateServerStatus();
            }
        }, 0, 2000);


    }

    /**
     * getVersionName - returns the version name (e.g. 2.3.2) for this application.
     *
     * @param context
     * @param cls     - a class from which to determine the version mame.
     * @return the string version name specified in AndroidManifest.xml
     */
    public static String getVersionName(Context context, Class cls) {
        try {
            ComponentName comp = new ComponentName(context, cls);
            PackageInfo pinfo = context.getPackageManager().getPackageInfo(
                    comp.getPackageName(), 0);
            return "Version: " + pinfo.versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getVersionName Exception - " + e.toString());
            return null;
        }
    }

        /**
         * checkFirstRun - checks to see if this is the first run of the app after installation or upgrade.
         * if it is, the relevant dialog message is displayed.  If not, the routine just exists so start-up can continue.
         */
    public void checkFirstRun() {
        String storedVersionName = "";
        String versionName;
        AlertDialog UpdateDialog;
        AlertDialog FirstRunDialog;
        SharedPreferences prefs;
        Log.i(TAG, "checkFirstRun()");
        versionName = this.getVersionName(this, StartupActivity.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        storedVersionName = (prefs.getString("AppVersionName", null));
        Log.v(TAG, "storedVersionName=" + storedVersionName + ", versionName=" + versionName);

        // CHeck for new installation
        //storedVersionName = null;  // FIXME Force first run dialog for easier testing ****************************
        if (storedVersionName == null || storedVersionName.length() == 0) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);
            final String s = new String(
                    getString(R.string.FirstRunDlgMsg));
            alertDialogBuilder
                    .setTitle(getString(R.string.FirstRunDlgTitle))
                    .setMessage(Html.fromHtml(s))
                    .setCancelable(false)
                    .setNeutralButton(getString(R.string.closeBtnTxt), (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        //MainActivity.this.finish();
                    })
                    .setPositiveButton("Privacy Policy", (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        String url = OsdUtil.PRIVACY_POLICY_URL;
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        dialog.cancel();
                        mDialogDisplayed = false;
                    })
                    .setNegativeButton("Data Sharing", (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        String url = OsdUtil.DATA_SHARING_URL;
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        dialog.cancel();
                        mDialogDisplayed = false;
                    })
            ;
            FirstRunDialog = alertDialogBuilder.create();
            Log.i(TAG, "Displaying First Run Dialog");
            FirstRunDialog.show();
            mDialogDisplayed = true;
        } else if (!storedVersionName.equals(versionName)) {
            // Check for update of installed application
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);
            final String s = new String(
                    getString(R.string.UpgradeMsg) + getString(R.string.changelog)
            );

            alertDialogBuilder
                    .setTitle(getString(R.string.UpdateDialogTitleTxt))
                    .setMessage(Html.fromHtml(s))
                    .setCancelable(false)
                    .setNeutralButton(getString(R.string.closeBtnTxt), (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        //MainActivity.this.finish();
                    })
                    .setPositiveButton("Privacy Policy", (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        String url = OsdUtil.PRIVACY_POLICY_URL;
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        dialog.cancel();
                        mDialogDisplayed = false;
                    })
                    .setNegativeButton("Data Sharing", (dialog, id) -> {
                        dialog.cancel();
                        mDialogDisplayed = false;
                        String url = OsdUtil.DATA_SHARING_URL;
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        dialog.cancel();
                        mDialogDisplayed = false;
                    });
            UpdateDialog = alertDialogBuilder.create();
            Log.i(TAG, "Displaying Update Dialog");
            UpdateDialog.show();
            mDialogDisplayed = true;
        } else {
            Log.v(TAG, "App has already been run - not showing dialog.");
        }
        Log.i(TAG, "Setting Stored AppVersionName to" + versionName);
        prefs.edit().putString("AppVersionName", versionName).commit();
    }

    private void showBatteryOptimisationWarningDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        final SpannableString s = new SpannableString(
                getString(R.string.battery_usage_optimisation_dialog_text)
        );
        // This makes the links display as links, but they do not respond to clicks for some reason...
        Linkify.addLinks(s, Linkify.ALL);
        alertDialogBuilder
                .setTitle(R.string.battery_usage_optimisation_dialog_title)
                .setMessage(s)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.okBtnTxt), (dialog, id) -> {
                    dialog.cancel();
                    mBatteryOptDialogDisplayed = false;
                });
        mBatteryOptDialog = alertDialogBuilder.create();
        Log.i(TAG, "Displaying Update Dialog");
        mBatteryOptDialog.show();
        mBatteryOptDialogDisplayed = true;
    }

    /*****************************************************************************/
    public boolean arePermissionsOK() {
        boolean allOk = true;
        Log.v(TAG, "arePermissionsOK");
        for (int i = 0; i < REQUIRED_PERMISSIONS.length; i++) {
            if (ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[i])
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, REQUIRED_PERMISSIONS[i] + " Permission Not Granted");
                allOk = false;
            }
        }
        return allOk;
    }

    public boolean areSMSPermissions1OK() {
        boolean allOk = true;
        Log.v(TAG, "areSMSPermissions1 OK()");
        for (int i = 0; i < SMS_PERMISSIONS_1.length; i++) {
            if (ContextCompat.checkSelfPermission(this, SMS_PERMISSIONS_1[i])
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "areSMSPermissions1OK: "+SMS_PERMISSIONS_1[i] + " Permission Not Granted");
                allOk = false;
            }
        }
        return allOk;
    }


    public boolean areLocationPermissions1OK() {
        boolean allOk = true;
        Log.v(TAG, "areLocationPermissions1 OK()");
        for (int i = 0; i < LOCATION_PERMISSIONS_1.length; i++) {
            if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSIONS_1[i])
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, LOCATION_PERMISSIONS_1[i] + " Permission Not Granted");
                allOk = false;
            }
        }
        return allOk;
    }

    public boolean areLocationPermissions2OK() {
        boolean allOk = true;
        Log.v(TAG, "areSMSPermissions2OK()");
        for (int i = 0; i < LOCATION_PERMISSIONS_2.length; i++) {
            if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSIONS_2[i])
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, LOCATION_PERMISSIONS_2[i] + " Permission Not Granted");
                allOk = false;
            }
        }
        return allOk;
    }

    public void requestPermissions(AppCompatActivity activity) {
        if (mPermissionsRequested) {
            Log.i(TAG, "requestPermissions() - request already sent - not doing anything");
        } else {
            Log.i(TAG, "requestPermissions() - requesting permissions");
            for (int i = 0; i < REQUIRED_PERMISSIONS.length; i++) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        REQUIRED_PERMISSIONS[i])) {
                    Log.i(TAG, "shouldShowRationale for permission" + REQUIRED_PERMISSIONS[i]);
                }
            }
            ActivityCompat.requestPermissions(activity,
                    REQUIRED_PERMISSIONS,
                    42);
            mPermissionsRequested = true;
        }
    }

    public void requestSMSPermissions() {
        if (mSmsPermissionsRequested) {
            Log.i(TAG, "requestSMSPermissions() - request already sent - not doing anything");
        } else {
            Log.i(TAG, "requestSMSPermissions() - requesting permissions");
            mSmsPermissionsRequested = true;
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);
            alertDialogBuilder
                    .setTitle(R.string.permissions_required)
                    .setMessage(R.string.sms_permissions_rationale_1)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.okBtnTxt), (dialog, id) -> {
                        dialog.cancel();
                        Log.i(TAG, "requestSMSPermissions(): Launching ActivityCompat.requestPermissions()");
                        ActivityCompat.requestPermissions(StartupActivity.this,
                                SMS_PERMISSIONS_1,
                                45);
                    })
                    .setNegativeButton(getString(R.string.cancelBtnTxt), (dialog, id) -> dialog.cancel()).create().show();
        }
    }


    public void requestLocationPermissions1() {
        if (mLocationPermissions1Requested) {
            Log.i(TAG, "requestLocationPermissions1() - request already sent - not doing anything");
        } else {
            Log.i(TAG, "requestLocationPermissions1() - requesting permissions");
            mLocationPermissions1Requested = true;
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);
            alertDialogBuilder
                    .setTitle(R.string.permissions_required)
                    .setMessage(R.string.location_permissions_rationale_1)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.okBtnTxt), (dialog, id) -> {
                        dialog.cancel();
                        Log.i(TAG, "requestLocationPermissions1(): Launching ActivityCompat.requestPermissions()");
                        ActivityCompat.requestPermissions(StartupActivity.this,
                                LOCATION_PERMISSIONS_1,
                                43);
                    })
                    .setNegativeButton(getString(R.string.cancelBtnTxt), (dialog, id) -> dialog.cancel())
                    .create().show();
        }
    }

    public void requestLocationPermissions2() {
        if (mLocationPermissions2Requested) {
            Log.i(TAG, "requestSMSPermissions2() - request already sent - not doing anything");
        } else {
            Log.i(TAG, "requestSMSPermissions2() - requesting permissions");
            mLocationPermissions2Requested = true;

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    this);
            alertDialogBuilder
                    .setTitle(R.string.permissions_required)
                    .setMessage(R.string.location_permissions_2_rationale)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.okBtnTxt), (dialog, id) -> {
                        dialog.cancel();
                        Log.i(TAG, "requestSMSPermissions(): Launching ActivityCompat.requestPermissions()");
                        ActivityCompat.requestPermissions(StartupActivity.this,
                                LOCATION_PERMISSIONS_2,
                                44);
                    })
                    .setNegativeButton(getString(R.string.cancelBtnTxt), (dialog, id) -> dialog.cancel()).create().show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult - Permission" + permissions + " = " + grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            Log.i(TAG, "Permission " + permissions[i] + " = " + grantResults[i]);
        }
    }


}
