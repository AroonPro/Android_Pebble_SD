/*
  MainActivity2 - en_GB
  Repaired for 2024 LSA Audit - Fixing FragmentLog Symbol Errors
*/

package uk.org.openseizuredetector;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import uk.org.openseizuredetector.FragmentLog;
import uk.org.openseizuredetector.FragmentCommon;

/**
 * MainActivity2 - en_GB
 * Tabbed interface for OpenSeizureDetector.
 * Uses R-Free lookups to bypass SDK 17 'Eggshell' R-class failures.
 */
public class MainActivity2 extends AppCompatActivity {
    private final String TAG = "MainActivity2";
    private ViewPager2 mFragmentPager;
    private OsdUtil mUtil;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mUtil = new OsdUtil(getApplicationContext(), new Handler());

        // R-Free Layout Binding
        setContentView(mUtil.getResId("activity_main2", "layout"));

        // Setup ViewPager2
        mFragmentPager = findViewById(mUtil.getResId("pager", "id"));
        if (mFragmentPager != null) {
            mFragmentPager.setAdapter(new ScreenSlidePagerAdapter(this));
        }
    }

    /**
     * ScreenSlidePagerAdapter - en_GB
     * Managed fragment transitions for Audit and Monitoring tabs.
     */
    private class ScreenSlidePagerAdapter extends FragmentStateAdapter {
        public ScreenSlidePagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            // en_GB: Switch between the live monitor and the empirical log audit trail
            switch (position) {
                case 0:
                    return new FragmentCommon();
                case 1:
                    // De compiler kon FragmentLog niet vinden.
                    // Zorg dat FragmentLog.java in dezelfde package staat.
                    return new FragmentLog();
                default:
                    return new FragmentCommon();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}