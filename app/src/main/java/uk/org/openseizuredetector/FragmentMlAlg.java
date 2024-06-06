package uk.org.openseizuredetector;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Objects;

/**
 * FragmentMlAlg - en_GB
 * UI Fragment for Machine Learning Algorithm status.
 * * R-Free Architecture:
 * Replaces R.layout and R.id with dynamic OsdUtil lookups to ensure
 * the 2026 build remains stable on the 2012 'Eggshell' (SDK 17).
 * * LSA Audit 2024:
 * Provides transparency on algorithmic decision-making, crucial for
 * legal and safety verification.
 */
public class FragmentMlAlg extends FragmentOsdBaseClass {
    private final String TAG = "FragmentMlAlg";

    public FragmentMlAlg() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // R-Free Layout Inflation - en_GB
        int layoutId = mUtil.getResId("fragment_ml_alg", "layout");
        if (layoutId == 0) {
            Log.e(TAG, "Layout fragment_ml_alg NOT FOUND");
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        mRootView = inflater.inflate(layoutId, container, false);
        return mRootView;
    }

    @Override
    protected void updateUi() {
        Log.v(TAG, "updateUi() - LSA Audit Check");

        // Safety check for SDK 17 'Eggshell' stability
        if (mRootView == null || !isAdded() || !isVisible()) {
            return;
        }

        // R-Free View Binding
        int textViewId = mUtil.getResId("fragment_ml_alg_tv1", "id");
        TextView tv = (TextView) mRootView.findViewById(textViewId);

        if (tv != null) {
            if (mConnection != null && mConnection.mBound) {
                // en_GB: Standardized status strings via mUtil
                tv.setText(mUtil.getStringById("BoundToServer"));
            } else {
                tv.setText("*** NOT BOUND TO SERVER ***");
                Log.w(TAG, "UI Update failed: Server not bound.");
            }
        }
    }
}