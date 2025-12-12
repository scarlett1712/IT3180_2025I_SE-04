package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
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
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.FinanceAdapter;
import com.se_04.enoti.finance.FinanceItem;
import com.se_04.enoti.finance.FinanceRepository;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.DataCacheManager;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private Spinner spinnerFilterType, spinnerMonth, spinnerYear;
    private ExtendedFloatingActionButton btnUtility, btnAdd;
    private RecyclerView recyclerView;
    private ImageView btnExportExcel; // 🔥 Nút xuất Excel

    // 🔥 Các View Container cần ẩn/hiện tùy Role
    private View cardStatsContainer;      // CardView biểu đồ
    private View lblFinanceStatsHeader;   // Dòng chữ "Quản lý tài chính"
    private View layoutFabContainer;      // Layout chứa các nút thêm mới (FAB)
    private TextView textList;

    // --- Data & Logic ---
    private FinanceAdapter adapter;
    private final List<FinanceItem> allFinances = new ArrayList<>();

    private int selectedMonth = 0;
    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);

    // Launcher để refresh dữ liệu khi quay lại từ màn hình tạo/sửa
    private final ActivityResultLauncher<Intent> addFinanceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadAllData();
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

        // 🔥 KIỂM TRA ROLE VÀ ẨN GIAO DIỆN
        setupRoleBasedUI();

        setupChart();
        setupRecyclerView();
        setupTimeFilters();
        setupOtherListeners();

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
        recyclerView = view.findViewById(R.id.recyclerViewManageFinance);

        // Nút xuất Excel (Cần đảm bảo ID này có trong XML)
        btnExportExcel = view.findViewById(R.id.btnExportExcel);

        // Các nút FAB thêm mới
        btnAdd = view.findViewById(R.id.btnAddReceipt);
        btnUtility = view.findViewById(R.id.btnUtility);

        // Ánh xạ các Container để ẩn/hiện
        cardStatsContainer = view.findViewById(R.id.card_stats_container);
        lblFinanceStatsHeader = view.findViewById(R.id.lbl_finance_stats_header);
        layoutFabContainer = view.findViewById(R.id.layout_fab_container);
        textList = view.findViewById(R.id.textList); // Dòng chữ "Danh sách khoản thu/chi"

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.purple_primary, android.R.color.holo_green_light);
            swipeRefreshLayout.setOnRefreshListener(this::loadAllData);
        }
    }

    private void setupRoleBasedUI() {
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();

        if (currentUser != null) {
            // --- LOGIC CHO KẾ TOÁN (ACCOUNTANT) ---
            if (currentUser.getRole() == Role.ACCOUNTANT) {
                // 1. Ẩn dòng chữ "Quản lý tài chính"
                if (lblFinanceStatsHeader != null) lblFinanceStatsHeader.setVisibility(View.GONE);

                // 2. Ẩn CardView Thống kê (Biểu đồ)
                if (cardStatsContainer != null) cardStatsContainer.setVisibility(View.GONE);

                // 3. Đổi màu chữ danh sách cho dễ nhìn (nếu cần)
                if (textList != null) textList.setTextColor(Color.WHITE); // Nên để đen cho nền trắng

                // 4. Đảm bảo nút Xuất Excel HIỆN
                if (btnExportExcel != null) btnExportExcel.setVisibility(View.VISIBLE);
            }

            // --- LOGIC CHO ADMIN ---
            else if (currentUser.getRole() == Role.ADMIN) {
                // Admin thấy hết, nhưng có thể ẩn bớt FAB nếu muốn quản lý ở chỗ khác
                // Hiện tại để mặc định là hiển thị tất cả
                if (layoutFabContainer != null) layoutFabContainer.setVisibility(View.VISIBLE);
                if (btnExportExcel != null) btnExportExcel.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupWelcome(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Admin";

        // Thêm prefix chức vụ
        txtWelcome.setText("Xin chào " + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }

    private void setupChart() {
        // Nếu View đã ẩn thì không cần setup để tiết kiệm tài nguyên
        if (barChart == null || (cardStatsContainer != null && cardStatsContainer.getVisibility() == View.GONE)) return;

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
        // Nếu là kế toán (đã ẩn chart) thì không cần setup Spinner tháng/năm
        if (cardStatsContainer != null && cardStatsContainer.getVisibility() == View.GONE) return;

        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear - 2; i <= currentYear + 2; i++) {
            years.add(String.valueOf(i));
        }
        ArrayAdapter<String> adapterYear = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, years);
        adapterYear.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapterYear);
        spinnerYear.setSelection(2);
        selectedYear = currentYear;

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = position;
                loadFinancialStats();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = Integer.parseInt(parent.getItemAtPosition(position).toString());
                loadFinancialStats();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupOtherListeners() {
        // Listener cho nút tạo mới
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), CreateFinanceActivity.class);
                addFinanceLauncher.launch(intent);
            });
        }

        if (btnUtility != null) {
            btnUtility.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), BulkUtilityBillActivity.class);
                addFinanceLauncher.launch(intent);
            });
        }

        // Listener cho tìm kiếm & lọc
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { adapter.getFilter().filter(query); return false; }
            @Override
            public boolean onQueryTextChange(String newText) { adapter.getFilter().filter(newText); return false; }
        });

        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adapter.filterByType(parent.getItemAtPosition(position).toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 🔥 Listener cho nút Xuất Excel
        if (btnExportExcel != null) {
            btnExportExcel.setOnClickListener(v -> downloadExcelReport());
        }
    }

    // 🔥 HÀM TẢI FILE EXCEL (ĐÃ SỬA)
    private void downloadExcelReport() {
        Toast.makeText(getContext(), "Đang yêu cầu server...", Toast.LENGTH_SHORT).show();

        String baseUrl = ApiConfig.BASE_URL + "/api/finance/export-excel";
        String token = UserManager.getInstance(getContext()).getAuthToken();

        // 🔥 GẮN TOKEN VÀO URL
        String finalUrl = baseUrl + "?token=" + token;

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(finalUrl));

            request.setTitle("Báo cáo Tài chính");
            request.setDescription("Đang tải xuống file Excel...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Lưu vào thư mục Downloads
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "BaoCao_TaiChinh_" + System.currentTimeMillis() + ".xlsx");

            // Cho phép quét media để file hiện ngay trong thư viện
            request.allowScanningByMediaScanner();

            DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(getContext(), "Đang tải xuống! Kiểm tra thanh thông báo.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Lỗi tải: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // --- MAIN DATA LOADER ---
    private void loadAllData() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);
        loadFinanceList();

        // Chỉ tải dữ liệu biểu đồ nếu container đang hiển thị (Admin)
        if (cardStatsContainer != null && cardStatsContainer.getVisibility() == View.VISIBLE) {
            loadFinancialStats();
        }
    }

    private void loadFinanceList() {
        // 1. Load từ Cache trước để UI mượt
        loadFinanceFromCache();

        // 2. Gọi API để lấy dữ liệu mới nhất
        FinanceRepository.getInstance().fetchAdminFinances(requireContext(),
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;
                        saveFinanceToCache(finances);
                        updateListUI(finances);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                    @Override
                    public void onError(String message) {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                });
    }

    private void loadFinanceFromCache() {
        if (getContext() == null) return;
        String data = DataCacheManager.getInstance(getContext()).readCache(DataCacheManager.CACHE_FINANCE);
        if (data != null && !data.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                List<FinanceItem> cachedList = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    FinanceItem item = new FinanceItem();
                    item.setId(obj.optInt("id"));
                    item.setTitle(obj.optString("title"));
                    item.setPrice(obj.has("amount") ? obj.optLong("amount") : 0);
                    item.setType(obj.optString("type"));
                    item.setDate(obj.optString("date"));
                    item.setStatus(obj.optString("status"));
                    item.setPaidRooms(obj.optInt("paid_rooms"));
                    item.setTotalRooms(obj.optInt("total_rooms"));
                    cachedList.add(item);
                }
                updateListUI(cachedList);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void saveFinanceToCache(List<FinanceItem> list) {
        if (getContext() == null) return;
        try {
            JSONArray array = new JSONArray();
            for (FinanceItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("amount", item.getPrice());
                obj.put("type", item.getType());
                obj.put("date", item.getDate());
                obj.put("status", item.getStatus());
                obj.put("paid_rooms", item.getPaidRooms());
                obj.put("total_rooms", item.getTotalRooms());
                array.put(obj);
            }
            DataCacheManager.getInstance(getContext()).saveCache(DataCacheManager.CACHE_FINANCE, array.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateListUI(List<FinanceItem> finances) {
        allFinances.clear();
        HashSet<Integer> seenIds = new HashSet<>();
        for (FinanceItem item : finances) {
            if (item != null && !seenIds.contains(item.getId())) {
                seenIds.add(item.getId());
                allFinances.add(item);
            }
        }
        allFinances.sort((f1, f2) -> f2.getId() - f1.getId());

        if (adapter != null) {
            adapter.updateList(allFinances);
            if (spinnerFilterType != null && spinnerFilterType.getSelectedItem() != null) {
                adapter.filterByType(spinnerFilterType.getSelectedItem().toString());
            }
        }
    }

    private void loadFinancialStats() {
        if (cardStatsContainer == null || cardStatsContainer.getVisibility() == View.GONE) return;

        String url = ApiConfig.BASE_URL + "/api/finance/statistics";
        url += "?year=" + selectedYear;
        if (selectedMonth > 0) url += "&month=" + selectedMonth;

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
                error -> Log.e("Stats", "Error: " + error.getMessage())
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