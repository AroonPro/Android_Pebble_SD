/*
  PrefActivity - en_GB
  Full Integral Version - Repaired for 2024 LSA Audit.
  Manages system configuration using R-Free dynamic resource mapping.
*/

package uk.org.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

/**
 * PrefActivity - en_GB
 * Handles user preferences for seizure detection and data logging.
 * * R-Free Architecture:
 * Replaces static R.xml references with dynamic OsdUtil lookups.
 * * LSA Audit 2024 Context:
 * Ensures sensitivity and notification settings are correctly loaded
 * to maintain the operational integrity of the 2012 legacy system.
 */
public class PrefActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PrefActivity";
    private static OsdUtil mUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialise mUtil for R-Free lookups
        mUtil = new OsdUtil(getApplicationContext(), new Handler());

        // Check if we are in the 'Header' view or 'Fragment' view
        if (hasHeaders()) {
            Button button = new Button(this);
            button.setText(mUtil.getStringById("BackToMonitor"));
            button.setOnClickListener(v -> finish());
            setListFooter(button);
        }
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        // en_GB: Fix for 'actual and formal argument lists differ in length'
        // We must pass both the Resource ID and the 'target' list provided by the system.
        int resId = mUtil.getResId("preference_headers", "xml");

        if (resId != 0) {
            loadHeadersFromResource(resId, target);
            Log.i(TAG, "LSA Audit: Preference headers loaded into target list.");
        } else {
            Log.e(TAG, "LSA Audit Error: preference_headers.xml not found via mUtil.");
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return fragmentName.startsWith("uk.org.openseizuredetector.PrefActivity");
    }

    /* --- Inner Fragments: R-Free Implementations --- */

    /**
     * Base class to handle dynamic XML loading for all preference fragments.
     */
    public static class RFreePreferenceFragment extends PreferenceFragment {
        protected void addRFreeResource(String xmlName) {
            int resId = mUtil.getResId(xmlName, "xml");
            if (resId != 0) {
                addPreferencesFromResource(resId);
            } else {
                Log.e(TAG, "LSA Audit Error: Preference XML '" + xmlName + "' not found.");
            }
        }
    }

    public static class GeneralPrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("general_prefs");
        }
    }

    public static class AlarmPrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("alarm_prefs");
        }
    }

    public static class LoggingPrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("logging_prefs");
        }
    }

    public static class SeizureDetectorPrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("seizure_detector_prefs");
        }
    }

    public static class PebbleDatasourcePrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("pebble_datasource_prefs");
        }
    }

    public static class NetworkDatasourcePrefsFragment extends RFreePreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addRFreeResource("network_datasource_prefs");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.i(TAG, "LSA Audit: Preference changed - " + key);
        // en_GB: Handle real-time sensitivity updates here
    }
}