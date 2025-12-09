package com.se_04.enoti.feedback.admin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.utils.BaseActivity;

import java.util.List;

public class FeedbackAdapter_Admin extends RecyclerView.Adapter<FeedbackAdapter_Admin.ViewHolder> {

    private final List<FeedbackItem_Admin> feedbackList;

    public FeedbackAdapter_Admin(List<FeedbackItem_Admin> feedbackList) {
        this.feedbackList = feedbackList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feedback_admin, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedbackItem_Admin item = feedbackList.get(position);

        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getDate());
        holder.txtRepliedNotification.setText(item.getSender());

        // 🔥 Khi bấm vào một phản hồi -> mở màn hình trả lời phản hồi
        holder.itemView.setOnClickListener(v -> {
            // Lấy context của Activity cha
            BaseActivity activity = (BaseActivity) v.getContext();

            // Tạo instance của BottomSheet và truyền dữ liệu
            FeedbackReplyBottomSheet_Admin bottomSheet =
                    FeedbackReplyBottomSheet_Admin.newInstance(
                            item.getId(),
                            item.getTitle() // hoặc item.getContent() nếu bạn muốn nội dung gốc thay vì tiêu đề
                    );

            // Hiển thị popup trượt từ dưới lên
            bottomSheet.show(activity.getSupportFragmentManager(), "FeedbackReplyBottomSheet_Admin");
        });

    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtRepliedNotification;

        ViewHolder(View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtRepliedNotification = itemView.findViewById(R.id.txtRepliedNotification);
        }
    }
}
