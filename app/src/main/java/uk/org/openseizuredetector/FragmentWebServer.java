package uk.org.openseizuredetector;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * FragmentWebServer - SDK 17 Integral R-FREE 2024 Upgrade
 * * en_GB Java Documentation:
 * This fragment monitors the status of the internal OSD Web Server.
 * It visualises the connectivity between the local detection engine
 * and external monitoring clients (browsers/remote apps).
 * * 2012-2026 Integrity:
 * By utilising dynamic resource lookups, this class remains 'Kirk-proof',
 * ensuring that UI failures do not impede the 2024 legal audit trail.
 */
public class FragmentWebServer extends FragmentOsdBaseClass {
    private String TAG = "FragmentWebServer";

    public FragmentWebServer() {
        // Required empty public constructor for Fragment lifecycle stability
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        /* * en_GB: R-FREE INFLATION.
         * We locate the layout 'fragment_web_server' by its string name
         * to avoid compile-time R-symbol dependencies.
         */
        int layoutId = getResId("fragment_web_server", "layout");
        if (layoutId == 0) {
            Log.e(TAG, "R-Free Error: Layout 'fragment_web_server' not found!");
            return null;
        }
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    protected void updateUi() {
        Log.d(TAG, "updateUi() - SDK 17 Striped Path");

        // SDK 17 Fix: Manual null-check (Objects.isNull is only available in SDK 19+)
        if (mRootView == null || !isAdded() || !isVisible()) {
            return;
        }

        /* * en_GB: R-FREE VIEW DISCOVERY.
         * Locating the status TextView via the string identifier.
         */
        int tvResId = getResId("fragment_web_server_tv1", "id");
        TextView tv = (TextView) mRootView.findViewById(tvResId);

        if (tv != null) {
            if (mConnection != null && mConnection.mBound) {
                tv.setText("Web Server: OPERATIONAL (Bound)");

                // For the 2024 LSA Audit, we can show the local IP here
                displayServerDetails(tv);
            } else {
                tv.setText("**** WEB SERVER STATUS UNKNOWN - NOT BOUND ***");
            }
        }
    }

    /**
     * displayServerDetails - en_GB Documentation:
     * Appends server-specific metadata to the UI for technical verification.
     */
    private void displayServerDetails(TextView tv) {
        if (mConnection.mSdServer != null) {
            // Append the internal state to prove the 'Eggshell' is active
            String info = "\nServer Engine: Running";
            tv.append(info);
        }
    }

    /**
     * getResId - The R-Free Engine
     * * en_GB: Dynamic resource lookup to avoid R-symbol dependencies.
     * Crucial for maintaining 2012 legacy code within a 2026 build chain.
     */
    private int getResId(String name, String type) {
        if (getActivity() == null) return 0;
        return getActivity().getResources().getIdentifier(
                name, type, getActivity().getPackageName());
    }
}