package com.se_04.enoti.finance.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.FinanceAdapter;
import com.se_04.enoti.finance.FinanceItem;
import com.se_04.enoti.finance.FinanceRepository;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class ManageFinanceFragment extends Fragment {

    // --- UI Components ---
    private TextView txtRevenue, txtExpense;
    private BarChart barChart;
    private SearchView searchView;
    private Spinner spinnerFilterType; // Lọc trạng thái (Tất cả, Đã đóng...)
    private Spinner spinnerMonth;      // Lọc tháng cho CHART
    private FloatingActionButton btnAdd;
    private RecyclerView recyclerView;

    // --- Data & Logic ---
    private FinanceAdapter adapter;
    private final List<FinanceItem> allFinances = new ArrayList<>(); // Danh sách gốc
    private final Handler refreshHandler = new Handler();
    private int selectedMonth = 0; // 0 = Tất cả năm, 1-12 = Tháng cụ thể

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadFinanceList();   // Tải danh sách
                loadFinancialStats(); // Tải số liệu biểu đồ
                refreshHandler.postDelayed(this, 10000); // 10s refresh 1 lần
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_finance, container, false);

        initViews(view);
        setupWelcome(view);
        setupChart();
        setupRecyclerView();
        setupListeners();

        return view;
    }

    private void initViews(View view) {
        txtRevenue = view.findViewById(R.id.txtTotalRevenue);
        txtExpense = view.findViewById(R.id.txtTotalExpense);
        barChart = view.findViewById(R.id.barChart);

        searchView = view.findViewById(R.id.search_view);
        spinnerFilterType = view.findViewById(R.id.spinner_filter);
        spinnerMonth = view.findViewById(R.id.spinner_month);
        btnAdd = view.findViewById(R.id.btnAddReceipt);
        recyclerView = view.findViewById(R.id.recyclerViewManageFinance);
    }

    private void setupWelcome(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Admin";
        txtWelcome.setText("Xin chào " + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }

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

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FinanceAdapter(new ArrayList<>(), null);
        recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        // 1. Nút thêm
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateFinanceActivity.class);
            startActivity(intent);
        });

        // 2. Tìm kiếm
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { adapter.getFilter().filter(query); return false; }
            @Override
            public boolean onQueryTextChange(String newText) { adapter.getFilter().filter(newText); return false; }
        });

        // 3. Spinner Loại (Trạng thái) -> Lọc danh sách
        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedType = parent.getItemAtPosition(position).toString();
                adapter.filterByType(selectedType);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. 🔥 Spinner Tháng -> CHỈ CẬP NHẬT BIỂU ĐỒ (Không lọc danh sách)
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = position; // 0: Tất cả, 1-12: Tháng

                // Chỉ gọi hàm load stats cho biểu đồ
                loadFinancialStats();

                // ❌ ĐÃ BỎ: filterListByMonth(); -> Danh sách không bị ảnh hưởng
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- API 1: Tải danh sách (Hiển thị tất cả) ---
    private void loadFinanceList() {
        FinanceRepository.getInstance().fetchAdminFinances(requireContext(),
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;

                        // Lọc trùng & Lưu vào danh sách gốc
                        allFinances.clear();
                        HashSet<Integer> seenIds = new HashSet<>();
                        for (FinanceItem item : finances) {
                            if (item != null && !seenIds.contains(item.getId())) {
                                seenIds.add(item.getId());
                                allFinances.add(item);
                            }
                        }
                        // Sắp xếp mới nhất lên đầu
                        allFinances.sort((f1, f2) -> f2.getId() - f1.getId());

                        // 🔥 CẬP NHẬT TRỰC TIẾP TOÀN BỘ DANH SÁCH (Không lọc theo tháng nữa)
                        if (adapter != null) {
                            adapter.updateList(allFinances);

                            // Re-apply filter trạng thái (nếu đang chọn filter type khác "Tất cả")
                            if (spinnerFilterType != null && spinnerFilterType.getSelectedItem() != null) {
                                adapter.filterByType(spinnerFilterType.getSelectedItem().toString());
                            }
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Log.e("ManageFinance", "Load list error: " + message);
                    }
                });
    }

    // --- API 2: Tải thống kê biểu đồ (Có lọc theo tháng) ---
    private void loadFinancialStats() {
        // URL chuẩn (không có 's' ở finance)
        String url = ApiConfig.BASE_URL + "/api/finance/statistics";

        // Thêm tham số tháng/năm nếu chọn tháng cụ thể
        if (selectedMonth > 0) {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            url += "?month=" + selectedMonth + "&year=" + year;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        double revenue = response.getDouble("revenue");
                        double expense = response.getDouble("expense");

                        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                        if (txtRevenue != null) txtRevenue.setText(formatter.format(revenue));
                        if (txtExpense != null) txtExpense.setText(formatter.format(expense));

                        updateChart(revenue, expense);

                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> Log.e("Stats", "Error loading stats: " + error.getMessage())
        );

        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void updateChart(double revenue, double expense) {
        if (barChart == null) return;

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

    @Override
    public void onResume() {
        super.onResume();
        loadFinanceList();
        loadFinancialStats();
        refreshHandler.postDelayed(refreshRunnable, 10000);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}