package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

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
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView txtRevenue, txtExpense;
    private BarChart barChart;
    private SearchView searchView;
    private Spinner spinnerFilterType; // Lọc trạng thái cho DANH SÁCH
    private Spinner spinnerMonth;      // Lọc tháng cho BIỂU ĐỒ
    private Spinner spinnerYear;       // Lọc năm cho BIỂU ĐỒ
    private ExtendedFloatingActionButton btnUtility, btnAdd;
    private RecyclerView recyclerView;

    // --- Data & Logic ---
    private FinanceAdapter adapter;
    private final List<FinanceItem> allFinances = new ArrayList<>(); // Danh sách gốc

    // Biến lưu trạng thái lọc thời gian (Mặc định: Tất cả tháng, Năm hiện tại)
    private int selectedMonth = 0;
    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);

    // Launcher để nhận kết quả khi tạo khoản thu mới xong -> Tự động reload
    private final ActivityResultLauncher<Intent> addFinanceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadAllData(); // Reload lại dữ liệu
                    Toast.makeText(getContext(), "Đã cập nhật dữ liệu mới", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_finance, container, false);

        initViews(view);
        setupWelcome(view);
        setupChart();
        setupRecyclerView();
        setupTimeFilters(); // Cài đặt Spinner Tháng/Năm
        setupOtherListeners(); // Cài đặt Search, Filter Type, FAB

        // Tải dữ liệu lần đầu
        loadAllData();

        return view;
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        txtRevenue = view.findViewById(R.id.txtTotalRevenue);
        txtExpense = view.findViewById(R.id.txtTotalExpense);
        barChart = view.findViewById(R.id.barChart);

        searchView = view.findViewById(R.id.search_view);
        spinnerFilterType = view.findViewById(R.id.spinner_filter);
        spinnerMonth = view.findViewById(R.id.spinner_month);
        spinnerYear = view.findViewById(R.id.spinner_year);
        btnAdd = view.findViewById(R.id.btnAddReceipt);
        btnUtility = view.findViewById(R.id.btnUtility);
        recyclerView = view.findViewById(R.id.recyclerViewManageFinance);

        // Cấu hình SwipeRefresh
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.purple_primary, android.R.color.holo_green_light);
            swipeRefreshLayout.setOnRefreshListener(this::loadAllData);
        }
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

    private void setupTimeFilters() {
        // 1. Cấu hình Spinner Năm (Động: hiện tại +/- 2 năm)
        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear - 2; i <= currentYear + 2; i++) {
            years.add(String.valueOf(i));
        }
        ArrayAdapter<String> adapterYear = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, years);
        adapterYear.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapterYear);

        // Chọn năm hiện tại (vị trí index 2 trong list 5 năm)
        spinnerYear.setSelection(2);
        selectedYear = currentYear;

        // 2. Sự kiện chọn THÁNG -> Chỉ reload Chart
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = position; // 0: Tất cả, 1-12: Tháng
                loadFinancialStats(); // 🔥 Chỉ gọi API thống kê
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 3. Sự kiện chọn NĂM -> Chỉ reload Chart
        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String yearStr = parent.getItemAtPosition(position).toString();
                selectedYear = Integer.parseInt(yearStr);
                loadFinancialStats(); // 🔥 Chỉ gọi API thống kê
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupOtherListeners() {
        // 1. Nút thêm (Dùng Launcher)
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateFinanceActivity.class);
            addFinanceLauncher.launch(intent);
        });

        btnUtility.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), BulkUtilityBillActivity.class);
            addFinanceLauncher.launch(intent); // Dùng chung Launcher để reload khi xong
        });

        // 2. Tìm kiếm (Lọc List)
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { adapter.getFilter().filter(query); return false; }
            @Override
            public boolean onQueryTextChange(String newText) { adapter.getFilter().filter(newText); return false; }
        });

        // 3. Spinner Loại (Lọc List)
        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedType = parent.getItemAtPosition(position).toString();
                adapter.filterByType(selectedType);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- MAIN LOADER ---
    private void loadAllData() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);
        loadFinanceList();   // Tải danh sách (toàn bộ)
        loadFinancialStats(); // Tải biểu đồ (theo bộ lọc)
    }

    // --- API 1: Tải danh sách (Admin xem tất cả) ---
    private void loadFinanceList() {
        FinanceRepository.getInstance().fetchAdminFinances(requireContext(),
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;

                        // Lọc trùng ID
                        allFinances.clear();
                        HashSet<Integer> seenIds = new HashSet<>();
                        for (FinanceItem item : finances) {
                            if (item != null && !seenIds.contains(item.getId())) {
                                seenIds.add(item.getId());
                                allFinances.add(item);
                            }
                        }
                        // Sắp xếp ID giảm dần (Mới nhất lên đầu)
                        allFinances.sort((f1, f2) -> f2.getId() - f1.getId());

                        // Cập nhật Adapter
                        if (adapter != null) {
                            adapter.updateList(allFinances);

                            // Re-apply filter loại nếu đang chọn
                            if (spinnerFilterType != null && spinnerFilterType.getSelectedItem() != null) {
                                adapter.filterByType(spinnerFilterType.getSelectedItem().toString());
                            }
                        }

                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onError(String message) {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        // Log.e("ManageFinance", "List Error: " + message);
                    }
                });
    }

    // --- API 2: Tải thống kê biểu đồ (Theo Tháng/Năm) ---
    private void loadFinancialStats() {
        // URL chuẩn
        String url = ApiConfig.BASE_URL + "/api/finance/statistics";

        // Luôn gửi kèm năm
        url += "?year=" + selectedYear;
        // Nếu chọn tháng cụ thể (khác 0) thì gửi kèm tháng
        if (selectedMonth > 0) {
            url += "&month=" + selectedMonth;
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
}