package uk.org.openseizuredetector;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * FragmentDataSharing - SDK 17 Integral R-FREE 2024 Upgrade
 * * en_GB Java Documentation:
 * This fragment is now fully decoupled from the R-class. It uses
 * dynamic resource discovery to inflate its layout and find its views.
 * * 2012-2026 Integrity:
 * By removing 'R', we ensure the 'Eggshell' can be recompiled in
 * modern environments without resource-ID conflicts during the LSA audit.
 */
public class FragmentDataSharing extends FragmentOsdBaseClass {
    private String TAG = "FragmentDataSharing";

    public FragmentDataSharing() {
        // Required empty public constructor for Fragment stability
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        /* * en_GB: R-FREE INFLATION.
         * Instead of R.layout.fragment_data_sharing, we find the
         * integer ID at runtime.
         */
        int layoutId = getResId("fragment_data_sharing", "layout");
        if (layoutId == 0) {
            Log.e(TAG, "R-Free Error: Layout 'fragment_data_sharing' not found!");
            return null;
        }
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    protected void updateUi() {
        Log.d(TAG, "updateUi() - SDK 17 Striped & R-Free Path");

        // SDK 17 Fix: Manual null-check (Objects.isNull is SDK 19+)
        if (mRootView == null || !isAdded() || !isVisible()) {
            return;
        }

        /* * en_GB: R-FREE VIEW DISCOVERY.
         * Locating the TextView by its string name 'fragment_data_sharing_tv1'.
         */
        int tvResId = getResId("fragment_data_sharing_tv1", "id");
        TextView tv = (TextView) mRootView.findViewById(tvResId);

        if (tv != null) {
            if (mConnection != null && mConnection.mBound) {
                tv.setText("Data Sharing Status: INTEGRAL (Bound)");
            } else {
                tv.setText("**** NOT BOUND TO SERVER ***");
            }
        }
    }

    /**
     * getResId - The R-Free Engine
     * * en_GB: Dynamic resource lookup to avoid R-symbol dependencies.
     * Essential for maintaining the 2012 codebase in a 2026 build-toolchain.
     */
    private int getResId(String name, String type) {
        if (getActivity() == null) return 0;
        return getActivity().getResources().getIdentifier(
                name, type, getActivity().getPackageName());
    }
}