package com.se_04.enoti.home.admin; // Your package name

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.notification.NotificationAdapter;
import com.se_04.enoti.notification.NotificationItem;
import com.se_04.enoti.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeFragment_Admin extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {// Inflate your layout for this fragment

        View view = inflater.inflate(R.layout.fragment_home_admin, container, false); // Make sure fragment_home.xml exists

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        String admin = "admin"; // ví dụ
        String message = getString(R.string.welcome, admin);
        txtWelcome.setText(message);

        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

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

        return view;
    }
}
