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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import java.util.Objects;
;
/**
 * order of boolean tracing
 * mConnection.mBound
 * mConnection.mWatchConnected
 * mConnection.hasSdSettings()
 * mWatchAppRunningCheck
 * mConnection.hasSdData
 */

/**
 * A Passive data source that expects a device to send it data periodically by sending a POST request.
 * The POST network request is handled in the SDWebServer class, which calls the 'updateFrom JSON()'
 * function to send the data to this datasource.
 * SdWebServer expects POST requests to /data and /settings URLs to send data or watch settings.
 */
public class SdDataSourceAw extends SdDataSource {
    private String TAG = "SdDataSourceAw";
    private final String mAppPackageName = "uk.org.openseizuredetector.aw";
    //private final String mAppPackageName = "uk.org.openseizuredetector";
    private Intent aWIntent = null;



    public SdDataSourceAw(Context context, Handler handler,
                          SdDataReceiver sdDataReceiver) {
        super(context, handler, sdDataReceiver);
        mName = "AndroidWear";
        // Set default settings from XML files (mContext is set by super().
        PreferenceManager.setDefaultValues(mContext,
                R.xml.network_passive_datasource_prefs, true);
    }


    /**
     * powerUpdateReceiver with coding from:
     * https://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android
     * */

    public class IntentBroadCastReceiver  extends BroadcastReceiver {
        public boolean isRegistered = false;

        /**
         * register receiver
         * @param context - Context
         * @param filter - Intent Filter
         * @return see Context.registerReceiver(BroadcastReceiver,IntentFilter)
         */
        public Intent register(Context context, IntentFilter filter) {
            try {
                // ceph3us note:
                // here I propose to create
                // a isRegistered(Context) method
                // as you can register receiver on different context
                // so you need to match against the same one :)
                // example  by storing a list of weak references
                // see LoadedApk.class - receiver dispatcher
                // its and ArrayMap there for example
                return !isRegistered
                        ? context.registerReceiver(this, filter)
                        : null;
            } finally {
                isRegistered = true;
            }
        }

        /**
         * unregister received
         * @param context - context
         * @return true if was registered else false
         */
        public boolean unregister(Context context) {
            // additional work match on context before unregister
            // eg store weak ref in register then compare in unregister
            // if match same instance
            return isRegistered
                    && unregisterInternal(context);
        }

        private boolean unregisterInternal(Context context) {
            context.unregisterReceiver(this);
            isRegistered = false;
            return true;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            intentReceivedAction(intent);
        }

    }

    private IntentBroadCastReceiver intentBroadCastReceiver = null;

    /**
     * Start the datasource updating - initialises from sharedpreferences first to
     * make sure any changes to preferences are taken into account.
     */
    public void start() {
        Log.i(TAG, "start()");
        mUtil.writeToSysLogFile("SdDataSourceAw.start()");
        super.start();


        // Now start the AndroidWear companion app
        PackageManager manager = mContext.getPackageManager();
        aWIntent = manager.getLaunchIntentForPackage(Constants.GLOBAL_CONSTANTS.mAppPackageNameWearReceiver);
        Log.i(TAG,"aWIntent: " + aWIntent);
        if (aWIntent == null) {
            mUtil.showToast("Error - OpenSeizureDetector Android Wear App is not installed - please install it and run it");
            installAwApp();
        } else {
            try {
                aWIntent.setClassName(aWIntent.getPackage(),".WearReceiver");
                //aWIntent = new Intent();
                //aWIntent.setPackage(Constants.GLOBAL_CONSTANTS.mAppPackageNameWearReceiver);
                //FIXME: tell me how to encorporate <data ######## /> with:
                // .setData()
                // and: launch DebugActivity from debugger.
                // (this is one way of 2 way communication.)
                SdData sdData = getSdData();
                aWIntent.putExtra("data","Start");
                aWIntent.putExtra("mSdData", sdData.toSettingsJSON());
                //aWIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(aWIntent);
            } catch (Exception e){
                Log.e(TAG,"start() encountered an error",e);
                if (Objects.equals(intentBroadCastReceiver,null))
                    intentBroadCastReceiver = new IntentBroadCastReceiver();
                if (intentBroadCastReceiver.isRegistered)
                    intentBroadCastReceiver.unregister(mContext);
                mHandler.postDelayed(()->{
                    intentBroadCastReceiver.register(mContext,new IntentFilter(Constants.GLOBAL_CONSTANTS.mSdServerIntent));
                    aWIntent = new Intent(Constants.ACTION.REGISTER_START_INTENT_AW);
                    aWIntent.putExtra("setContext","setByPebbleSD");
                    mContext.sendBroadcast(aWIntent);
                },100);
            }
        }
    }

    /**
     * Stop the datasource from updating
     */
    public void stop() {
        Log.i(TAG, "stop()");
        mUtil.writeToSysLogFile("SdDataSourceAw.stop()");
        if (!Objects.equals(aWIntent, null)) {
            aWIntent.putExtra("data","Stop");
            mContext.startActivity(aWIntent);
        }
        super.stop();
    }

    private void installAwApp() {
        // from https://stackoverflow.com/questions/11753000/how-to-open-the-google-play-store-directly-from-my-android-application
        // First tries to open Play Store, then uses URL if play store is not installed.
        try {
            aWIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + mAppPackageName));
            aWIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(aWIntent);
        } catch (android.content.ActivityNotFoundException anfe) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + mAppPackageName));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);
        }
    }

    public void startWearReceiverApp(){
        try {
            aWIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            aWIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            SdData sdData = getSdData();
            aWIntent.putExtra("data",Uri.parse(Constants.ACTION.START_MOBILE_RECEIVER_ACTION));
            aWIntent.putExtra("mSdData", sdData.toSettingsJSON());
            mContext.startActivity(aWIntent);
        } catch (android.content.ActivityNotFoundException anfe) {
            aWIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + mAppPackageName));
            aWIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(aWIntent);
        }

    }

    public void intentReceivedAction(Intent intent){

    }
    public void startWearSDApp(){
        try{

            aWIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            aWIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            SdData sdData = getSdData();
            aWIntent.putExtra("data",Uri.parse(Constants.ACTION.START_WEAR_APP_ACTION));
            aWIntent.putExtra("mSdData", sdData.toSettingsJSON());
            mContext.startActivity(aWIntent);
        }catch ( Exception e ){
            Log.e(TAG,"startWearSDApp: Error occoured",e);
        }

    }

    public void startMobileSD(){
        try{

            aWIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            aWIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            SdData sdData = getSdData();
            aWIntent.putExtra("data",Uri.parse(Constants.ACTION.PUSH_SETTINGS_ACTION));
            aWIntent.putExtra("mSdData", sdData.toSettingsJSON());
            mContext.startActivity(aWIntent);
        }catch ( Exception e ){
            Log.e(TAG,"startWearSDApp: Error occoured",e);
        }
    }


    public void mobileBatteryPctUpdate(){
        try{

            aWIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            aWIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            aWIntent.putExtra("data",Uri.parse(Constants.ACTION.BATTERYUPDATE_ACTION));
            aWIntent.putExtra("mobileBatteryPct", ((SdServer)mSdDataReceiver).batteryPct);
            mContext.startActivity(aWIntent);
        }catch ( Exception e ){
            Log.e(TAG,"startWearSDApp: Error occoured",e);
        }
    }


}







