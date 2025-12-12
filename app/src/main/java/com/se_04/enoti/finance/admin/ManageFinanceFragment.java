package com.se_04.enoti.finance.admin;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class ManageFinanceFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView txtRevenue, txtExpense;
    private BarChart barChart;
    private SearchView searchView;
    private Spinner spinnerFilterType, spinnerMonth, spinnerYear;
    private ExtendedFloatingActionButton btnUtility, btnAdd;
    private RecyclerView recyclerView;
    private ImageView btnExportExcel;

    private View cardStatsContainer, lblFinanceStatsHeader, layoutFabContainer;
    private TextView textList;

    private FinanceAdapter adapter;
    private final List<FinanceItem> allFinances = new ArrayList<>();

    private int selectedMonth = 0;
    private int selectedYear = Calendar.getInstance().get(Calendar.YEAR);

    private final ActivityResultLauncher<Intent> addFinanceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadAllData();
                    Toast.makeText(getContext(), "Cập nhật thành công", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_finance, container, false);
        initViews(view);
        setupWelcome(view);
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
        btnExportExcel = view.findViewById(R.id.btnExportExcel);
        btnAdd = view.findViewById(R.id.btnAddReceipt);
        btnUtility = view.findViewById(R.id.btnUtility);
        cardStatsContainer = view.findViewById(R.id.card_stats_container);
        lblFinanceStatsHeader = view.findViewById(R.id.lbl_finance_stats_header);
        layoutFabContainer = view.findViewById(R.id.layout_fab_container);
        textList = view.findViewById(R.id.textList);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.purple_primary, android.R.color.holo_green_light);
            swipeRefreshLayout.setOnRefreshListener(this::loadAllData);
        }
    }

    private void setupRoleBasedUI() {
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser != null) {
            if (currentUser.getRole() == Role.ACCOUNTANT) {
                if (lblFinanceStatsHeader != null) lblFinanceStatsHeader.setVisibility(View.GONE);
                if (cardStatsContainer != null) cardStatsContainer.setVisibility(View.GONE);
                if (textList != null) textList.setTextColor(Color.BLACK);
                if (btnExportExcel != null) btnExportExcel.setVisibility(View.VISIBLE);
            } else if (currentUser.getRole() == Role.ADMIN) {
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
        String prefix = (currentUser != null && currentUser.getRole() == Role.ACCOUNTANT) ? "Kế toán " : "";
        txtWelcome.setText("Xin chào " + prefix + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" : (hour >= 11 && hour < 14) ? "trưa" : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }

    private void setupChart() {
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
        if (cardStatsContainer != null && cardStatsContainer.getVisibility() == View.GONE) return;
        List<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear - 2; i <= currentYear + 2; i++) years.add(String.valueOf(i));

        ArrayAdapter<String> adapterYear = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, years);
        adapterYear.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapterYear);
        spinnerYear.setSelection(2);
        selectedYear = currentYear;

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedMonth = pos;
                loadFinancialStats();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedYear = Integer.parseInt(p.getItemAtPosition(pos).toString());
                loadFinancialStats();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupOtherListeners() {
        if (btnAdd != null) btnAdd.setOnClickListener(v -> addFinanceLauncher.launch(new Intent(getActivity(), CreateFinanceActivity.class)));
        if (btnUtility != null) btnUtility.setOnClickListener(v -> addFinanceLauncher.launch(new Intent(getActivity(), BulkUtilityBillActivity.class)));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { adapter.getFilter().filter(q); return false; }
            @Override public boolean onQueryTextChange(String n) { adapter.getFilter().filter(n); return false; }
        });
        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { adapter.filterByType(p.getItemAtPosition(pos).toString()); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        if (btnExportExcel != null) {
            btnExportExcel.setOnClickListener(v -> exportFinanceToExcel(allFinances));
        }
    }

    // =========================================================================
    // 🔥 HÀM TẠO EXCEL OFFLINE (LOGIC MỚI: DÙNG SỐ TIỀN THỰC TẾ)
    // =========================================================================
    private void exportFinanceToExcel(List<FinanceItem> financeList) {
        if (financeList == null || financeList.isEmpty()) {
            Toast.makeText(getContext(), "Không có dữ liệu!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Workbook workbook = new HSSFWorkbook();
            Sheet sheet = workbook.createSheet("Báo cáo Tài chính");

            // 1. Header
            String[] headers = {"Mã", "Tiêu đề", "Loại", "Định mức/Gốc", "Hạn nộp", "Tiến độ", "Thực thu/Chi"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 2. Data
            int rowNum = 1;
            double grandTotalRevenue = 0;
            double grandTotalExpense = 0;

            for (FinanceItem item : financeList) {
                Row row = sheet.createRow(rowNum++);

                String type = item.getType() != null ? item.getType().toLowerCase() : "";
                boolean isExpense = type.contains("chi_phi");

                double baseAmount = 0;
                try {
                    if (item.getPrice() != null) baseAmount = ((Number) item.getPrice()).doubleValue();
                } catch (Exception e) {}

                int paid = item.getPaidRooms();
                int total = item.getTotalRooms();

                // 🔥 LOGIC CHÍNH: LẤY SỐ TIỀN THỰC TẾ TỪ SERVER
                // Bạn cần thêm hàm getRealRevenue() vào class FinanceItem
                double realMoney = item.getRealRevenue();

                // Fallback: Nếu server chưa trả về realRevenue (chưa update app), dùng logic cũ
                if (realMoney == 0 && total > 0 && paid > 0) {
                    realMoney = baseAmount * paid;
                }

                if (isExpense) {
                    // Chi phí thì dùng số tiền đã nhập (baseAmount)
                    realMoney = baseAmount;
                    grandTotalExpense += realMoney;
                } else {
                    grandTotalRevenue += realMoney;
                }

                row.createCell(0).setCellValue(item.getId());
                row.createCell(1).setCellValue(item.getTitle());
                row.createCell(2).setCellValue(isExpense ? "Chi" : "Thu");

                // Cột "Định mức"
                if (baseAmount == 0) row.createCell(3).setCellValue("Tự nguyện");
                else row.createCell(3).setCellValue(baseAmount);

                row.createCell(4).setCellValue(item.getDate());

                // Cột "Tiến độ"
                if (isExpense) row.createCell(5).setCellValue("-");
                else if (total > 0) row.createCell(5).setCellValue(paid + "/" + total);
                else row.createCell(5).setCellValue("Khác");

                // Cột "Thực thu/Chi"
                row.createCell(6).setCellValue(realMoney);
            }

            // 3. Footer
            Row totalRow = sheet.createRow(rowNum + 1);
            totalRow.createCell(1).setCellValue("TỔNG KẾT:");

            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            totalRow.createCell(2).setCellValue("Thu: " + nf.format(grandTotalRevenue));
            totalRow.createCell(3).setCellValue("Chi: " + nf.format(grandTotalExpense));
            totalRow.createCell(6).setCellValue("Dư: " + nf.format(grandTotalRevenue - grandTotalExpense));

            // 4. Lưu file
            String fileName = "TaiChinh_" + new SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault()).format(new Date()) + ".xls";
            saveExcelFile(fileName, workbook);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi xuất Excel: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveExcelFile(String fileName, Workbook workbook) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Enoti_Finance");

            Uri uri = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                uri = requireContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }

            OutputStream outputStream = null;
            if (uri != null) {
                outputStream = requireContext().getContentResolver().openOutputStream(uri);
            } else {
                java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Enoti_Finance");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            if (outputStream != null) {
                workbook.write(outputStream);
                outputStream.close();
                workbook.close();
                Toast.makeText(requireContext(), "Đã lưu: " + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi lưu file!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAllData() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);
        loadFinanceList();
        if (cardStatsContainer != null && cardStatsContainer.getVisibility() == View.VISIBLE) loadFinancialStats();
    }

    private void loadFinanceList() {
        loadFinanceFromCache();
        FinanceRepository.getInstance().fetchAdminFinances(requireContext(),
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;
                        saveFinanceToCache(finances);
                        updateListUI(finances);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                    @Override public void onError(String message) { if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false); }
                });
    }

    // 🔥 PARSE JSON: Đọc thêm total_collected_real
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

                    if (obj.isNull("amount")) item.setPrice(0L);
                    else item.setPrice(obj.optLong("amount"));

                    item.setType(obj.optString("type"));
                    item.setDate(obj.optString("date"));
                    item.setStatus(obj.optString("status"));
                    item.setPaidRooms(obj.optInt("paid_rooms"));
                    item.setTotalRooms(obj.optInt("total_rooms"));

                    // 🔥 ĐỌC SỐ TIỀN THỰC TẾ
                    item.setRealRevenue(obj.optDouble("total_collected_real", 0));

                    cachedList.add(item);
                }
                updateListUI(cachedList);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // 🔥 SAVE JSON: Lưu thêm total_collected_real
    private void saveFinanceToCache(List<FinanceItem> list) {
        if (getContext() == null) return;
        try {
            JSONArray array = new JSONArray();
            for (FinanceItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("amount", item.getPrice() == null ? JSONObject.NULL : item.getPrice());
                obj.put("type", item.getType());
                obj.put("date", item.getDate());
                obj.put("status", item.getStatus());
                obj.put("paid_rooms", item.getPaidRooms());
                obj.put("total_rooms", item.getTotalRooms());

                // 🔥 LƯU SỐ TIỀN THỰC TẾ
                obj.put("total_collected_real", item.getRealRevenue());

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
            if (spinnerFilterType != null && spinnerFilterType.getSelectedItem() != null) adapter.filterByType(spinnerFilterType.getSelectedItem().toString());
        }
    }

    private void loadFinancialStats() {
        if (cardStatsContainer == null || cardStatsContainer.getVisibility() == View.GONE) return;
        String url = ApiConfig.BASE_URL + "/api/finance/statistics?year=" + selectedYear;
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
                }, error -> Log.e("Stats", "Error: " + error.getMessage()));
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
        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        barChart.setData(data);
        barChart.animateY(800);
        barChart.invalidate();
    }
}