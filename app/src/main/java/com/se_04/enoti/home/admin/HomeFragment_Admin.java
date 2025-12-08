package com.se_04.enoti.home.admin; // Your package name

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.finance.admin.CreateFinanceActivity;
import com.se_04.enoti.home.user.MainActivity_User;
import com.se_04.enoti.maintenance.admin.MaintenanceActivity;
import com.se_04.enoti.maintenance.admin.ManageAssetFragment;
import com.se_04.enoti.notification.NotificationAdapter;
import com.se_04.enoti.notification.NotificationItem;
import com.se_04.enoti.utils.UserManager;
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

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        String message = "Xin chào " + username + "!";
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

        LinearLayout layoutCreateFinance = view.findViewById(R.id.layoutCreateFinance);
        layoutCreateFinance.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CreateFinanceActivity.class);
            startActivity(intent);
        });

        LinearLayout layoutMaintenance = view.findViewById(R.id.layoutMaintenance);
        layoutMaintenance.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ManageAssetFragment.class);
            startActivity(intent);
        });

        setupQuickNav(view);

        return view;
    }

    private void setupQuickNav(View view) {
        view.findViewById(R.id.layoutManageResident).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Admin) {
                ((MainActivity_Admin) getActivity()).switchToManageResidentsTab();
            }
        });
        view.findViewById(R.id.layoutManageNotification).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Admin) {
                ((MainActivity_Admin) getActivity()).switchToManageNotificationsTab();
            }
        });
        view.findViewById(R.id.layoutBill).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Admin) {
                ((MainActivity_Admin) getActivity()).switchToManageFinanceTab();
            }
        });

        view.findViewById(R.id.layoutMaintenance).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_Admin) {
                ((MainActivity_Admin) getActivity()).switchToManageAssetTab();
            }
        });

        view.findViewById(R.id.layoutSettings).setOnClickListener(v ->
                Snackbar.make(v, "Chức năng sẽ được cập nhật trong thời gian tới.", Snackbar.LENGTH_LONG).show()
        );

        view.findViewById(R.id.layoutSupport).setOnClickListener(v ->
                Snackbar.make(v, "Chức năng sẽ được cập nhật trong thời gian tới.", Snackbar.LENGTH_LONG).show()
        );




    }
}
