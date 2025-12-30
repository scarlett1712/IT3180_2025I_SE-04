package com.se_04.enoti.home.user;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

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
    private static final int AUTO_SCROLL_INTERVAL = 6300; // 6.3 giây trượt 1 lần
    private static final int DATA_REFRESH_INTERVAL = 30000; // 30 giây load lại data từ server 1 lần

    private NotificationAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout indicatorLayout;
    private LinearLayoutManager layoutManager;
    private SnapHelper snapHelper;

    private int currentPage = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 🔥 Luồng 1: Tự động trượt Banner
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || adapter == null || layoutManager == null) return;

            int itemCount = adapter.getItemCount();
            if (itemCount > 1) {
                // Tính toán trang tiếp theo (Vòng lặp: nếu là cuối thì về 0)
                currentPage = (currentPage + 1) % itemCount;

                // Trượt mượt mà
                recyclerView.smoothScrollToPosition(currentPage);

                // Cập nhật dấu chấm
                updateDots(currentPage, itemCount);
            }
            handler.postDelayed(this, AUTO_SCROLL_INTERVAL);
        }
    };

    // 🔥 Luồng 2: Tự động cập nhật dữ liệu từ Server (Polling)
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                loadHighlightedNotifications();
                handler.postDelayed(this, DATA_REFRESH_INTERVAL);
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

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "▶️ HomeFragment_User resumed — starting auto-scroll & refresh");
        loadHighlightedNotifications();

        // Bắt đầu các tiến trình chạy tự động
        handler.removeCallbacks(autoScrollRunnable);
        handler.removeCallbacks(dataRefreshRunnable);
        handler.postDelayed(autoScrollRunnable, AUTO_SCROLL_INTERVAL);
        handler.postDelayed(dataRefreshRunnable, DATA_REFRESH_INTERVAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ HomeFragment_User paused — stopping auto-scroll");
        // Dừng tất cả để tránh tốn pin và lỗi memory leak
        handler.removeCallbacks(autoScrollRunnable);
        handler.removeCallbacks(dataRefreshRunnable);
    }

    private void setupWelcomeViews(View view) {
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null && currentUser.getName() != null)
                ? currentUser.getName() : "Cư dân";

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

        // Lắng nghe sự kiện người dùng vuốt tay để cập nhật chỉ số trang hiện tại
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        currentPage = layoutManager.getPosition(centerView);
                        updateDots(currentPage, adapter.getItemCount());
                    }
                }
            }
        });
    }

    private void loadHighlightedNotifications() {
        if (!isAdded() || getContext() == null) return;

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) return;

        try {
            long userId = Long.parseLong(currentUser.getId());
            NotificationRepository.getInstance(requireContext()).fetchNotifications(userId, new NotificationRepository.NotificationsCallback() {
                @Override
                public void onSuccess(List<NotificationItem> items) {
                    if (isAdded() && items != null && !items.isEmpty()) {
                        int listSize = Math.min(items.size(), MAX_HIGHLIGHTED_NOTIFICATIONS);
                        List<NotificationItem> limitedList = items.subList(0, listSize);

                        adapter.updateList(limitedList);
                        setupIndicator(limitedList.size());
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "⚠️ Load error: " + message);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing ID", e);
        }
    }

    private void setupIndicator(int itemCount) {
        if (indicatorLayout == null) return;
        indicatorLayout.removeAllViews();
        if (itemCount <= 1) return;

        for (int i = 0; i < itemCount; i++) {
            ImageView dot = new ImageView(requireContext());
            // Highlight trang hiện tại
            dot.setImageResource(i == currentPage ? R.drawable.indicator_active : R.drawable.indicator_inactive);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            indicatorLayout.addView(dot, params);
        }
    }

    private void updateDots(int position, int itemCount) {
        if (indicatorLayout == null || itemCount <= 1) return;
        for (int i = 0; i < itemCount; i++) {
            if (i < indicatorLayout.getChildCount()) {
                ImageView dot = (ImageView) indicatorLayout.getChildAt(i);
                dot.setImageResource(i == position ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            }
        }
    }

    private void setupQuickNav(View view) {
        view.findViewById(R.id.layoutNotification).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) ((MainActivity_User) getActivity()).switchToNotificationsTab();
        });
        view.findViewById(R.id.layoutFinance).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) ((MainActivity_User) getActivity()).switchToFinanceTab();
        });
        view.findViewById(R.id.layoutFeedback).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) ((MainActivity_User) getActivity()).switchToFeedbackTab();
        });
        view.findViewById(R.id.layoutAsset).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity_User) ((MainActivity_User) getActivity()).switchToAssetTab();
        });
    }
}