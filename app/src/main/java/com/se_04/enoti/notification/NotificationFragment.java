package com.se_04.enoti.notification;

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
    private static final int REFRESH_INTERVAL = 10000; // 10 seconds

    private NotificationAdapter adapter;
    private final List<NotificationItem> originalList = new ArrayList<>();
    private final List<NotificationItem> filteredList = new ArrayList<>();

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;
    private TextView txtEmpty;

    // 1. Declare the repository here, but DO NOT initialize it.
    private NotificationRepository repository;

    private boolean isFirstLoad = true;
    private String cacheFileName; // Cache filename specific to the user

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            // Ensure the fragment is still attached before refreshing
            if (isAdded()) {
                loadNotificationsFromCurrentUser();
                // Schedule the next refresh
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 2. Initialize the repository here, where the context is safely available.
        repository = NotificationRepository.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        // Initialize views
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilterType = view.findViewById(R.id.spinnerFilterType);
        spinnerFilterTime = view.findViewById(R.id.spinnerFilterTime);
        txtEmpty = view.findViewById(R.id.txtEmpty);

        // Set up welcome message
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        txtWelcome.setText("Xin chào " + username + "!");

        // Set up time-based greeting
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        // Set up RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(filteredList, NotificationAdapter.VIEW_TYPE_NORMAL);
        recyclerView.setAdapter(adapter);

        // Set up spinners and search view
        setupControls();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isFirstLoad = true;
        // Start the refresh cycle
        refreshHandler.removeCallbacks(refreshRunnable); // Remove any old callbacks
        refreshHandler.post(refreshRunnable); // Start immediately, then it will schedule itself
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop the refresh cycle to save resources
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void setupControls() {
        if (getContext() == null) return;

        // Type filter spinner
        String[] typeOptions = {"Tất cả", "Hành chính", "Kỹ thuật & bảo trì", "Tài chính", "Sự kiện & cộng đồng", "Khẩn cấp"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, typeOptions);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterType.setAdapter(typeAdapter);

        // Time sort spinner
        String[] timeOptions = {"Mới nhất", "Cũ nhất"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterTime.setAdapter(timeAdapter);

        // Listener for both spinners
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                applyFiltersAndSearch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spinnerFilterType.setOnItemSelectedListener(filterListener);
        spinnerFilterTime.setOnItemSelectedListener(filterListener);

        // Listener for search view
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFiltersAndSearch();
                return false; // Let the system handle default behavior
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFiltersAndSearch();
                return true;
            }
        });
    }

    private void loadNotificationsFromCurrentUser() {
        if (!isAdded()) return;

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            Log.w(TAG, "Current user is not available. Cannot load notifications.");
            return;
        }

        // 1. Define cache filename and load from cache first
        cacheFileName = "cache_notifs_" + currentUser.getId() + ".json";

        // Only load from cache if the list is empty to avoid UI flicker on auto-refresh
        if (originalList.isEmpty()) {
            loadFromCache();
        }

        // 2. Fetch fresh data from the network
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
                if (!isAdded()) return; // Check if fragment is still attached

                // Save the fresh data to cache
                saveListToCache(items);

                int oldSize = originalList.size();
                int newSize = items.size();

                originalList.clear();
                originalList.addAll(items);
                applyFiltersAndSearch();

                // Show a Snackbar for new notifications
                if (!isFirstLoad && newSize > oldSize && getView() != null) {
                    Snackbar.make(getView(), "Bạn có thông báo mới!", Snackbar.LENGTH_SHORT)
                            .setAction("Xem", v -> {
                                RecyclerView rv = getView().findViewById(R.id.recyclerViewNotifications);
                                if (rv != null) rv.smoothScrollToPosition(0);
                            })
                            .show();
                }
                isFirstLoad = false;
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Log.e(TAG, "Failed to load notifications: " + message);
                    // On network error, the UI will continue to show data from the cache.
                }
            }
        });
    }

    private void saveListToCache(List<NotificationItem> items) {
        if (!isAdded()) return;
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
            Log.e(TAG, "Failed to save cache", e);
        }
    }

    private void loadFromCache() {
        if (!isAdded()) return;
        String data = DataCacheManager.getInstance(requireContext()).readCache(cacheFileName);
        if (data != null && !data.isEmpty()) {
            try {
                JSONArray array = new JSONArray(data);
                List<NotificationItem> cachedList = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    // Create object with all 8 parameters in the correct constructor order
                    cachedList.add(new NotificationItem(
                            obj.optInt("notification_id"),
                            obj.optString("title"),
                            obj.optString("created_at"),
                            obj.optString("expired_date", ""),
                            obj.optString("type"),
                            obj.optString("sender"),
                            obj.optString("content"),
                            obj.optBoolean("is_read")
                    ));
                }
                originalList.clear();
                originalList.addAll(cachedList);
                applyFiltersAndSearch();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load from cache", e);
            }
        }
    }

    private void applyFiltersAndSearch() {
        if (searchView == null || spinnerFilterType == null || spinnerFilterTime == null) return;

        String searchQuery = searchView.getQuery() == null ? "" : searchView.getQuery().toString().toLowerCase().trim();
        String selectedTypeVi = spinnerFilterType.getSelectedItem().toString();
        String selectedType = convertTypeToEnglish(selectedTypeVi);
        String selectedTime = spinnerFilterTime.getSelectedItem().toString();

        filteredList.clear();
        for (NotificationItem item : originalList) {
            String title = item.getTitle() != null ? item.getTitle().toLowerCase() : "";
            String content = item.getContent() != null ? item.getContent().toLowerCase() : "";
            String sender = item.getSender() != null ? item.getSender().toLowerCase() : "";
            String type = item.getType() != null ? item.getType() : "";

            boolean matchesSearch = title.contains(searchQuery) || content.contains(searchQuery) || sender.contains(searchQuery);
            boolean matchesType = selectedType.equals("All") || type.equalsIgnoreCase(selectedType);

            if (matchesSearch && matchesType) {
                filteredList.add(item);
            }
        }

        // Sort based on time selection
        if (selectedTime.equals("Mới nhất")) {
            Collections.sort(filteredList, (a, b) -> b.getDate().compareTo(a.getDate()));
        } else {
            Collections.sort(filteredList, Comparator.comparing(NotificationItem::getDate));
        }

        // Update adapter and empty text view
        if (adapter != null) {
            adapter.updateList(filteredList);
        }

        if (txtEmpty != null) {
            txtEmpty.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
            if (filteredList.isEmpty()) {
                txtEmpty.setText(originalList.isEmpty() ? "Chưa có thông báo nào." : "Không tìm thấy kết quả.");
            }
        }
    }

    private String convertTypeToEnglish(String typeVi) {
        switch (typeVi) {
            case "Hành chính":
                return "Administrative";
            case "Kỹ thuật & bảo trì":
                return "Maintenance";
            case "Tài chính":
                return "Finance";
            case "Sự kiện & cộng đồng":
                return "Event";
            case "Khẩn cấp":
                return "Emergency";
            default:
                return "All";
        }
    }
}
