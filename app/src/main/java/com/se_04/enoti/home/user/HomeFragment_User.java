package com.se_04.enoti.home.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.notification.NotificationAdapter;
import com.se_04.enoti.notification.NotificationItem;
import com.se_04.enoti.notification.NotificationRepository;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.UserManager;

import java.util.Calendar;
import java.util.List;

public class HomeFragment_User extends Fragment {

    private NotificationAdapter adapter;
    private static int MAX_HIGHLIGHTED_NOTIFICATIONS = 6;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_user, container, false);

        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        // 🔹 Lấy thông tin người dùng từ UserManager (thay vì SharedPreferences)
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null && currentUser.getName() != null)
                ? currentUser.getName()
                : "Gia đình City";


        // 🔹 Hiển thị lời chào
        String message = getString(R.string.welcome, username);
        txtWelcome.setText(message);

        // 🔹 Xác định buổi trong ngày
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

        // 🔹 RecyclerView hiển thị thông báo nổi bật
        RecyclerView recyclerView = view.findViewById(R.id.recyclerHighlightedNotifications);
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);

        // --- FIX IS HERE ---
        // 1. Get the full list of notifications
        List<NotificationItem> fullList = NotificationRepository.getInstance().getNotifications();

        // 2. Determine the actual number of items to show (to prevent crashes if the list is small)
        int listSize = Math.min(fullList.size(), MAX_HIGHLIGHTED_NOTIFICATIONS);

        // 3. Create a sublist with the limited number of items
        List<NotificationItem> limitedList = fullList.subList(0, listSize);

        // 4. Pass the limited list to the adapter
        adapter = new NotificationAdapter(limitedList, NotificationAdapter.VIEW_TYPE_HIGHLIGHTED);
        recyclerView.setAdapter(adapter);

        // Hiệu ứng cuộn từng item
        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        // 🔹 Indicator chấm tròn
        LinearLayout indicatorLayout = view.findViewById(R.id.indicatorLayout);
        int itemCount = limitedList.size(); // Use the size of the limited list
        ImageView[] dots = new ImageView[itemCount];

        // Add a check to prevent crash if list is empty
        if (itemCount > 0) {
            for (int i = 0; i < itemCount; i++) {
                dots[i] = new ImageView(requireContext());
                dots[i].setImageResource(i == 0
                        ? R.drawable.indicator_active
                        : R.drawable.indicator_inactive);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(8, 0, 8, 0);
                indicatorLayout.addView(dots[i], params);
            }

            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        View centerView = snapHelper.findSnapView(layoutManager);
                        if (centerView != null) {
                            int pos = layoutManager.getPosition(centerView);
                            for (int i = 0; i < itemCount; i++) {
                                dots[i].setImageResource(i == pos
                                        ? R.drawable.indicator_active
                                        : R.drawable.indicator_inactive);
                            }
                        }
                    }
                }
            });
        }

        // 🔹 Chuyển tab nhanh
        LinearLayout btnNotification = view.findViewById(R.id.layoutNotification);
        btnNotification.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToNotificationsTab();
            }
        });

        LinearLayout btnFinance = view.findViewById(R.id.layoutFinance);
        btnFinance.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToFinanceTab();
            }
        });

        LinearLayout btnFeedback = view.findViewById(R.id.layoutFeedback);
        btnFeedback.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToFeedbackTab();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
