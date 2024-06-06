package uk.org.openseizuredetector;

import android.graphics.Color;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.View.OnClickListener;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FragmentHrAlg - SDK 17 Integral 2024 Upgrade
 * * en_GB Java Documentation:
 * This class handles the Heart Rate (HR) algorithm visualisation.
 * It is 'Tuned' for SDK 17 (Jelly Bean) to ensure 2012 data-integrity
 * remains accessible in 2026.
 * * R-FREE STRATEGY:
 * We replace direct R-string references with dynamic lookups via Context,
 * ensuring the 'Eggshell' does not break if resource IDs shift.
 */
public class FragmentHrAlg extends FragmentOsdBaseClass {
    private String TAG = "FragmentHrAlg";

    private LineChart mLineChart;
    private LineDataSet lineDataSet;
    private TextView tvAvgAHr, tvHr, tv, tvCurrent;
    private View mRootViewLocal; // Local reference to avoid fragment lifecycle 'Red Outs'

    public FragmentHrAlg() {
        // Required empty public constructor for 2012 Fragment standards
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // SDK 17: Initialise dataset with manual colour hex for R-Free stability
        lineDataSet = new LineDataSet(new ArrayList<Entry>(), "Heart Rate History");
        lineDataSet.setValueTextColor(Color.BLACK);
        lineDataSet.setValueTextSize(18f);
        lineDataSet.setDrawValues(false);
        lineDataSet.setCircleSize(0f);
        lineDataSet.setLineWidth(3f);
        lineDataSet.setColor(0xFFFF0000); // R-Free: Direct Hex for Red
    }

    @Override
    public void onResume() {
        super.onResume();
        // SDK 17: Replace Lambda with Anonymous Inner Class for OnClickListener
        if (mRootView != null) {
            View switchBtn = mRootView.findViewById(getResId("switch1", "id"));
            if (switchBtn != null) {
                switchBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        updateUi();
                    }
                });
            }
            setupChart();
        }
    }

    private void setupChart() {
        mLineChart = (LineChart) mRootView.findViewById(getResId("lineChartBattHist", "id"));
        if (mLineChart == null) return;

        mLineChart.getLegend().setEnabled(false);
        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);

        YAxis yAxis = mLineChart.getAxisLeft();
        yAxis.setAxisMinValue(40f);
        yAxis.setAxisMaxValue(240f);
        yAxis.setTextColor(Color.WHITE);

        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float v) {
                return new DecimalFormat("###").format(v);
            }
        });
        mLineChart.getAxisRight().setEnabled(false);
    }

    @Override
    protected void updateUi() {
        // SDK 17 Striped: No Objects.isNull, use classic != null
        if (mRootView == null || !isAdded() || !isVisible()) return;

        tv = (TextView) mRootView.findViewById(getResId("fragment_hr_alg_tv1", "id"));
        tvHr = (TextView) mRootView.findViewById(getResId("current_hr_tv", "id"));
        tvAvgAHr = (TextView) mRootView.findViewById(getResId("adaptive_avg_hr_tv", "id"));

        if (mConnection != null && mConnection.mBound && mConnection.mSdServer != null) {
            if (tv != null) tv.setText("Bound to Server");

            // Accessing the 'Kirk' bridge directly for Heart Rate data
            if (tvHr != null)
                tvHr.setText(String.valueOf((short) mConnection.mSdServer.mSdData.mHR));

            // Extracting Circular Buffer data from the engine
            if (mConnection.mSdServer.mSdDataSource != null &&
                    mConnection.mSdServer.mSdDataSource.mSdAlgHr != null) {

                CircBuf hrHist = mConnection.mSdServer.mSdDataSource.mSdAlgHr.getHrHistBuff();
                if (hrHist != null && hrHist.getNumVals() > 0) {
                    renderGraph(hrHist);
                }
            }
        }
    }

    /**
     * renderGraph - SDK 17 Integral & Scope Fix
     * * en_GB Java Documentation:
     * We declare nPoints at the method level to ensure it is visible
     * for the xSpan calculation used in the LSA audit description.
     */
    private void renderGraph(CircBuf hrHist) {
        // 1. Declareer nPoints DIRECT aan het begin van de methode
        int nPoints = hrHist.getNumVals();
        double[] vals = hrHist.getVals();

        lineDataSet.clear();

        // 2. Gebruik nPoints in de loop
        for (int i = 0; i < nPoints; i++) {
            lineDataSet.addEntry(new Entry((float) vals[i], i));
        }

        LineData hrData = new LineData(lineDataSet);
        mLineChart.setData(hrData);

        // 3. THE FIX: nPoints is nu hier 'In Scope' voor de berekening
        float xSpan = (nPoints * 5.0f) / 60.0f;

        // Maak het Description object (zoals we eerder hebben gefixt)
        com.github.mikephil.charting.components.Description description =
                new com.github.mikephil.charting.components.Description();

        String descText = "HR Hist: " + String.format("%.1f", xSpan) + " mins";
        description.setText(descText);
        description.setTextColor(Color.WHITE);

        mLineChart.setDescription(description);
        mLineChart.notifyDataSetChanged();
        mLineChart.invalidate();
    }

    /**
     * getResId - The R-Free Engine
     * en_GB: Dynamic resource lookup to avoid R.id compile errors.
     */
    private int getResId(String name, String type) {
        if (getActivity() == null) return 0;
        return getActivity().getResources().getIdentifier(name, type, getActivity().getPackageName());
    }
}