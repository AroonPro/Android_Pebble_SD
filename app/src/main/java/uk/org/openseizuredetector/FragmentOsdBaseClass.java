package uk.org.openseizuredetector;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Timer;
import java.util.TimerTask;

/**
 * FragmentOsdBaseClass - Base class for fragments using SdServiceConnection.
 * Refactored for en_GB standards and dynamic resource handling.
 */
public class FragmentOsdBaseClass extends Fragment {
    protected String TAG = "FragmentOsdBaseClass";
    protected Context mContext;
    protected OsdUtil mUtil;
    protected SdServiceConnection mConnection;
    protected final Handler updateUiHandler = new Handler(Looper.getMainLooper());
    protected Timer mUiTimer;
    protected View mRootView;

    protected int okColour = Color.BLUE;
    protected int warnColour = Color.MAGENTA;
    protected int alarmColour = Color.RED;
    protected int okTextColour = Color.WHITE;
    protected int warnTextColour = Color.WHITE;
    protected int alarmTextColour = Color.BLACK;

    public FragmentOsdBaseClass() {
        // Required empty public constructor
    }

    // --- DYNAMIC RESOURCE HELPERS ---
    protected int resId(String name, String type) {
        if (isAdded()) {
            return getResources().getIdentifier(name, type, requireContext().getPackageName());
        }
        return 0;
    }

    protected View safeFind(String idName) {
        int id = resId(idName, "id");
        return (mRootView != null && id != 0) ? mRootView.findViewById(id) : null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
        mContext = getContext();
        mUtil = new OsdUtil(mContext, updateUiHandler);

        // FIX: Universele constructor met de juiste Service class
        mConnection = new SdServiceConnection(mContext);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        int layoutId = resId("fragment_sd_data_viewer", "layout");
        if (layoutId != 0) {
            return inflater.inflate(layoutId, container, false);
        }
        return null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRootView = view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");

        // FIX: Gebruik de nieuwe doBindService logica
        if (mUtil.isServerRunning()) {
            Log.i(TAG, "onResume() - Binding to Server");
            mConnection.doBindService();
        } else {
            Log.i(TAG, "onResume() - Server Not Running");
        }

        mUiTimer = new Timer();
        mUiTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateUiOnUiThread();
            }
        }, 0, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");
        if (mUiTimer != null) {
            mUiTimer.cancel();
            mUiTimer = null;
        }
        /* en_GB Java Explanation:
         * In 2012, unbinding a service that wasn't bound would crash the app.
         * We check mBound first. If true, we tell the Context to let go of the 'Aap' (the engine).
         */
        if (mConnection != null && mConnection.mBound) {
            try {
                // We gebruiken de geparkeerde context om de verbinding te verbreken
                mContext.unbindService(mConnection);
                mConnection.mBound = false; // Reset de eierschaal status
                Log.i(TAG, "Service successfully unbound from Fragment.");
            } catch (Exception e) {
                Log.e(TAG, "Unbind Error: " + e.toString());
            }
        }
    }

    private void updateUiOnUiThread() {
        updateUiHandler.post(() -> {
            // isAdded() check voorkomt crashes als fragment al weg is (Issue 176)
            if (isAdded() && mContext != null && mRootView != null) {
                try {
                    updateUi();
                } catch (Exception e) {
                    Log.e(TAG, "updateUiOnUiThread() - exception: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Safely fetches a string resource by its name.
     * Prevents "Resource Not Found" crashes during module migrations.
     */
    public String getStr(String key) {
        if (mContext == null) return key;

        int id = resId(key, "string");
        if (id != 0) {
            return mContext.getString(id);
        } else {
            // Fallback: return the key itself so the UI isn't empty if the string is missing
            Log.w(TAG, "getStr: Resource not found for key: " + key);
            return key;
        }
    }



    /**
     * Subclasses override this.
     */
    protected void updateUi() {
        TextView tv = (TextView) safeFind("fragment_sddata_viewer_tv1");
        if (tv != null) {
            if (mConnection != null && mConnection.mBound) {
                tv.setText("Bound to Server");
            } else {
                tv.setText("****NOT BOUND TO SERVER***");
            }
        }
    }
}