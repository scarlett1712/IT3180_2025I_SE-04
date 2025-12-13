package com.se_04.enoti.notification;

import android.content.Context;
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

import com.google.android.material.snackbar.Snackbar;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.DataCacheManager;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";
    private static final int REFRESH_INTERVAL = 10000; // Tăng lên 10s để đỡ spam server nếu đã có cache

    private NotificationAdapter adapter;
    private List<NotificationItem> originalList = new ArrayList<>();
    private List<NotificationItem> filteredList = new ArrayList<>();

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;
    private TextView txtEmpty;

    private final NotificationRepository repository = NotificationRepository.getInstance();
    private boolean isFirstLoad = true;
    private String cacheFileName; // 🔥 Tên file cache riêng cho user

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadNotificationsFromCurrentUser();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilterType = view.findViewById(R.id.spinnerFilterType);
        spinnerFilterTime = view.findViewById(R.id.spinnerFilterTime);
        txtEmpty = view.findViewById(R.id.txtEmpty);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        txtWelcome.setText("Xin chào " + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(filteredList, NotificationAdapter.VIEW_TYPE_NORMAL);
        recyclerView.setAdapter(adapter);

        setupControls();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isFirstLoad = true;
        loadNotificationsFromCurrentUser();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void setupControls() {
        if (getContext() == null) return;

        String[] typeOptions = {"Tất cả", "Hành chính", "Kỹ thuật & bảo trì", "Tài chính", "Sự kiện & cộng đồng", "Khẩn cấp"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, typeOptions);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterType.setAdapter(typeAdapter);

        String[] timeOptions = {"Mới nhất", "Cũ nhất"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterTime.setAdapter(timeAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                applyFiltersAndSearch();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerFilterType.setOnItemSelectedListener(filterListener);
        spinnerFilterTime.setOnItemSelectedListener(filterListener);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) {
                applyFiltersAndSearch();
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                applyFiltersAndSearch();
                return true;
            }
        });
    }

    private void loadNotificationsFromCurrentUser(){
        if (!isAdded() || getContext() == null) return;

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        // 🔥 1. Xác định tên file cache và Load Cache trước
        cacheFileName = "cache_notifs_" + currentUser.getId() + ".json";

        // Chỉ load cache nếu list đang trống (tránh giật màn hình khi đang refresh tự động)
        if (originalList.isEmpty()) {
            loadFromCache();
        }

        try {
            long userId = Long.parseLong(currentUser.getId());
            loadNotifications(userId);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse user ID", e);
        }
    }

    private void loadNotifications(long userId) {
        repository.fetchNotifications(userId, new NotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<NotificationItem> items) {
                if (isAdded()) {
                    // 🔥 2. Lưu vào Cache khi tải thành công
                    saveListToCache(items);

                    int oldSize = originalList.size();
                    int newSize = items.size();

                    originalList.clear();
                    originalList.addAll(items);
                    applyFiltersAndSearch();

                    if (!isFirstLoad && newSize > oldSize) {
                        Snackbar.make(requireView(), "Bạn có thông báo mới!", Snackbar.LENGTH_SHORT)
                                .setAction("Xem", v -> {
                                    RecyclerView rv = getView().findViewById(R.id.recyclerViewNotifications);
                                    if (rv != null) rv.smoothScrollToPosition(0);
                                })
                                .show();
                    }
                    isFirstLoad = false;
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load notifications: " + message);
                // Nếu lỗi mạng, UI vẫn hiển thị dữ liệu từ cache đã load trước đó
            }
        });
    }

    private void saveListToCache(List<NotificationItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (NotificationItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("notification_id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("content", item.getContent());
                obj.put("type", item.getType());
                obj.put("sender", item.getSender());
                obj.put("created_at", item.getDate());
                obj.put("expired_date", item.getExpired_date());
                obj.put("is_read", item.isRead());
                array.put(obj);
            }
            DataCacheManager.getInstance(requireContext()).saveCache(cacheFileName, array.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔥 Helper: Đọc từ Cache (Sửa lỗi 8 tham số)
    private void loadFromCache() {
        String data = DataCacheManager.getInstance(requireContext()).readCache(cacheFileName);
        if (data != null && !data.isEmpty()) {
            try {
                JSONArray array = new JSONArray(data);
                List<NotificationItem> cachedList = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    // Tạo đối tượng với ĐỦ 8 THAM SỐ theo đúng thứ tự Constructor
                    cachedList.add(new NotificationItem(
                            obj.optInt("notification_id"),      // 1. id
                            obj.optString("title"),             // 2. title
                            obj.optString("created_at"),        // 3. date (created_at)
                            obj.optString("expired_date", ""),  // 4. expired_date (Mới thêm)
                            obj.optString("type"),              // 5. type
                            obj.optString("sender"),            // 6. sender
                            obj.optString("content"),           // 7. content
                            obj.optBoolean("is_read")           // 8. isRead
                    ));
                }
                originalList.clear();
                originalList.addAll(cachedList);
                applyFiltersAndSearch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void applyFiltersAndSearch() {
        if (searchView == null || spinnerFilterType == null) return;

        String searchQuery = searchView.getQuery() == null ? "" : searchView.getQuery().toString().toLowerCase().trim();
        String selectedTypeVi = spinnerFilterType.getSelectedItem() == null ? "Tất cả" : spinnerFilterType.getSelectedItem().toString();
        String selectedType = convertTypeToEnglish(selectedTypeVi);
        String selectedTime = spinnerFilterTime.getSelectedItem() == null ? "Mới nhất" : spinnerFilterTime.getSelectedItem().toString();

        filteredList.clear();
        for (NotificationItem item : originalList) {
            String title = item.getTitle() != null ? item.getTitle().toLowerCase() : "";
            String content = item.getContent() != null ? item.getContent().toLowerCase() : "";
            String sender = item.getSender() != null ? item.getSender().toLowerCase() : "";
            String type = item.getType() != null ? item.getType() : "";

            boolean matchesSearch = title.contains(searchQuery) || content.contains(searchQuery) || sender.contains(searchQuery);
            boolean matchesType = selectedType.equals("All") || type.equalsIgnoreCase(selectedType);

            if (matchesSearch && matchesType) filteredList.add(item);
        }

        if (selectedTime.equals("Mới nhất")) {
            Collections.sort(filteredList, (a, b) -> b.getDate().compareTo(a.getDate()));
        } else {
            Collections.sort(filteredList, Comparator.comparing(NotificationItem::getDate));
        }

        if (adapter != null) adapter.updateList(filteredList);

        if (txtEmpty != null) {
            txtEmpty.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
            if (filteredList.isEmpty()) {
                txtEmpty.setText(originalList.isEmpty() ? "Chưa có thông báo nào." : "Không tìm thấy kết quả.");
            }
        }
    }

    private String convertTypeToEnglish(String typeVi) {
        switch (typeVi) {
            case "Hành chính": return "Administrative";
            case "Kỹ thuật & bảo trì": return "Maintenance";
            case "Tài chính": return "Finance";
            case "Sự kiện & cộng đồng": return "Event";
            case "Khẩn cấp": return "Emergency";
            default: return "All";
        }
    }
}