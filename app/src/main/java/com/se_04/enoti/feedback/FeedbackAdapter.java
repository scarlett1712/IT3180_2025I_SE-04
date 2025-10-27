package com.se_04.enoti.feedback;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.notification.NotificationRepository;

import java.util.List;

public class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.ViewHolder> {

    private List<FeedbackItem> feedbackList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FeedbackItem item);
    }

    public FeedbackAdapter(List<FeedbackItem> feedbackList) {
        this.feedbackList = feedbackList;
    }

    public FeedbackAdapter(List<FeedbackItem> feedbackList, OnItemClickListener listener) {
        this.feedbackList = feedbackList;
        this.listener = listener;
    }

    public void updateList(List<FeedbackItem> newList) {
        this.feedbackList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feedback, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedbackItem item = feedbackList.get(position);

        // --- Hiển thị tiêu đề và ngày tạo ---
        holder.txtTitle.setText("Phản hồi #" + item.getFeedbackId());
        holder.txtDate.setText(item.getCreatedAt());

        // --- Hiển thị tạm ---
        holder.txtContent.setText("Đang tải tên thông báo...");

        // --- Lấy tên thông báo từ repository ---
        NotificationRepository.getInstance().fetchNotificationTitle(
                item.getNotificationId(),
                new NotificationRepository.TitleCallback() {
                    @Override
                    public void onSuccess(String title) {
                        holder.txtContent.setText("Phản hồi cho thông báo: " + title);
                    }

                    @Override
                    public void onError(String message) {
                        holder.txtContent.setText("Phản hồi cho thông báo #" + item.getNotificationId());
                    }
                }
        );

        // --- Hiển thị màu trạng thái ---
        int colorResId;
        if (item.getStatus() == null) {
            colorResId = R.color.status_default; // fallback
        } else {
            switch (item.getStatus().toLowerCase()) {
                case "pending":
                    colorResId = R.color.status_default;
                    break;
                case "sent":
                    colorResId = R.color.status_sent;
                    break;
                case "read":
                    colorResId = R.color.status_read;
                    break;
                case "replied":
                    colorResId = R.color.status_replied;
                    break;
                default:
                    colorResId = R.color.status_default;
                    break;
            }
        }

        holder.statusIndicator.getBackground().setTint(
                ContextCompat.getColor(holder.itemView.getContext(), colorResId)
        );

        // --- Click item ---
        holder.itemView.setOnClickListener(v -> {
            FeedbackItem clicked = feedbackList.get(holder.getBindingAdapterPosition());
            if (listener != null) {
                listener.onItemClick(clicked);
            } else {
                Intent intent = new Intent(v.getContext(), FeedbackDetailActivity.class);
                intent.putExtra("feedback_item", clicked);
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return (feedbackList != null) ? feedbackList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtContent;
        View statusIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtContent = itemView.findViewById(R.id.txtRepliedNotification);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}
