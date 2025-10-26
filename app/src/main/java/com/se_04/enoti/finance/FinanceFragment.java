package com.se_04.enoti.finance;

import android.os.Bundle;
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
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FinanceFragment extends Fragment {

    private FinanceAdapter adapter;
    private final List<FinanceItem> financeList = new ArrayList<>();
    private SearchView searchView;
    private Spinner spinnerFilter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_finance, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilter = view.findViewById(R.id.spinner_filter);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour >= 11 && hour < 14) ? "trưa"
                : (hour >= 14 && hour < 18) ? "chiều" : "tối";

        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewReceipts);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new FinanceAdapter(financeList);
        recyclerView.setAdapter(adapter);

        setupListeners();

        return view;
    }

    private void setupListeners() {
        // Handle Search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // When searching, reset the type filter to "Tất cả"
                spinnerFilter.setSelection(0, false); // Avoid triggering onItemSelected
                adapter.getFilter().filter(newText);
                return false;
            }
        });

        // Handle Spinner selection
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                // When filtering by type, clear the search view
                if (!searchView.getQuery().toString().isEmpty()) {
                    searchView.setQuery("", false);
                }
                adapter.filterByType(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadFinances() {
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập để xem thông tin tài chính", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        FinanceRepository.getInstance().fetchFinances(requireContext(), currentUser.getId(), new FinanceRepository.FinanceCallback() {
            @Override
            public void onSuccess(List<FinanceItem> finances) {
                if (isAdded()) {
                    adapter.updateList(finances);
                    // After updating the list, re-apply the current filter selection
                    if (spinnerFilter != null) {
                        String selectedType = spinnerFilter.getSelectedItem().toString();
                        adapter.filterByType(selectedType);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFinances();
    }
}
