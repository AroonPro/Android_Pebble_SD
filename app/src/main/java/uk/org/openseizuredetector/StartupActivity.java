package uk.org.openseizuredetector;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.rohitss.uceh.UCEHandler;
import java.util.Timer;
import java.util.TimerTask;

/**
 * StartupActivity - en_GB
 * Primary entry point for the OpenSeizureDetector application.
 * * 2012-2026 Compatibility:
 * This class uses the 'R-Free' architecture via OsdUtil to map resources
 * dynamically. This prevents 'Symbol Not Found' errors in the 2026 build
 * environment caused by a volatile 'R' class.
 * * LSA Audit 2024 Context:
 * Maintenance of the 'Eggshell' (SDK 17) involves ensuring that context-bound
 * utilities (mUtil) are correctly initialised to avoid NullPointerExceptions
 * during the safety-critical startup sequence.
 */
public class StartupActivity extends AppCompatActivity {
    // TAG Shortcut using Java Reflection
    private final String TAG = this.getClass().getSimpleName();

    private int okColour = Color.BLUE;
    private int warnColour = Color.MAGENTA;
    private int alarmColour = Color.RED;
    private int okTextColour = Color.WHITE;
    private int warnTextColour = Color.BLACK;
    private int alarmTextColour = Color.BLACK;

    /**
     * OsdUtil mUtil - en_GB
     * Utility provider initialised as null. Must be bound to
     * getApplicationContext() in onCreate to ensure long-term stability
     * and prevent memory leaks.
     */
    private OsdUtil mUtil = null;

    private Timer mUiTimer;
    private SdServiceConnection mConnection;
    private boolean mStartedMainActivity = false;
    private boolean mDialogDisplayed = false;
    private Handler mHandler = new Handler();
    private boolean mBatteryOptDialogDisplayed = false;
    private AlertDialog mBatteryOptDialog;

    // Permissions and State flags
    private boolean mPermissionsRequested;
    private boolean mBindInProgress = false;
    private String mSdDataSourceName;

    // Permissions Arrays (Simplified for brevity, keep your Manifest.permissions here)
    public final String[] REQUIRED_PERMISSIONS = { Manifest.permission.WAKE_LOCK, "android.permission.POST_NOTIFICATIONS" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate() - Initialising R-Free Startup Sequence");

        // Step 1: Initialise mUtil with Application Context
        mHandler = new Handler();
        mUtil = new OsdUtil(getApplicationContext(), mHandler);

        // Step 2: Set Layout using dynamic ID
        if (mUtil != null) {
            setContentView(mUtil.getResId("startup_activity", "layout"));
        }

        // Step 3: Global Exception Handler
        new UCEHandler.Builder(this)
                .addCommaSeparatedEmailAddresses("crashreports@openseizuredetector.org.uk,")
                .build();

        // Step 4: Load Default Preferences (R-Free)
        loadRFreePrefs();

        // Keep screen on for safety during setup
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Step 5: Setup UI Buttons
        setupButtons();

        mConnection = new SdServiceConnection(getApplicationContext());
    }

    private void loadRFreePrefs() {
        String[] prefs = {"alarm_prefs", "general_prefs", "seizure_detector_prefs"};
        for (String p : prefs) {
            PreferenceManager.setDefaultValues(this, mUtil.getResId(p, "xml"), true);
        }
    }

    private void setupButtons() {
        Button bSettings = (Button) findViewById(mUtil.getResId("settingsButton", "id"));
        if (bSettings != null) {
            bSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, PrefActivity.class));
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart() - Refreshing UI status");

        TextView tvApp = (TextView) findViewById(mUtil.getResId("appNameTv", "id"));
        if (tvApp != null) {
            tvApp.setText("OpenSeizureDetector V" + mUtil.getAppVersionName());
        }

        // Start the UI Refresh Timer
        mUiTimer = new Timer();
        mUiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(serverStatusRunnable);
            }
        }, 0, 2000);
    }

    /**
     * serverStatusRunnable - en_GB
     * Core status loop. Uses mUtil.getResId to safely update UI elements
     * without direct R-class dependencies.
     */
    final Runnable serverStatusRunnable = new Runnable() {
        public void run() {
            if (mUtil == null) return;

            boolean allOk = true;
            TextView tv = (TextView) findViewById(mUtil.getResId("textItem1", "id"));
            ProgressBar pb = (ProgressBar) findViewById(mUtil.getResId("progressBar1", "id"));

            if (arePermissionsOK()) {
                tv.setText(mUtil.getStringById("AppPermissionsOk"));
                tv.setBackgroundColor(okColour);
                pb.setIndeterminate(false);
            } else {
                tv.setText(mUtil.getStringById("AppPermissionsWarning"));
                tv.setBackgroundColor(alarmColour);
                allOk = false;
                requestPermissions(StartupActivity.this);
            }

            // Server connection check
            if (allOk && !mUtil.isServerRunning()) {
                mUtil.startServer();
                allOk = false;
            }

            // Transition to main UI if all checks pass
            if (allOk && !mStartedMainActivity && !mDialogDisplayed) {
                mStartedMainActivity = true;
                startActivity(new Intent(getApplicationContext(), MainActivity2.class));
                finish();
            }
        }
    };

    public boolean arePermissionsOK() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public void requestPermissions(AppCompatActivity activity) {
        if (!mPermissionsRequested) {
            ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, 42);
            mPermissionsRequested = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mUiTimer != null) mUiTimer.cancel();
        mUtil.unbindFromServer(getApplicationContext(), mConnection);
    }
}