package com.se_04.enoti.feedback;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.account.UserItem;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FeedbackFragment extends Fragment {

    private static final String TAG = "FeedbackFragment";
    private FeedbackAdapter adapter;
    private RecyclerView recyclerView;

    private final FeedbackRepository repository = FeedbackRepository.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshTask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feedback, container, false);

        // --- Giao diện chào ---
        TextView txtWelcome = view.findViewById(R.id.txtWelcome);
        TextView txtGreeting = view.findViewById(R.id.txtGreeting);

        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        String username = (currentUser != null) ? currentUser.getName() : "Người dùng";
        txtWelcome.setText("Xin chào " + username + "!");

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String timeOfDay;
        if (hour >= 5 && hour < 11) timeOfDay = "sáng";
        else if (hour >= 11 && hour < 14) timeOfDay = "trưa";
        else if (hour >= 14 && hour < 18) timeOfDay = "chiều";
        else timeOfDay = "tối";
        txtGreeting.setText(getString(R.string.greeting, timeOfDay));

        // --- RecyclerView ---
        recyclerView = view.findViewById(R.id.recyclerViewFeedback);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new FeedbackAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // --- Lần đầu load ---
        loadFeedbacks();

        // --- Cập nhật tự động mỗi 3 giây ---
        setupAutoRefresh();

        return view;
    }

    private void loadFeedbacks() {
        UserItem currentUser = UserManager.getInstance(requireContext()).getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Không tìm thấy người dùng hiện tại.");
            return;
        }

        String userId = currentUser.getId();
        repository.fetchFeedbacks(userId, new FeedbackRepository.FeedbackListCallback() {
            @Override
            public void onSuccess(List<FeedbackItem> feedbackList) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        adapter.updateList(feedbackList);
                        Log.d(TAG, "Loaded feedbacks: " + feedbackList.size());
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error loading feedbacks: " + errorMessage);
            }
        });
    }

    private void setupAutoRefresh() {
        autoRefreshTask = new Runnable() {
            @Override
            public void run() {
                loadFeedbacks();
                handler.postDelayed(this, 3000); // 3 giây
            }
        };
        handler.postDelayed(autoRefreshTask, 3000);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFeedbacks();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefreshTask);
    }
}
