package uk.org.openseizuredetector;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * FragmentWatchSig - Refactored for en_GB OSD standards and MPAndroidChart 3.x.
 */
public class FragmentWatchSig extends FragmentOsdBaseClass {
    private LineChart mLineChart;
    private LineDataSet lineDataSet;

    public FragmentWatchSig() {
        TAG = "FragmentWatchSig";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lineDataSet = new LineDataSet(new ArrayList<>(), "Watch Signal Strength history");
        lineDataSet.setValueTextColor(Color.BLACK);
        lineDataSet.setValueTextSize(18f);
        lineDataSet.setDrawValues(false);
        lineDataSet.setCircleSize(0f);
        lineDataSet.setLineWidth(3f);
        lineDataSet.setColor(Color.RED);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = resId("fragment_watch_sig", "layout");
        if (layoutId != 0) {
            return inflater.inflate(layoutId, container, false);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupChart();
    }

    private void setupChart() {
        mLineChart = (LineChart) safeFind("sigStrengthLineChart");
        if (mLineChart == null) return;

        mLineChart.getLegend().setEnabled(false);

        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.WHITE);

        YAxis yAxis = mLineChart.getAxisLeft();
        yAxis.setAxisMaximum(-50f); // MPAndroidChart 3.x uses setAxisMaximum instead of setAxisMaxValue
        yAxis.setAxisMinimum(-100f);
        yAxis.setTextColor(Color.WHITE);

        yAxis.setValueFormatter(new ValueFormatter() {
            private final DecimalFormat format = new DecimalFormat("###");
            @Override
            public String getFormattedValue(float v) {
                return format.format(v);
            }
        });

        mLineChart.getAxisRight().setEnabled(false);
    }

    @Override
    protected void updateUi() {
        if (mConnection == null || !mConnection.mBound || mConnection.mSdService == null) {
            return;
        }

        TextView tvCurrSigStren = (TextView) safeFind("current_sig_strength_tv");
        SdData data = mConnection.mSdService.mSdData;

        if (tvCurrSigStren != null) {
            tvCurrSigStren.setText(String.valueOf((int) data.watchSignalStrength));
        }

        double[] histArr = data.watchSignalStrengthBuff.getVals();
        if (histArr != null && histArr.length > 0) {
            lineDataSet.clear();

            for (int i = 0; i < histArr.length; i++) {
                // MPAndroidChart 3.x: Constructor is Entry(x, y)
                lineDataSet.addEntry(new Entry((float) i, (float) histArr[i]));
            }

            // FIX: LineData no longer accepts x-values array in 3.x
            LineData histLineData = new LineData(lineDataSet);
            mLineChart.setData(histLineData);

            // FIX: Description is now an object, not a String
            float xSpan = (histArr.length * 5.0f) / 60.0f;
            String descText = "Signal Strength History " + String.format("%.1f", xSpan) + " min";

            Description description = new Description();
            description.setText(descText);
            description.setTextColor(Color.WHITE);
            mLineChart.setDescription(description);

            mLineChart.invalidate();
        }
    }
}