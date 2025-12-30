package com.se_04.enoti.finance;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.FinanceDetailActivity_Admin;
import com.se_04.enoti.utils.DataCacheManager;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FinanceFragment extends Fragment {

    private FinanceAdapter adapter;
    // 🔥 masterList: Lưu toàn bộ dữ liệu gốc từ API
    private final List<FinanceItem> masterList = new ArrayList<>();
    // financeList: Dữ liệu đang hiển thị (đã lọc)
    private final List<FinanceItem> financeList = new ArrayList<>();

    private SearchView searchView;
    private Spinner spinnerFilter; // Lọc loại
    private Spinner spinnerStatus; // 🔥 Lọc trạng thái

    private boolean isAdmin;
    private int currentUserId;
    private Context context;
    private String cacheFileName;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadFinances(false);
                refreshHandler.postDelayed(this, 5000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_finance, container, false);
        context = requireContext();

        initViews(view);
        setupUserAndGreeting(view);
        setupRecyclerView(view);
        setupSpinners(); // 🔥 Setup dữ liệu cho Spinner mới
        setupListeners(); // 🔥 Logic lọc tổng hợp

        return view;
    }

    private void initViews(View view) {
        searchView = view.findViewById(R.id.search_view);
        spinnerFilter = view.findViewById(R.id.spinner_filter);
        spinnerStatus = view.findViewById(R.id.spinner_status); // 🔥
    }

    private void setupUserAndGreeting(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            try {
                currentUserId = Integer.parseInt(currentUser.getId());
                cacheFileName = "cache_finance_user_" + currentUserId + ".json";
            } catch (NumberFormatException e) { e.printStackTrace(); }

            isAdmin = currentUser.getRole() == Role.ADMIN;
            txtWelcome.setText(getString(R.string.welcome, currentUser.getName()));
        } else {
            txtWelcome.setText("Chào bạn");
        }

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour >= 11 && hour < 14) ? "trưa"
                : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }

    private void setupRecyclerView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewReceipts);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        if (isAdmin) {
            adapter = new FinanceAdapter(financeList, item -> {
                Intent intent = new Intent(context, FinanceDetailActivity_Admin.class);
                intent.putExtra("finance_id", item.getId());
                intent.putExtra("title", item.getTitle());
                intent.putExtra("due_date", item.getDate());
                startActivity(intent);
            });
        } else {
            adapter = new FinanceAdapter(financeList);
        }
        recyclerView.setAdapter(adapter);
    }

    // 🔥 Cấu hình dữ liệu cho Spinner Trạng thái
    private void setupSpinners() {
        // 🔥 Đảm bảo thứ tự này khớp với logic (0, 1, 2)
        String[] statusOptions = {"Tất cả trạng thái", "Chưa thanh toán", "Đã thanh toán"};

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        // Mặc định chọn cái đầu tiên
        spinnerStatus.setSelection(0);
    }

    // 🔥 HÀM LỌC TỔNG HỢP (QUAN TRỌNG NHẤT)
    private void applyFilters() {
        String query = searchView.getQuery().toString().toLowerCase();

        // Lấy vị trí (Index) thay vì lấy chuỗi text để tránh sai chính tả
        int typeIndex = spinnerFilter.getSelectedItemPosition(); // 0: Tất cả, 1: Bắt buộc, 2: Tự nguyện
        int statusIndex = spinnerStatus.getSelectedItemPosition(); // 0: Tất cả, 1: Chưa TT, 2: Đã TT

        Log.d("FILTER_DEBUG", "Filter -> Type Index: " + typeIndex + " | Status Index: " + statusIndex + " | MasterList Size: " + masterList.size());

        List<FinanceItem> filteredList = new ArrayList<>();

        for (FinanceItem item : masterList) {
            String itemType = item.getType();
            if (itemType == null) itemType = "";
            String itemStatus = item.getStatus();
            if (itemStatus == null) itemStatus = "";

            // 1. Check Tìm kiếm (Search)
            boolean matchSearch = item.getTitle().toLowerCase().contains(query);

            // 2. Check Loại (Type) dựa trên Index
            boolean matchType = false;

            if (typeIndex == 0) {
                // Index 0 = Tất cả loại phí
                matchType = true;
            }
            else if (typeIndex == 1) {
                // Index 1 = Bắt buộc (Tất cả cái gì KHÔNG PHẢI tự nguyện)
                matchType = !itemType.equals("Tự nguyện") && !itemType.equals("donation");
            }
            else if (typeIndex == 2) {
                // Index 2 = Tự nguyện
                matchType = itemType.equals("Tự nguyện") || itemType.equals("donation");
            }

            // 3. Check Trạng thái (Status) dựa trên Index
            boolean matchStatus = false; // Mặc định false để check kỹ

            if (statusIndex == 0) {
                // Index 0 = Tất cả trạng thái (SpinnerStatus chưa khởi tạo hoặc chọn cái đầu)
                matchStatus = true;
            }
            else if (statusIndex == 1) {
                // Index 1 = Chưa thanh toán
                matchStatus = "chua_thanh_toan".equalsIgnoreCase(itemStatus);
            }
            else if (statusIndex == 2) {
                // Index 2 = Đã thanh toán
                matchStatus = "da_thanh_toan".equalsIgnoreCase(itemStatus);
            }

            // Debug từng item nếu cần thiết
            // Log.d("FILTER_ITEM", "Title: " + item.getTitle() + " | Match: " + (matchSearch && matchType && matchStatus));

            // Thêm vào list nếu thỏa mãn cả 3 điều kiện
            if (matchSearch && matchType && matchStatus) {
                filteredList.add(item);
            }
        }

        // Cập nhật Adapter
        if (adapter != null) {
            adapter.updateList(filteredList);

            // Hiển thị thông báo nếu không có kết quả
            if (filteredList.isEmpty() && !masterList.isEmpty()) {
                // Có thể show 1 textview "Không tìm thấy kết quả" ở đây nếu muốn
                Log.d("FILTER_DEBUG", "Kết quả lọc rỗng.");
            }
        }
    }

    private void setupListeners() {
        // Sự kiện Search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilters();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilters();
                return false;
            }
        });

        // Sự kiện Spinner Loại
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 🔥 Sự kiện Spinner Trạng thái
        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadFinances(boolean loadCacheFirst) {
        if (currentUserId == 0) return;

        if (loadCacheFirst) loadFromCache();

        FinanceRepository.getInstance().fetchFinances(
                context,
                currentUserId,
                isAdmin,
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;

                        saveToCache(finances);

                        // 🔥 Cập nhật danh sách gốc
                        masterList.clear();
                        masterList.addAll(finances);

                        // 🔥 Áp dụng bộ lọc hiện tại ngay lập tức
                        applyFilters();
                    }

                    @Override
                    public void onError(String message) {}
                }
        );
    }

    private void loadFromCache() {
        String data = DataCacheManager.getInstance(context).readCache(cacheFileName);
        if (data != null && !data.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                List<FinanceItem> list = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    FinanceItem item = new FinanceItem();
                    item.setId(obj.optInt("id"));
                    item.setTitle(obj.optString("title"));
                    item.setDate(obj.optString("date"));
                    item.setPrice(obj.optLong("amount"));
                    item.setType(obj.optString("type"));
                    item.setStatus(obj.optString("status"));
                    list.add(item);
                }

                // Cập nhật masterList và áp dụng lọc
                masterList.clear();
                masterList.addAll(list);
                applyFilters();

            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void saveToCache(List<FinanceItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (FinanceItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("date", item.getDate());
                obj.put("amount", item.getPrice());
                obj.put("type", item.getType());
                obj.put("status", item.getStatus());
                array.put(obj);
            }
            DataCacheManager.getInstance(context).saveCache(cacheFileName, array.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFinances(true);
        refreshHandler.postDelayed(refreshRunnable, 5000);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}