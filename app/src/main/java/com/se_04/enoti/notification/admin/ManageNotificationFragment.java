package com.se_04.enoti.notification.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.notification.NotificationAdapter;
import com.se_04.enoti.notification.NotificationItem;
import com.se_04.enoti.notification.NotificationRepository;
import com.se_04.enoti.utils.DataCacheManager;
import com.se_04.enoti.utils.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ManageNotificationFragment extends Fragment {

    private static final String TAG = "ManageNotificationFrag";
    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private NotificationRepository repository;

    private List<NotificationItem> originalList = new ArrayList<>();
    private List<NotificationItem> filteredList = new ArrayList<>();

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;

    // Receiver to listen for new notifications created by the admin
    private final BroadcastReceiver notificationCreatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "'Notification Created' broadcast received. Refreshing list...");
            loadSentNotifications();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_notification, container, false);

        repository = NotificationRepository.getInstance(requireContext());
        setupWelcomeViews(view);
        setupRecyclerView(view);
        setupFilters(view);

        FloatingActionButton btnAdd = view.findViewById(R.id.btnAddNotification);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateNotificationActivity.class);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Register the receiver
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                notificationCreatedReceiver,
                new IntentFilter(CreateNotificationActivity.ACTION_NOTIFICATION_CREATED)
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unregister the receiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(notificationCreatedReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSentNotifications(); // Also load when returning to the fragment
    }

    private void setupWelcomeViews(View view) {
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

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewSentNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter(filteredList, NotificationAdapter.VIEW_TYPE_NORMAL);
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters(View view) {
        spinnerFilterType = view.findViewById(R.id.spinnerFilterType);
        spinnerFilterTime = view.findViewById(R.id.spinnerFilterTime);
        searchView = view.findViewById(R.id.search_view);

        // Filter type
        String[] typeOptions = {"Tất cả", "Hành chính", "Kỹ thuật & bảo trì", "Tài chính", "Sự kiện & cộng đồng", "Khẩn cấp"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, typeOptions);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterType.setAdapter(typeAdapter);

        // Filter time
        String[] timeOptions = {"Mới nhất", "Cũ nhất"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterTime.setAdapter(timeAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { applyFiltersAndSearch(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerFilterType.setOnItemSelectedListener(filterListener);
        spinnerFilterTime.setOnItemSelectedListener(filterListener);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { applyFiltersAndSearch(); return true; }
            @Override public boolean onQueryTextChange(String newText) { applyFiltersAndSearch(); return true; }
        });
    }

    private void loadSentNotifications() {
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) return;

        // 1. Load Cache
        loadCache();

        // 2. Load API
        repository.fetchAdminNotifications(new NotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<NotificationItem> items) {
                if (isAdded() && items != null) {
                    // 🔥 Lưu cache
                    saveCache(items);

                    // Update UI
                    updateListUI(items);
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Log.e(TAG, "Failed to fetch admin notifications: " + message);
                    Toast.makeText(requireContext(), "Không thể tải danh sách thông báo", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateListUI(List<NotificationItem> items) {
        originalList.clear();
        originalList.addAll(items);
        applyFiltersAndSearch();
    }

    // 🔥 Lưu cache
    private void saveCache(List<NotificationItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (NotificationItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("notification_id", item.getId());
                obj.put("title", item.getTitle());
                obj.put("content", item.getContent());
                obj.put("type", item.getType());
                obj.put("created_at", item.getDate());
                obj.put("expired_date", item.getExpired_date());

                // 🔥 LƯU THÊM FILE URL & TYPE
                obj.put("file_url", item.getFileUrl());
                obj.put("file_type", item.getFileType());

                array.put(obj);
            }
            DataCacheManager.getInstance(requireContext()).saveCache(DataCacheManager.CACHE_ADMIN_NOTIFS, array.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🔥 Đọc cache
    private void loadCache() {
        String data = DataCacheManager.getInstance(requireContext()).readCache(DataCacheManager.CACHE_ADMIN_NOTIFS);
        if (data != null && !data.isEmpty()) {
            try {
                JSONArray array = new JSONArray(data);
                List<NotificationItem> list = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new NotificationItem(
                            obj.optInt("notification_id"),
                            obj.optString("title"),
                            obj.optString("created_at"),
                            obj.optString("expired_date"),
                            obj.optString("type"),
                            "Hệ thống", // Sender mặc định admin
                            obj.optString("content"),
                            true,
                            obj.optString("file_url"), // 🔥 LẤY TỪ CACHE
                            obj.optString("file_type") // 🔥 LẤY TỪ CACHE
                    ));
                }
                updateListUI(list);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void applyFiltersAndSearch() {
        String searchQuery = searchView.getQuery() == null ? "" : searchView.getQuery().toString().toLowerCase().trim();
        String selectedTypeVi = spinnerFilterType.getSelectedItem() == null
                ? "Tất cả"
                : spinnerFilterType.getSelectedItem().toString();
        String selectedType = convertTypeToEnglish(selectedTypeVi);
        String selectedTime = spinnerFilterTime.getSelectedItem() == null
                ? "Mới nhất"
                : spinnerFilterTime.getSelectedItem().toString();

        filteredList.clear();
        for (NotificationItem item : originalList) {
            boolean matchesSearch = item.getTitle().toLowerCase().contains(searchQuery)
                    || item.getContent().toLowerCase().contains(searchQuery)
                    || item.getSender().toLowerCase().contains(searchQuery);

            boolean matchesType = selectedType.equals("All")
                    || item.getType().equalsIgnoreCase(selectedType);

            if (matchesSearch && matchesType) filteredList.add(item);
        }

        if (selectedTime.equals("Mới nhất")) {
            Collections.sort(filteredList, (a, b) -> b.getDate().compareTo(a.getDate()));
        } else {
            Collections.sort(filteredList, Comparator.comparing(NotificationItem::getDate));
        }

        adapter.updateList(filteredList);
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