package com.se_04.enoti.notification;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.notification.admin.NotificationDetailActivity_Admin;
import com.se_04.enoti.utils.UserManager;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private static final String TAG = "NotificationAdapter";
    public static final int VIEW_TYPE_NORMAL = 1;
    public static final int VIEW_TYPE_HIGHLIGHTED = 2;

    private final List<NotificationItem> notificationList = new ArrayList<>();
    private OnNotificationClickListener clickListener;
    private final int currentViewType;

    public interface OnNotificationClickListener {
        void onNotificationClicked(long notificationId);
    }

    public NotificationAdapter(List<NotificationItem> initial, int viewType) {
        if (initial != null) notificationList.addAll(initial);
        this.currentViewType = viewType;
    }

    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.clickListener = listener;
    }

    public void updateList(List<NotificationItem> newList) {
        notificationList.clear();
        if (newList != null) notificationList.addAll(newList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return currentViewType;
    }

    @NonNull
    @Override
    public NotificationAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        if (viewType == VIEW_TYPE_HIGHLIGHTED) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification_highlighted, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem notification = notificationList.get(position);
        if (notification == null) return;

        // --- ĐIỀU CHỈNH KÍCH THƯỚC ---
        ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
        if (currentViewType == VIEW_TYPE_HIGHLIGHTED) {
            params.width = (int) (holder.itemView.getContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            int heightInDp = 160;
            params.height = (int) (heightInDp * holder.itemView.getContext().getResources().getDisplayMetrics().density);

            holder.itemView.setMinimumHeight(params.height);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        holder.itemView.setLayoutParams(params);

        // Bind dữ liệu
        holder.txtTitle.setText(notification.getTitle());
        String displayDate = (notification.getExpired_date() == null || notification.getExpired_date().isEmpty())
                ? notification.getDate() : notification.getExpired_date();
        holder.txtDate.setText(displayDate);
        holder.txtContent.setText(notification.getContent());

        // 🔥 LOGIC LÀM MỜ: Nếu là HIGHLIGHTED thì luôn rõ nét (1.0f)
        if (currentViewType == VIEW_TYPE_HIGHLIGHTED) {
            holder.itemView.setAlpha(1.0f);
        } else {
            // Chỉ làm mờ ở trang danh sách thông báo bình thường
            holder.itemView.setAlpha(notification.isRead() ? 0.7f : 1.0f);
        }

        // Xử lý Click
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            NotificationItem clicked = notificationList.get(pos);
            Context context = holder.itemView.getContext();

            if (!clicked.isRead()) {
                clicked.setRead(true);
                notifyItemChanged(pos);
                if (clickListener != null) clickListener.onNotificationClicked(clicked.getId());

                NotificationRepository.getInstance(context).markAsRead(clicked.getId(), new NotificationRepository.SimpleCallback() {
                    @Override public void onSuccess() { Log.d(TAG, "Marked as read"); }
                    @Override public void onError(String msg) { Log.e(TAG, "Error: " + msg); }
                });
            }

            Intent intent;
            if (UserManager.getInstance(context).isAdmin()) {
                intent = new Intent(context, NotificationDetailActivity_Admin.class);
            } else {
                intent = new Intent(context, NotificationDetailActivity.class);
            }

            intent.putExtra("notification_id", clicked.getId());
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            intent.putExtra("is_read", clicked.isRead());
            intent.putExtra("file_url", clicked.getFileUrl());
            intent.putExtra("file_type", clicked.getFileType());

            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtContent;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtContent = itemView.findViewById(R.id.txtContent);
        }
    }
}