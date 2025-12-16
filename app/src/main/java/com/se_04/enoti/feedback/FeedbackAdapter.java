package com.se_04.enoti.feedback;

import android.content.Context;
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
        Context context = holder.itemView.getContext(); // 🔥 Lấy Context từ View

        // --- Hiển thị tiêu đề và ngày tạo ---
        holder.txtTitle.setText("Phản hồi #" + item.getFeedbackId());
        holder.txtDate.setText(item.getCreatedAt());

        // --- Hiển thị tạm ---
        holder.txtContent.setText("Đang tải tên thông báo...");

        // --- Lấy tên thông báo từ repository ---
        // 🔥 SỬA: Truyền 'context' vào getInstance
        NotificationRepository.getInstance(context).fetchNotificationTitle(
                item.getNotificationId(),
                new NotificationRepository.TitleCallback() {
                    @Override
                    public void onSuccess(String title) {
                        // Kiểm tra xem ViewHolder có còn ở vị trí cũ không (tránh lỗi khi cuộn nhanh)
                        if (holder.getBindingAdapterPosition() == position) {
                            holder.txtContent.setText("Phản hồi cho: " + title);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (holder.getBindingAdapterPosition() == position) {
                            holder.txtContent.setText("Phản hồi cho thông báo #" + item.getNotificationId());
                        }
                    }
                }
        );

        // --- Hiển thị màu trạng thái ---
        int colorResId;
        String status = item.getStatus();
        if (status == null) status = "pending";

        switch (status.toLowerCase()) {
            case "sent":
                colorResId = R.color.status_sent;
                break;
            case "read":
                colorResId = R.color.status_read;
                break;
            case "replied":
                colorResId = R.color.status_replied;
                break;
            case "pending":
            default:
                colorResId = R.color.status_default;
                break;
        }

        // Kiểm tra null để tránh crash nếu view chưa có background
        if (holder.statusIndicator.getBackground() != null) {
            holder.statusIndicator.getBackground().setTint(
                    ContextCompat.getColor(context, colorResId)
            );
        }

        // --- Click item ---
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                FeedbackItem clicked = feedbackList.get(currentPos);
                if (listener != null) {
                    listener.onItemClick(clicked);
                } else {
                    Intent intent = new Intent(context, FeedbackDetailActivity.class);
                    intent.putExtra("feedback_item", clicked); // FeedbackItem cần implements Serializable
                    context.startActivity(intent);
                }
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
            // Lưu ý: ID này phải khớp với layout item_feedback.xml
            txtContent = itemView.findViewById(R.id.txtRepliedNotification);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}