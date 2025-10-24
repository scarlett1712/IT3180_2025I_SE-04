package com.se_04.enoti.notification;

import android.os.Bundle;
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

    private NotificationAdapter adapter;
    private List<NotificationItem> originalList = new ArrayList<>();
    private List<NotificationItem> filteredList = new ArrayList<>();

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;

    private final NotificationRepository repository = NotificationRepository.getInstance();

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
        adapter = new NotificationAdapter(filteredList, NotificationAdapter.VIEW_TYPE_HIGHLIGHTED);
        recyclerView.setAdapter(adapter);

        // Spinners
        String[] typeOptions = {"Tất cả", "Thông báo", "Tin khẩn", "Sự kiện", "Bảo trì"};
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

        // Fetch from backend
        long userIdForApi = 0;
        if (currentUser != null) {
            String idStr = currentUser.getId();
            try {
                userIdForApi = Long.parseLong(idStr);
            } catch (Exception ignored) {
                // if id is not numeric (e.g., phone), you should adapt your backend route to accept phone string
                // For now use 0 -> backend should handle or change code to pass phone.
                userIdForApi = 0;
            }
        }

        loadNotifications(userIdForApi);

        return view;
    }

    private void loadNotifications(long userId) {
        repository.fetchNotifications(userId, new NotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<NotificationItem> items) {
                originalList.clear();
                originalList.addAll(items);
                applyFiltersAndSearch();
            }

            @Override
            public void onError(String message) {
                // fallback: keep empty list or show snackbar/toast
                originalList.clear();
                applyFiltersAndSearch();
            }
        });
    }

    private void applyFiltersAndSearch() {
        String searchQuery = searchView.getQuery() == null ? "" : searchView.getQuery().toString().toLowerCase().trim();
        String selectedType = spinnerFilterType.getSelectedItem() == null ? "Tất cả" : spinnerFilterType.getSelectedItem().toString();
        String selectedTime = spinnerFilterTime.getSelectedItem() == null ? "Mới nhất" : spinnerFilterTime.getSelectedItem().toString();

        filteredList.clear();
        for (NotificationItem item : originalList) {
            boolean matchesSearch = item.getTitle().toLowerCase().contains(searchQuery) ||
                    item.getContent().toLowerCase().contains(searchQuery) ||
                    item.getSender().toLowerCase().contains(searchQuery);

            boolean matchesType = selectedType.equals("Tất cả") || item.getType().equalsIgnoreCase(selectedType);

            if (matchesSearch && matchesType) filteredList.add(item);
        }

        if (selectedTime.equals("Mới nhất")) {
            Collections.sort(filteredList, (a, b) -> {
                // fallback: compare strings if timestamps not parsed
                return b.getDate().compareTo(a.getDate());
            });
        } else {
            Collections.sort(filteredList, Comparator.comparing(NotificationItem::getDate));
        }

        adapter.updateList(filteredList);
    }
}
