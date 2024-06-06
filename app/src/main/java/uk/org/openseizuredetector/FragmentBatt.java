package uk.org.openseizuredetector;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import java.util.List;

public class FragmentBatt extends FragmentOsdBaseClass {
    private String TAG = "FragmentBatt";

    private LineChart mLineChart;
    private LineDataSet lineDataSet;

    public FragmentBatt() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the dataset with OSD battery styling (Cyan/Blue for battery)
        lineDataSet = new LineDataSet(new ArrayList<>(), "Battery history");
        lineDataSet.setValueTextColor(Color.WHITE);
        lineDataSet.setDrawValues(false);
        lineDataSet.setCircleSize(0f);
        lineDataSet.setLineWidth(3f);
        lineDataSet.setColor(Color.CYAN);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = resId("fragment_batt", "layout");
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupChart(view);
    }

    private void setupChart(View v) {
        mLineChart = (LineChart) safeFind("battLineChart");
        if (mLineChart == null) return;

        mLineChart.getLegend().setEnabled(false);

        XAxis xAxis = mLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);

        YAxis yAxis = mLineChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(100f);
        yAxis.setTextColor(Color.WHITE);
        yAxis.setValueFormatter(new ValueFormatter() {
            private final DecimalFormat format = new DecimalFormat("###");
            @Override
            public String getFormattedValue(float v) {
                return format.format(v) + "%";
            }
        });

        mLineChart.getAxisRight().setEnabled(false);
    }

    @Override
    protected void updateUi() {
        if (mConnection == null || !mConnection.mBound || mConnection.mSdService == null) {
            return;
        }

        SdData data = mConnection.mSdService.mSdData;

        if (data.watchBattBuff != null) {
            double[] watchBattArr = data.watchBattBuff.getVals();
            int nVals = data.watchBattBuff.getNumVals();

            if (nVals > 0) {
                lineDataSet.clear();
                for (int i = 0; i < nVals; i++) {
                    // MPAndroidChart 3.x uses Entry(x, y)
                    lineDataSet.addEntry(new Entry((float) i, (float) watchBattArr[i]));
                }

                // FIX: LineData no longer accepts x-values String array
                LineData watchBattHistLineData = new LineData(lineDataSet);
                mLineChart.setData(watchBattHistLineData);

                // FIX: Description is now an object
                float xSpan = (nVals * 5.0f) / 60.0f;
                Description desc = new Description();
                desc.setText(getStr("watch_batt_hist") + " " + String.format("%.1f", xSpan) + " " + getStr("minutes"));
                desc.setTextColor(Color.WHITE);
                desc.setTextSize(12f);

                mLineChart.setDescription(desc);
                mLineChart.invalidate();
            }
        }
    }
}