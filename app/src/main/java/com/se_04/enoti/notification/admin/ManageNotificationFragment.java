package com.se_04.enoti.notification.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.se_04.enoti.R;
import com.se_04.enoti.notification.admin.CreateNotificationActivity;
import com.se_04.enoti.utils.UserManager;

import java.util.Calendar;

public class ManageNotificationFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_notification, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

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

        FloatingActionButton btnAdd = view.findViewById(R.id.btnAddNotification);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateNotificationActivity.class);
            startActivity(intent);
        });

        return view;
    }
}
