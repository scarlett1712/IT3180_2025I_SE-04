package com.se_04.enoti.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";
    private NotificationAdapter adapter;
    private List<NotificationItem> originalList = new ArrayList<>();
    private List<NotificationItem> filteredList = new ArrayList<>();

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;

    private final NotificationRepository repository = NotificationRepository.getInstance();

    // 🕒 Handler để refresh dữ liệu định kỳ
    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                Log.d(TAG, "⏱ Refreshing notifications every 3s...");
                loadNotificationsFromCurrentUser();
                refreshHandler.postDelayed(this, 3000); // gọi lại sau 3s
            }
        }
    };

    // BroadcastReceiver để cập nhật khi có thông báo mới từ FCM
    private final BroadcastReceiver newNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "📩 New notification broadcast received. Reloading list...");
            loadNotificationsFromCurrentUser();
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

        setupControls(currentUser);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                newNotificationReceiver,
                new IntentFilter(MyFirebaseMessagingService.ACTION_NEW_NOTIFICATION)
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(newNotificationReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "🔁 Fragment resumed. Starting auto-refresh and reloading notifications...");
        loadNotificationsFromCurrentUser();
        refreshHandler.postDelayed(refreshRunnable, 3000); // bắt đầu chạy định kỳ 3s
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "⏸ Fragment paused. Stopping auto-refresh...");
        refreshHandler.removeCallbacks(refreshRunnable); // ngừng khi rời fragment
    }

    private void setupControls(UserItem currentUser) {
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

    private void loadNotificationsFromCurrentUser(){
        if (getContext() == null) return;
        UserItem currentUser = UserManager.getInstance(getContext()).getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            Log.e(TAG, "⚠️ Cannot load notifications, user or user ID is null.");
            return;
        }

        try {
            long userId = Long.parseLong(currentUser.getId());
            loadNotifications(userId);
        } catch (NumberFormatException e) {
            Log.e(TAG, "❌ Failed to parse user ID for reloading: " + currentUser.getId(), e);
        }
    }

    private void loadNotifications(long userId) {
        repository.fetchNotifications(userId, new NotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<NotificationItem> items) {
                if (isAdded()) {
                    originalList.clear();
                    originalList.addAll(items);
                    applyFiltersAndSearch();
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Log.e(TAG, "❌ Failed to load notifications: " + message);
                    originalList.clear();
                    applyFiltersAndSearch();
                }
            }
        });
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
