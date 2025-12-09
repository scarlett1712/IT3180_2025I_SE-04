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
import com.se_04.enoti.utils.DataCacheManager; // 🔥 Import Cache
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FinanceFragment extends Fragment {

    private FinanceAdapter adapter;
    private final List<FinanceItem> financeList = new ArrayList<>();
    private SearchView searchView;
    private Spinner spinnerFilter;

    private boolean isAdmin;
    private int currentUserId;
    private Context context;
    private String cacheFileName; // 🔥 Tên file cache

    // 🕒 Handler để refresh dữ liệu định kỳ
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadFinances(false); // false = không load cache lại, chỉ gọi API
                refreshHandler.postDelayed(this, 5000); // 5s refresh 1 lần
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

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilter = view.findViewById(R.id.spinner_filter);

        // 👤 Lấy thông tin người dùng hiện tại
        UserItem currentUser = UserManager.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            try {
                currentUserId = Integer.parseInt(currentUser.getId());
                // 🔥 Đặt tên file cache theo ID user để bảo mật
                cacheFileName = "cache_finance_user_" + currentUserId + ".json";
            } catch (NumberFormatException e) { e.printStackTrace(); }

            isAdmin = currentUser.getRole() == Role.ADMIN;
            txtWelcome.setText(getString(R.string.welcome, currentUser.getName()));
        } else {
            txtWelcome.setText("Chào bạn");
        }

        // 🌞 Lời chào theo thời gian
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour >= 11 && hour < 14) ? "trưa"
                : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewReceipts);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        // 👇 Tạo adapter phù hợp role
        if (isAdmin) {
            adapter = new FinanceAdapter(financeList, item -> {
                // Khi admin bấm vào -> mở trang quản lý chi tiết
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
        setupListeners();

        return view;
    }

    private void setupListeners() {
        // 🔍 Tìm kiếm
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null) adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                spinnerFilter.setSelection(0, false);
                if (adapter != null) adapter.getFilter().filter(newText);
                return false;
            }
        });

        // 🔽 Lọc theo loại
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (!searchView.getQuery().toString().isEmpty()) {
                    searchView.setQuery("", false);
                }
                if (adapter != null) adapter.filterByType(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 🔥 Sửa hàm loadFinances để hỗ trợ Cache
    private void loadFinances(boolean loadCacheFirst) {
        if (currentUserId == 0) {
            Toast.makeText(context, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Load từ Cache trước (chỉ chạy khi onResume hoặc lần đầu)
        if (loadCacheFirst) {
            loadFromCache();
        }

        // 2. Gọi API lấy dữ liệu mới
        FinanceRepository.getInstance().fetchFinances(
                context,
                currentUserId,
                isAdmin,
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (!isAdded()) return;

                        // Lưu vào cache
                        saveToCache(finances);

                        // Cập nhật UI
                        if (adapter != null) {
                            adapter.updateList(finances);
                            // Giữ lại filter nếu đang chọn
                            if (spinnerFilter != null && spinnerFilter.getSelectedItem() != null) {
                                String selected = spinnerFilter.getSelectedItem().toString();
                                if (!selected.equals("Tất cả")) {
                                    adapter.filterByType(selected);
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Nếu lỗi mạng thì thôi, dữ liệu cache vẫn đang hiển thị
                        // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // 🔥 Helper: Đọc từ Cache
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
                    item.setPrice(obj.optLong("amount")); // amount/price
                    item.setType(obj.optString("type"));
                    item.setStatus(obj.optString("status"));
                    list.add(item);
                }
                if (adapter != null) adapter.updateList(list);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // 🔥 Helper: Lưu vào Cache
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
        // Lần đầu vào màn hình -> Load cache ngay + Gọi API
        loadFinances(true);
        refreshHandler.postDelayed(refreshRunnable, 5000);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}