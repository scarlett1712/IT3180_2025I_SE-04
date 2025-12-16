package com.se_04.enoti.home.accountant;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.BulkUtilityBillActivity;
import com.se_04.enoti.finance.admin.ConfigRatesActivity;
import com.se_04.enoti.finance.admin.CreateFinanceActivity;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment_Accountant extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;

    // Texts
    private TextView txtTotalRevenue, txtTotalExpense;

    // Chart + Spinners
    private BarChart barChart;
    private Spinner spinnerMonth, spinnerYear;

    // Buttons
    private LinearLayout btnCreateFee, btnUtility, btnConfigPrice;

    // Time filters
    private int selectedMonth = 0;
    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_accountant, container, false);

        initViews(view);
        setupWelcomeViews(view);
        setupSpinners();
        setupChart();
        setupActions();

        loadFinancialStats();

        return view;
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        txtTotalRevenue = view.findViewById(R.id.txtTotalRevenue);
        txtTotalExpense = view.findViewById(R.id.txtTotalExpense);

        barChart = view.findViewById(R.id.barChart);

        spinnerMonth = view.findViewById(R.id.spinner_month);
        spinnerYear = view.findViewById(R.id.spinner_year);

        btnCreateFee = view.findViewById(R.id.btnCreateFee);
        btnUtility = view.findViewById(R.id.btnUtility);
        btnConfigPrice = view.findViewById(R.id.btnConfigPrice);

        swipeRefreshLayout.setOnRefreshListener(this::loadFinancialStats);
    }

    private void setupSpinners() {
        // Setup năm
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        List<String> years = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            years.add(String.valueOf(currentYear - i));
        }

        if (getContext() != null) {
            ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item, years);
            yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerYear.setAdapter(yearAdapter);
        }

        // Tháng hiện tại
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
        spinnerMonth.setSelection(currentMonth);

        spinnerMonth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                selectedMonth = pos;
                loadFinancialStats();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerYear.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                selectedYear = Integer.parseInt(parent.getItemAtPosition(pos).toString());
                loadFinancialStats();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupActions() {
        btnCreateFee.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CreateFinanceActivity.class)));

        btnUtility.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), BulkUtilityBillActivity.class)));

        btnConfigPrice.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ConfigRatesActivity.class)));
    }

    // ------------------------------
    //  🔥 CẤU HÌNH CHART NHƯ ADMIN
    // ------------------------------
    private void setupChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);
        barChart.getAxisRight().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Thu", "Chi"}));
    }

    // ------------------------------
    //  🔥 TẢI THỐNG KÊ
    // ------------------------------
    private void loadFinancialStats() {
        String url = ApiConfig.BASE_URL + "/api/finance/statistics";
        url += "?year=" + selectedYear;
        if (selectedMonth > 0) url += "&month=" + selectedMonth;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        double revenue = response.getDouble("revenue");
                        double expense = response.getDouble("expense");

                        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                        txtTotalRevenue.setText(formatter.format(revenue));
                        txtTotalExpense.setText(formatter.format(expense));

                        updateChart(revenue, expense);

                    } catch (JSONException e) { e.printStackTrace(); }

                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                },
                error -> {
                    Log.e("Stats", "Error: " + error.getMessage());
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                });

        Volley.newRequestQueue(requireContext()).add(request);
    }

    // ------------------------------
    //  🔥 UPDATE CHART GIỐNG ADMIN
    // ------------------------------
    private void updateChart(double revenue, double expense) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) revenue));
        entries.add(new BarEntry(1f, (float) expense));

        BarDataSet dataSet = new BarDataSet(entries, "Thống kê Thu/Chi");
        dataSet.setColors(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"));
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);

        barChart.setData(data);
        barChart.animateY(800);
        barChart.invalidate();
    }
    private void setupWelcomeViews(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null && currentUser.getName() != null)
                ? currentUser.getName()
                : "Cư dân";

        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" :
                (hour >= 11 && hour < 14) ? "trưa" :
                        (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }
}
