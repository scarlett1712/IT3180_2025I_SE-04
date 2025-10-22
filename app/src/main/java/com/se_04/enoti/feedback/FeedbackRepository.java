package com.se_04.enoti.feedback;

import java.util.ArrayList;
import java.util.List;

public class FeedbackRepository {
    private static FeedbackRepository instance;
    private final List<FeedbackItem> feedbacks;

    private FeedbackRepository() {
        feedbacks = new ArrayList<>();
        seedData();
    }

    public static FeedbackRepository getInstance() {
        if (instance == null) {
            instance = new FeedbackRepository();
        }
        return instance;
    }

    public List<FeedbackItem> getFeedbacks() { return feedbacks; }

    private void seedData() {
        feedbacks.add(new FeedbackItem("1", "Cúp điện toàn khu", "phản hồi thông báo", "10/10/2025", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam at dolor vitae lorem tristique tincidunt. Vestibulum congue congue sapien, in scelerisque libero porta in. Aliquam et tortor sed ipsum lacinia mollis convallis quis nulla.", "Cúp điện toàn khu"));
    }
}
