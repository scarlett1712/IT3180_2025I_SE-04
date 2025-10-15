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

        // Tìm RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Tạo danh sách mẫu
        List<NotificationItem> list = new ArrayList<>();
        list.add(new NotificationItem("Cúp điện toàn khu", "01/10/2025 - 02/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Bảo trì thang máy", "03/10/2025 - 04/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Họp cư dân", "05/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Thông báo phòng cháy chữa cháy", "07/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Đóng phí dịch vụ tháng 10", "10/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Họp ban quản lý", "15/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Cắt nước bảo trì", "20/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));
        list.add(new NotificationItem("Sự kiện trung thu cho thiếu nhi", "25/10/2025", "admin", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce vitae est nec nibh sagittis convallis. Nulla pulvinar, nulla quis efficitur tempus, lectus velit elementum mauris, eget sodales purus metus ut tellus. Morbi vitae erat risus. Fusce elementum tellus sed varius tincidunt. Nullam sed maximus dui. Nunc fermentum egestas neque eu placerat. Pellentesque condimentum commodo tincidunt. Praesent aliquam, justo ut imperdiet imperdiet, urna lorem congue lorem, at accumsan nulla metus eu ex. Nam massa risus, eleifend id interdum ac, ullamcorper ut nunc. Aliquam erat volutpat. Duis arcu sem, blandit eu risus et, interdum rutrum urna. Integer rutrum neque urna, non pulvinar nibh imperdiet ac. Quisque ullamcorper suscipit pretium."));

        // Gắn adapter
        NotificationAdapter adapter = new NotificationAdapter(list);
        recyclerView.setAdapter(adapter);

        return view;
    }
}
