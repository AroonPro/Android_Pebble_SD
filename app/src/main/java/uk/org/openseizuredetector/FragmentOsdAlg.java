package uk.org.openseizuredetector;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class FragmentOsdAlg extends FragmentOsdBaseClass {
    private String TAG = "FragmentOsdAlg";

    public FragmentOsdAlg() {
        // Required empty public constructor
    }

    /**
     * Safely fetches a string resource by its name.
     * Prevents "Resource Not Found" crashes during module migrations.
     */
    public String getStr(String key) {
        if (mContext == null) return key; // Fallback to key name if context is null

        int id = resId(key, "string");
        if (id != 0) {
            return mContext.getString(id);
        } else {
            Log.w(TAG, "getStr: Resource not found for key: " + key);
            return key; // Return the key string so the UI isn't empty
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Use resId helper for en_GB standard
        int layoutId = resId("fragment_osdalg", "layout");
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    protected void updateUi() {
        if (mConnection == null || !mConnection.mBound || mConnection.mSdService == null) {
            return;
        }

        SdData data = mConnection.mSdService.mSdData;

        // 1. Calculate Progress percentages
        long powerPc = (data.alarmThresh != 0) ? (long)(data.roiPower * 100 / data.alarmThresh) : 0;

        long specPc = (data.specPower != 0 && data.alarmRatioThresh != 0) ?
                (long)(100 * (data.roiPower * 10 / data.specPower) / data.alarmRatioThresh) : 0;

        long specRatio = (data.specPower != 0) ? (long)(10 * data.roiPower / data.specPower) : 0;
        long pSeizurePc = (long) (data.mPseizure * 100);

        // 2. Update TextViews and ProgressBars
        updateAlgorithmRow("powerTv", "powerProgressBar", "PowerEquals", data.roiPower, "Threshold", data.alarmThresh, powerPc, 75, 100);
        updateAlgorithmRow("spectrumTv", "spectrumProgressBar", "SpectrumRatioEquals", (double)specRatio, "Threshold", data.alarmRatioThresh, specPc, 75, 100);

        // Seizure Probability Bar
        ProgressBar pbSeizure = (ProgressBar) safeFind("pSeizureProgressBarM2");
        if (pbSeizure != null) {
            pbSeizure.setMax(100);
            pbSeizure.setProgress((int) pSeizurePc);
            pbSeizure.setProgressDrawable(getOsdDrawable(pSeizurePc, 30, 50));
        }

        // 3. Update Spectrum Chart (MPAndroidChart 3.x)
        updateSpectrumChart(data);
    }

    private void updateAlgorithmRow(String tvId, String pbId, String labelKey, double val, String threshKey, double thresh, long pc, int warn, int crit) {
        TextView tv = (TextView) safeFind(tvId);
        ProgressBar pb = (ProgressBar) safeFind(pbId);
        if (tv != null) {
            tv.setText(getStr(labelKey) + " " + val + " (" + getStr(threshKey) + "=" + thresh + ")");
        }
        if (pb != null) {
            pb.setMax(100);
            pb.setProgress((int) pc);
            pb.setProgressDrawable(getOsdDrawable(pc, warn, crit));
        }
    }

    private Drawable getOsdDrawable(long percent, int warn, int crit) {
        if (percent > crit) return mContext.getDrawable(resId("progress_bar_red", "drawable"));
        if (percent > warn) return mContext.getDrawable(resId("progress_bar_yellow", "drawable"));
        return mContext.getDrawable(resId("progress_bar_blue", "drawable"));
    }

    private void updateSpectrumChart(SdData data) {
        BarChart mChart = (BarChart) safeFind("chart1");
        if (mChart == null) return;

        List<BarEntry> yBarVals = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            float val = (data.simpleSpec != null && i < data.simpleSpec.length) ? data.simpleSpec[i] : 0f;
            yBarVals.add(new BarEntry((float) i, val));

            // Color bars: Red if within alarm frequency range, Gray otherwise
            if (i < data.alarmFreqMin || i > data.alarmFreqMax) {
                colors.add(Color.GRAY);
            } else {
                colors.add(Color.RED);
            }
        }

        BarDataSet barDataSet = new BarDataSet(yBarVals, "Spectrum");
        barDataSet.setColors(colors);
        barDataSet.setDrawValues(true);
        barDataSet.setValueTextColor(Color.WHITE);

        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.8f); // Replacement for setBarSpacePercent

        barData.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float v) {
                return new DecimalFormat("####").format(v);
            }
        });

        mChart.setData(barData);

        // Chart Styling for 3.x
        Description desc = new Description();
        desc.setText("");
        mChart.setDescription(desc);
        mChart.getLegend().setEnabled(false);

        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        // Custom labels for X Axis
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int i = (int) value;
                return i + "-" + (i + 1) + "Hz";
            }
        });

        YAxis yAxis = mChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(3000f);
        yAxis.setTextColor(Color.WHITE);

        mChart.getAxisRight().setEnabled(false);
        mChart.invalidate();
    }
}