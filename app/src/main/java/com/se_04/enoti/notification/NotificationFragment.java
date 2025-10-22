package com.se_04.enoti.notification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.SearchView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private List<NotificationItem> originalList;
    private List<NotificationItem> filteredList;

    private Spinner spinnerFilterType, spinnerFilterTime;
    private SearchView searchView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        // Ánh xạ view
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilterType = view.findViewById(R.id.spinnerFilterType);
        spinnerFilterTime = view.findViewById(R.id.spinnerFilterTime);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        String message = "Xin chào " + username + "!";
        txtWelcome.setText(message);

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        // RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Lấy danh sách thông báo
        originalList = NotificationRepository.getInstance().getNotifications();
        filteredList = new ArrayList<>(originalList);
        adapter = new NotificationAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        // Thiết lập Spinner loại thông báo
        String[] typeOptions = {"Tất cả", "Thông báo", "Tin khẩn", "Sự kiện", "Bảo trì"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, typeOptions);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterType.setAdapter(typeAdapter);

        // Thiết lập Spinner thời gian
        String[] timeOptions = {"Mới nhất", "Cũ nhất"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterTime.setAdapter(timeAdapter);

        // Áp dụng tìm kiếm
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFiltersAndSearch();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFiltersAndSearch();
                return true;
            }
        });

        // Áp dụng lọc
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFiltersAndSearch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };

        spinnerFilterType.setOnItemSelectedListener(filterListener);
        spinnerFilterTime.setOnItemSelectedListener(filterListener);

        return view;
    }

    private void applyFiltersAndSearch() {
        String searchQuery = searchView.getQuery().toString().toLowerCase().trim();
        String selectedType = spinnerFilterType.getSelectedItem().toString();
        String selectedTime = spinnerFilterTime.getSelectedItem().toString();

        // Lọc danh sách
        filteredList.clear();
        for (NotificationItem item : originalList) {
            boolean matchesSearch = item.getTitle().toLowerCase().contains(searchQuery)
                    || item.getContent().toLowerCase().contains(searchQuery);

            boolean matchesType = selectedType.equals("Tất cả")
                    || item.getType().equalsIgnoreCase(selectedType);

            if (matchesSearch && matchesType) {
                filteredList.add(item);
            }
        }

        // Sắp xếp theo thời gian
        if (selectedTime.equals("Mới nhất")) {
            Collections.sort(filteredList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        } else {
            Collections.sort(filteredList, Comparator.comparingLong(NotificationItem::getTimestamp));
        }

        adapter.updateList(filteredList);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
