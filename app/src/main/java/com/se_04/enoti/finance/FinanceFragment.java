package com.se_04.enoti.finance;

import android.content.Context;
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

import com.se_04.enoti.R;
import com.se_04.enoti.account.Role;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.FinanceDetailActivity_Admin;
import com.se_04.enoti.utils.UserManager;

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

    // 🕒 Handler để refresh dữ liệu định kỳ
    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadFinances();
                refreshHandler.postDelayed(this, 3000); // Cập nhật lại sau 3 giây
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
            currentUserId = Integer.parseInt(currentUser.getId());
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
                android.content.Intent intent = new android.content.Intent(context, FinanceDetailActivity_Admin.class);
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

        // 🔽 Lọc theo loại
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

    private void loadFinances() {
        if (currentUserId == 0) {
            Toast.makeText(context, "Vui lòng đăng nhập để xem thông tin tài chính", Toast.LENGTH_SHORT).show();
            return;
        }

        FinanceRepository.getInstance().fetchFinances(
                context,
                currentUserId,
                isAdmin,
                new FinanceRepository.FinanceCallback() {
                    @Override
                    public void onSuccess(List<FinanceItem> finances) {
                        adapter.updateList(finances);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFinances(); // tải lần đầu
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}
