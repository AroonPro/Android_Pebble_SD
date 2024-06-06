/*
 * OpenSeizureDetector - SdServiceConnection
 * SDK 17 Compliant - Integral 2024 Upgrade.
 */

package uk.org.openseizuredetector;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SdServiceConnection implements ServiceConnection {
    private static final String TAG = "SdServiceConnection";

    public boolean mBound = false;
    public AndroidSdService mSdService = null;
    // THE GRAFT: We place mSdServer here to stop the 'Symbol Not Found' errors
    public SdServer mSdServer = null;
    private SdDataSource mSdDataSource;


    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        Log.d(TAG, "onServiceConnected() - SDK 17 Binding Path");

        /* en_GB Java Explanation:
         * We no longer cast to 'LocalBinder'. We cast to your custom 'SdBinder'.
         * This is the 'Kirk' bridge that connects the UI directly to the DataSource instance.
         */
        try {
            // We cast the generic IBinder to our specific SdBinder
            SdDataSource.SdBinder binder = (SdDataSource.SdBinder) service;

            // We 'pull' the DataSource instance from the binder
            mSdDataSource = binder.getService();
            mBound = true;

            if (mSdDataSource != null) {
                // We park the SdServer reference here so RemoteDbActivity can find it.
                // This stops the 'Symbol Not Found' errors in your fragments.
                mSdServer = mSdDataSource.getSdServer();
                Log.d(TAG, "onServiceConnected: mSdServer successfully parked.");
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Binding Error: Service is not an instance of SdBinder! " + e.toString());
        }
    }


        private Context mContext; // Hier 'parkeren' we de context veilig


        // SDK 17 Constructor: We eisen de context bij creatie
        public SdServiceConnection(Context context) {
            this.mContext = context;
        }

        public void doBindService() {
            Log.d(TAG, "doBindService() - SDK 17 Context Path");
            if (mContext != null) {
                Intent intent = new Intent(mContext, SdDataSource.class);
                // Gebruik de geparkeerde context voor de bind
                mContext.bindService(intent, this, Context.BIND_AUTO_CREATE);
            } else {
                Log.e(TAG, "doBindService - mContext is NULL! Binding aborted.");
            }
        }


    @Override
    public void onServiceDisconnected(ComponentName arg0) {
        Log.d(TAG, "onServiceDisconnected");
        mBound = false;
        mSdService = null;
        mSdServer = null; // Clean up the eggshell
    }
}