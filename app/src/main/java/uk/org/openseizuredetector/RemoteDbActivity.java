/*
 * OpenSeizureDetector - RemoteDbActivity
 * SDK 17 (Android 4.2) "Eggshell" Foundation.
 * Integral 'Rip' - No Lambdas, No SDK 19+ Objects, No R-symbols.
 */

package uk.org.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;

public class RemoteDbActivity extends AppCompatActivity {
    private String TAG = "RemoteDbActivity";
    private Context mContext;
    private UiTimer mUiTimer;
    private LogManager mLm;
    private WebView mWebView;
    private SdServiceConnection mConnection;
    private OsdUtil mUtil;
    final Handler serverStatusHandler = new Handler();
    private String TOKEN_ID = "webApiAuthToken";
    private String mRemtoteUrl = "https://osdapi.ddns.net/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        Log.v(TAG, "onCreate() - SDK 17 compliant start");

        // R-STRIP: Dynamic layout lookup to stay integral across modules
        int layoutId = getResources().getIdentifier("activity_remote_db", "layout", getPackageName());
        if (layoutId != 0) {
            setContentView(layoutId);
        }

        mUtil = new OsdUtil(getApplicationContext(), serverStatusHandler);

        // SDK 17 FIX: Objects.isNull() is SDK 19+. Use classic null check.
        if (mConnection == null) {
            mConnection = new SdServiceConnection(this);
        }

        if (mConnection != null && !mConnection.mBound) {
            mUtil.bindToServer(this, mConnection);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String remoteUrl = extras.getString("url");
            if (remoteUrl != null) mRemtoteUrl = remoteUrl;
        }

        waitForConnection();

        // SDK 17: Explicit casting is MANDATORY
        Button authBtn = (Button) findViewById(getViewId("auth_button"));
        if (authBtn != null) {
            authBtn.setOnClickListener(onAuth);
        }

        mWebView = (WebView) findViewById(getViewId("remote_db_webview"));
        if (mWebView != null) {
            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
        }
    }

    private void waitForConnection() {
        // SDK 17 logic: Check bound state manually
        if (mConnection != null && mConnection.mBound) {
            Log.d(TAG, "waitForConnection - Bound!");
            initialiseServiceConnection();
        } else {
            // SDK 17 FIX: Use Anonymous Runnable, no Lambdas allowed
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitForConnection();
                }
            }, 100);
        }
    }

    /**
     * initialiseServiceConnection - SDK 17 Pull Logic
     * We do not look for mLm in the Service directly.
     * Instead, we use the 'Kirk' pull strategy to find the engine.
     */
    /**
     * initialiseServiceConnection - SDK 17 Pull Logic
     * We do not look for mLm in the Service directly.
     * Instead, we use the 'Kirk' pull strategy to find the engine.
     */
    private void initialiseServiceConnection() {
        Log.v(TAG, "initialiseServiceConnection() - SDK 17 Path");

        /* en_GB Java Explanation:
         * The compiler 'red out' because mConnection does not have a member named 'mSdServer'.
         * In the 2012 architecture, we bind to the 'Service' first.
         * We then access the 'Server' engine through that service.
         */
        if (mConnection != null && mConnection.mSdService != null) {

            // We look inside the Service to find the parked LogManager (mLm)
            // This is the 'Kirk' way to navigate the hierarchy in SDK 17.
            if (mConnection.mSdServer != null) {
                this.mLm = mConnection.mSdServer.mLm;

                if (this.mLm != null && mWebView != null) {
                    Log.d(TAG, "Success: mLm found in mSdServer via mSdService");
                    mWebView.loadUrl(mRemtoteUrl, getAuthHeaders());
                }
            } else {
                Log.e(TAG, "Error: mSdServer is NULL inside mSdService!");
            }
        }
    }

    private HashMap<String, String> getAuthHeaders() {
        HashMap<String, String> headersMap = new HashMap<String, String>();
        String authToken = getAuthToken();
        if (authToken != null) {
            headersMap.put("Authorization", "Token " + authToken);
        }
        return headersMap;
    }

    public String getAuthToken() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getString(TOKEN_ID, null);
    }

    // SDK 17 FIX: Standard OnClickListener object
    View.OnClickListener onAuth = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.v(TAG, "onAuth triggered");
            Intent i = new Intent(mContext, AuthenticateActivity.class);
            startActivity(i);
        }
    };

    private void updateUi() {
        Log.v(TAG, "updateUi() - SDK 17 manual update");
        TextView tv = (TextView) findViewById(getViewId("authStatusTv"));
        Button btn = (Button) findViewById(getViewId("auth_button"));

        if (mLm != null && mLm.mWac != null) {
            if (mLm.mWac.isLoggedIn()) {
                if (tv != null) tv.setText("Authenticated");
                if (btn != null) btn.setText("Log Out");
            } else {
                if (tv != null) tv.setText("NOT AUTHENTICATED");
                if (btn != null) btn.setText("Log In");
            }
        }
    }

    private int getViewId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    /* * UI Timer Logic - SDK 17 style
     */
    /* * SDK 17 Lifecycle Methods - Manual Management
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "onStart()");
        waitForConnection();
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume() - Starting UI Timer");
        startUiTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause() - Stopping UI Timer");
        stopUiTimer();
    }

    /*
     * startUiTimer() - SDK 17 Anonymous Class Logic
     * Ververst de UI elke seconde (1000ms).
     */
    private void startUiTimer() {
        if (mUiTimer != null) {
            Log.v(TAG, "startUiTimer - timer already running - cancelling it");
            mUiTimer.cancel();
            mUiTimer = null;
        }
        Log.v(TAG, "startUiTimer() - starting new timer");
        // CountDownTimer is veilig vanaf SDK 1
        mUiTimer = new UiTimer(1000, 1000);
        mUiTimer.start();
    }

    /*
     * stopUiTimer() - Voorkomt memory leaks op oude toestellen
     */
    public void stopUiTimer() {
        if (mUiTimer != null) {
            Log.v(TAG, "stopUiTimer(): cancelling timer");
            mUiTimer.cancel();
            mUiTimer = null;
        }
    }

    /**
     * UiTimer Class - SDK 17 Compliant
     * Deze 'inner class' regelt de periodieke UI updates.
     */
    private class UiTimer extends CountDownTimer {
        public UiTimer(long startTime, long interval) {
            super(startTime, interval);
        }

        @Override
        public void onTick(long l) {
            // Niets doen bij elke tick, we wachten op onFinish
        }

        @Override
        public void onFinish() {
            Log.v(TAG, "UiTimer - onFinish - Updating UI");
            updateUi();
            // SDK 17: Handmatig herstarten voor een oneindige loop
            start();
        }
    }
}