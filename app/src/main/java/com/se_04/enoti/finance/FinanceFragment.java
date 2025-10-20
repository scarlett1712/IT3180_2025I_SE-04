package com.se_04.enoti.finance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.SearchView;

import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.utils.UserManager;

import java.util.Calendar;
import java.util.List;

public class FinanceFragment extends Fragment {

    private FinanceAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_finance, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        SearchView searchView = view.findViewById(R.id.search_view);
        Spinner spinnerFilter = view.findViewById(R.id.spinner_filter);

        String username = UserManager.getInstance(requireContext()).getUsername();
        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng"
                : (hour >= 11 && hour < 14) ? "trưa"
                : (hour >= 14 && hour < 18) ? "chiều" : "tối";

        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewReceipts);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<FinanceItem> list = FinanceRepository.getInstance().getReceipts();
        adapter = new FinanceAdapter(list);
        recyclerView.setAdapter(adapter);

        // 🔍 Handle Search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();

                switch (selected) {
                    case "Tất cả":
                        adapter.getFilter().filter(""); // không lọc
                        break;
                    case "Khoản thu bắt buộc":
                        adapter.getFilter().filter("bắt buộc");
                        break;
                    case "Đóng góp":
                        adapter.getFilter().filter("đóng góp");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();
    }
}
