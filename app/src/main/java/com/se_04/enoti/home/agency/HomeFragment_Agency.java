package com.se_04.enoti.home.agency;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.UserManager;

import java.util.Calendar;

public class HomeFragment_Agency extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout provided for Agency
        View view = inflater.inflate(R.layout.fragment_home_agency, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);

        // Get current user info
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Cán bộ";
        String message = "Xin chào " + username + "!";
        txtWelcome.setText(message);

        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        // Set greeting based on time of day
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

        // Assuming R.string.greeting is defined as "Chào buổi %s" or similar
        String greeting = getString(R.string.greeting, timeOfDay);
        txtGreeting.setText(greeting);

        setupQuickNav(view);

        return view;
    }

    private void setupQuickNav(View view) {
        // Navigate to Resident Management
        view.findViewById(R.id.layoutManageResident).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Agency) {
                ((MainActivity_Agency) getActivity()).switchToManageResidentsTab();
            }
        });

        // Navigate to Asset Management
        view.findViewById(R.id.layoutManageAsset).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Agency) {
                ((MainActivity_Agency) getActivity()).switchToManageAssetTab();
            }
        });
    }
}