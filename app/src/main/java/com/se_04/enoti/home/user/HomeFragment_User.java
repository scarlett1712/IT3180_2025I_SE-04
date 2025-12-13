package com.se_04.enoti.home.user;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.android.material.snackbar.Snackbar;
import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.notification.NotificationAdapter;
import com.se_04.enoti.notification.NotificationItem;
import com.se_04.enoti.notification.NotificationRepository;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeFragment_User extends Fragment {

    private static final String TAG = "HomeFragment_User";
    private static final int MAX_HIGHLIGHTED_NOTIFICATIONS = 6;
    private static final int REFRESH_INTERVAL = 3000; // Tự động làm mới mỗi 3 giây

    private NotificationAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout indicatorLayout;
    private LinearLayoutManager layoutManager;
    private SnapHelper snapHelper;

    // Handler để chạy auto-refresh
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadHighlightedNotifications();
                // Lặp lại sau mỗi khoảng thời gian
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_user, container, false);

        setupWelcomeViews(view);
        setupRecyclerView(view);
        setupQuickNav(view);

        return view;
    }

    // 🔥 LIFECYCLE: Bắt đầu refresh khi màn hình hiện
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "▶️ HomeFragment_User resumed — starting auto-refresh");
        loadHighlightedNotifications();
        handler.removeCallbacks(refreshRunnable); // Xóa callback cũ để tránh chồng chéo
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    // 🔥 LIFECYCLE: Dừng refresh khi màn hình ẩn
    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void setupWelcomeViews(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null && currentUser.getName() != null)
                ? currentUser.getName()
                : "Cư dân";

        txtWelcome.setText(getString(R.string.welcome, username));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay = (hour >= 5 && hour < 11) ? "sáng" :
                (hour >= 11 && hour < 14) ? "trưa" :
                        (hour >= 14 && hour < 18) ? "chiều" : "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.recyclerHighlightedNotifications);
        indicatorLayout = view.findViewById(R.id.indicatorLayout);
        layoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new NotificationAdapter(new ArrayList<>(), NotificationAdapter.VIEW_TYPE_HIGHLIGHTED);
        recyclerView.setAdapter(adapter);

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);
    }

    private void loadHighlightedNotifications() {
        // Kiểm tra context để tránh crash khi fragment chưa gắn vào activity
        if (!isAdded() || getContext() == null) return;

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            Log.e(TAG, "❌ Cannot load notifications: user or user ID is null.");
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(currentUser.getId());
        } catch (NumberFormatException e) {
            Log.e(TAG, "❌ Could not parse user ID: " + currentUser.getId(), e);
            return;
        }

        NotificationRepository.getInstance().fetchNotifications(userId, new NotificationRepository.NotificationsCallback() {
            @Override
            public void onSuccess(List<NotificationItem> items) {
                if (isAdded() && items != null) {
                    // Lấy tối đa 6 thông báo mới nhất để hiển thị nổi bật
                    int listSize = Math.min(items.size(), MAX_HIGHLIGHTED_NOTIFICATIONS);
                    List<NotificationItem> limitedList = items.subList(0, listSize);

                    adapter.updateList(limitedList);
                    setupIndicator(limitedList.size());
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    // Log lỗi âm thầm thay vì hiện Toast liên tục (vì đang chạy polling)
                    Log.e(TAG, "⚠️ Failed to fetch notifications: " + message);
                }
            }
        });
    }

    private void setupIndicator(int itemCount) {
        indicatorLayout.removeAllViews();
        if (itemCount <= 1) return;

        ImageView[] dots = new ImageView[itemCount];
        for (int i = 0; i < itemCount; i++) {
            dots[i] = new ImageView(requireContext());
            dots[i].setImageResource(i == 0 ? R.drawable.indicator_active : R.drawable.indicator_inactive);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            indicatorLayout.addView(dots[i], params);
        }

        recyclerView.clearOnScrollListeners();
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        for (int i = 0; i < itemCount; i++) {
                            if (i < indicatorLayout.getChildCount()) {
                                ImageView dot = (ImageView) indicatorLayout.getChildAt(i);
                                dot.setImageResource(i == pos ? R.drawable.indicator_active : R.drawable.indicator_inactive);
                            }
                        }
                    }
                }
            }
        });
    }

    private void setupQuickNav(View view) {
        view.findViewById(R.id.layoutNotification).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToNotificationsTab();
            }
        });
        view.findViewById(R.id.layoutFinance).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToFinanceTab();
            }
        });
        view.findViewById(R.id.layoutFeedback).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToFeedbackTab();
            }
        });

        view.findViewById(R.id.layoutAsset).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) {
                ((MainActivity_User) getActivity()).switchToAssetTab();
            }
        });

    }
}