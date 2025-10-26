package com.se_04.enoti.finance.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.finance.FinanceAdapter;
import com.se_04.enoti.finance.FinanceItem;
import com.se_04.enoti.finance.FinanceRepository;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ManageFinanceFragment extends Fragment {

    private FinanceAdapter adapter;
    private final List<FinanceItem> financeList = new ArrayList<>();
    private SearchView searchView;
    private Spinner spinnerFilter;
    private FloatingActionButton btnAdd;
    private final Handler refreshHandler = new Handler();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadFinanceByRoom(); // ✅ Tải dữ liệu theo phòng
                refreshHandler.postDelayed(this, 3000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_finance, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        searchView = view.findViewById(R.id.search_view);
        spinnerFilter = view.findViewById(R.id.spinner_filter);
        btnAdd = view.findViewById(R.id.btnAddReceipt);

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateFinanceActivity.class);
            startActivity(intent);
        });

        // 👋 Chào admin
        txtWelcome.setText("Xin chào, Quản trị viên");
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour >= 11 && hour < 14) ? "trưa"
                : (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewManageFinance);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new FinanceAdapter(financeList);
        recyclerView.setAdapter(adapter);

        setupListeners();
        return view;
    }

    private void setupListeners() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                spinnerFilter.setSelection(0, false);
                adapter.getFilter().filter(newText);
                return false;
            }
        });

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (!searchView.getQuery().toString().isEmpty()) {
                    searchView.setQuery("", false);
                }
                adapter.filterByType(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 📥 Tải danh sách tài chính theo phòng cho admin
    private void loadFinanceByRoom() {
        String adminId = UserManager.getInstance(requireContext()).getID();

        FinanceRepository.getInstance().fetchAdminFinances(requireContext(), Integer.parseInt(adminId),
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        if (isAdded()) {
                            adapter.updateList(finances);
                            String selectedType = spinnerFilter.getSelectedItem().toString();
                            adapter.filterByType(selectedType);
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
        loadFinanceByRoom();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}
