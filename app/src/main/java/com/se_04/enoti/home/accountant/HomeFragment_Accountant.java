package com.se_04.enoti.home.accountant;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.se_04.enoti.R;
import com.se_04.enoti.finance.admin.BulkUtilityBillActivity;
import com.se_04.enoti.finance.admin.ConfigRatesActivity;
import com.se_04.enoti.finance.admin.CreateFinanceActivity;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment_Accountant extends Fragment {

    // Views
    private TextView txtTotalRevenue, txtTotalExpense;
    private Spinner spinnerMonth, spinnerYear;
    private BarChart barChart;
    private LinearLayout btnCreateFee, btnUtility, btnConfigPrice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_accountant, container, false);

        initViews(view);
        setupSpinners();
        setupActions();

        // Tải dữ liệu mặc định (tháng hiện tại)
        fetchDashboardData();

        return view;
    }

    private void initViews(View view) {
        // Thống kê text
        txtTotalRevenue = view.findViewById(R.id.txtTotalRevenue);
        txtTotalExpense = view.findViewById(R.id.txtTotalExpense);

        // Spinners & Chart
        spinnerMonth = view.findViewById(R.id.spinner_month);
        spinnerYear = view.findViewById(R.id.spinner_year);
        barChart = view.findViewById(R.id.barChart);

        // Các nút chức năng (LinearLayout)
        btnCreateFee = view.findViewById(R.id.btnCreateFee);
        btnUtility = view.findViewById(R.id.btnUtility);
        btnConfigPrice = view.findViewById(R.id.btnConfigPrice);
    }

    private void setupSpinners() {
        // 1. Setup Spinner Năm (Ví dụ: 5 năm gần nhất)
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        List<String> years = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            years.add(String.valueOf(currentYear - i));
        }
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);

        // 2. Chọn mặc định tháng/năm hiện tại
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH); // 0-11
        spinnerMonth.setSelection(currentMonth);
    }

    private void setupActions() {
        // 1. Tạo khoản thu mới
        btnCreateFee.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CreateFinanceActivity.class)));

        // 2. Chốt số Điện/Nước
        btnUtility.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), BulkUtilityBillActivity.class)));

        // 4. Cấu hình đơn giá
        btnConfigPrice.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ConfigRatesActivity.class)));
    }

    // --- API & DATA ---

    private void fetchDashboardData() {
        // Lấy tháng/năm đang chọn
        // Lưu ý: Tháng trong Java Calendar là 0-11, API có thể cần 1-12
        int month = spinnerMonth.getSelectedItemPosition() + 1;
        String year = spinnerYear.getSelectedItem().toString();

        String url = ApiConfig.BASE_URL + "/api/finance/dashboard?month=" + month + "&year=" + year;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // 1. Hiển thị Tổng Thu / Tổng Chi
                        double revenue = response.optDouble("total_revenue", 0);
                        double expense = response.optDouble("total_expense", 0); // Hoặc total_debt tùy API

                        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                        txtTotalRevenue.setText(formatter.format(revenue));
                        txtTotalExpense.setText(formatter.format(expense));

                        // 2. Vẽ biểu đồ (Nếu API trả về dữ liệu biểu đồ)
                        if (response.has("chart_data")) {
                            setupChart(response.getJSONArray("chart_data"));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(getContext(), "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show()
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void setupChart(JSONArray data) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        final ArrayList<String> labels = new ArrayList<>();

        try {
            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.getJSONObject(i);
                // Giả sử API trả về: {"day": "01", "amount": 500000}
                float amount = (float) obj.optDouble("amount", 0);
                entries.add(new BarEntry(i, amount));
                labels.add(obj.optString("day"));
            }
        } catch (Exception e) { e.printStackTrace(); }

        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu ngày");
        dataSet.setColor(getResources().getColor(R.color.purple_primary)); // Màu cột
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barChart.setData(barData);

        // Cấu hình trục X (Hiển thị ngày)
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);

        barChart.getDescription().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate(); // Refresh
    }
}