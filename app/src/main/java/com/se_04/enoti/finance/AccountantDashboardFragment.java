package com.se_04.enoti.finance;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class AccountantDashboardFragment extends Fragment {

    private TextView txtRevenue, txtExpense;
    private BarChart barChart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accountant_dashboard, container, false);

        txtRevenue = view.findViewById(R.id.txtTotalRevenue);
        txtExpense = view.findViewById(R.id.txtTotalExpense);
        barChart = view.findViewById(R.id.barChart);

        setupChart();
        loadFinancialStats();

        return view;
    }

    private void setupChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawValueAboveBar(true);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Thu", "Chi"}));
    }

    private void loadFinancialStats() {
        String url = ApiConfig.BASE_URL + "/api/finances/statistics"; // Sửa đường dẫn nếu cần

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        double revenue = response.getDouble("revenue");
                        double expense = response.getDouble("expense");

                        // 1. Hiển thị số liệu text
                        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                        txtRevenue.setText(formatter.format(revenue));
                        txtExpense.setText(formatter.format(expense));

                        // 2. Vẽ biểu đồ
                        updateChart(revenue, expense);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(getContext(), "Lỗi tải thống kê", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void updateChart(double revenue, double expense) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        // Cột 1: Doanh thu (x=0)
        entries.add(new BarEntry(0f, (float) revenue));
        // Cột 2: Chi phí (x=1)
        entries.add(new BarEntry(1f, (float) expense));

        BarDataSet dataSet = new BarDataSet(entries, "Thống kê Tài chính");

        // Màu sắc: Xanh cho thu, Đỏ cho chi
        dataSet.setColors(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"));
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f); // Độ rộng cột

        barChart.setData(data);
        barChart.animateY(1500); // Hiệu ứng hiển thị
        barChart.invalidate(); // Refresh
    }
}