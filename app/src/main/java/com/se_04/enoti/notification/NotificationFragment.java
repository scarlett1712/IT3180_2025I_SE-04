package com.se_04.enoti.notification;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import com.se_04.enoti.api.ApiService;
import com.se_04.enoti.api.RetrofitClient;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationFragment extends Fragment {

    private NotificationAdapter adapter;
    private Spinner spinnerSort;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);
        SearchView searchView = view.findViewById(R.id.search_view);

        String username = UserManager.getInstance(requireContext()).getUsername();
        String message = getString(R.string.welcome, username);
        txtWelcome.setText(message);

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String timeOfDay;
        if (hour >= 5 && hour < 11) {
            timeOfDay = "sáng";
        } else if (hour >= 11 && hour < 14) {
            timeOfDay = "trưa";
        } else if (hour >= 14 && hour < 18) {
            timeOfDay = "chiều";
        } else {
            timeOfDay = "tối";
        }

        String greeting = getString(R.string.greeting, timeOfDay);
        txtGreeting.setText(greeting);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // --- SEARCH ---
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });

        // --- SPINNER ---
        spinnerSort = view.findViewById(R.id.spinner_sort);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.notification_sort_options,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(spinnerAdapter);

        SharedPreferences prefs = requireContext().getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
        String savedOption = prefs.getString("selected_sort_option", "Tất cả");

        int spinnerPosition = spinnerAdapter.getPosition(savedOption);
        if (spinnerPosition >= 0) spinnerSort.setSelection(spinnerPosition);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String option = parent.getItemAtPosition(position).toString();

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("selected_sort_option", option);
                editor.apply();

                adapter.sortNotifications(option);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        return view;
    }

    private void fetchNotifications() {
        if (!isAdded()) return; // Fragment not attached

        UserManager userManager = UserManager.getInstance(requireContext());
        if (userManager.getCurrentUser() == null) {
            Log.e("NotificationFragment", "Cannot fetch notifications: User is not logged in.");
            return; // Exit if no user is logged in
        }
        String userId = userManager.getCurrentUser().getId();

        ApiService apiService = RetrofitClient.getApiService();
        Call<List<NotificationItem>> call = apiService.getNotificationsByUser(userId);

        call.enqueue(new Callback<List<NotificationItem>>() {
            @Override
            public void onResponse(@NonNull Call<List<NotificationItem>> call, @NonNull Response<List<NotificationItem>> response) {
                if (!isAdded()) return; // Fragment detached during the request

                if (response.isSuccessful() && response.body() != null) {
                    adapter.updateData(response.body());

                    // Restore spinner selection and sort after data is loaded
                    SharedPreferences prefs = requireContext().getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
                    String savedOption = prefs.getString("selected_sort_option", "Tất cả");
                    int spinnerPosition = ((ArrayAdapter<String>) spinnerSort.getAdapter()).getPosition(savedOption);
                    if (spinnerPosition >= 0) {
                        spinnerSort.setSelection(spinnerPosition);
                    }
                    adapter.sortNotifications(savedOption);

                } else {
                    Log.e("NotificationFragment", "Response not successful. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<NotificationItem>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Log.e("NotificationFragment", "Error fetching notifications", t);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchNotifications();
    }
}
