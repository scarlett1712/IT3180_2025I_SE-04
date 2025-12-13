package com.se_04.enoti.notification;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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

    public static final int VIEW_TYPE_NORMAL = 1;
    public static final int VIEW_TYPE_HIGHLIGHTED = 2;

    private final List<NotificationItem> notificationList = new ArrayList<>();

    public NotificationAdapter(List<NotificationItem> initial, int viewTypeHighlighted) {
        if (initial != null) notificationList.addAll(initial);
    }

    public void updateList(List<NotificationItem> newList) {
        notificationList.clear();
        if (newList != null) notificationList.addAll(newList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (position < 6) ? VIEW_TYPE_HIGHLIGHTED : VIEW_TYPE_NORMAL;
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

        holder.txtTitle.setText(notification.getTitle());

        // 🔥 LOGIC HIỂN THỊ NGÀY TRÊN DANH SÁCH 🔥
        // Nếu có ngày hẹn (hết hạn) thì hiển thị, nếu không thì hiển thị ngày tạo
        String displayDate = notification.getExpired_date();
        if (displayDate == null || displayDate.isEmpty()) {
            displayDate = notification.getDate();
        }
        holder.txtDate.setText(displayDate);

        holder.txtContent.setText(notification.getContent());

        // ... (phần xử lý màu nền giữ nguyên) ...

        // Xử lý sự kiện click item
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            NotificationItem clicked = notificationList.get(pos);
            Context context = holder.itemView.getContext();
            Intent intent;

            // Xác định Activity đích (User hay Admin)
            if (UserManager.getInstance(context).isAdmin()) {
                intent = new Intent(context, NotificationDetailActivity_Admin.class);
            } else {
                intent = new Intent(context, NotificationDetailActivity.class);
            }

            // 🔹 Truyền dữ liệu
            intent.putExtra("notification_id", clicked.getId());
            intent.putExtra("title", clicked.getTitle());

            // 🔥 KIỂM TRA NULL KHI PUT EXTRA 🔥
            String expDate = clicked.getExpired_date();
            if (expDate == null || expDate.equals("null")) expDate = ""; // Fallback an toàn
            intent.putExtra("expired_date", expDate);

            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            intent.putExtra("is_read", clicked.isRead());

            context.startActivity(intent);

            // 🔹 Cập nhật local: đánh dấu đã đọc
            if (!clicked.isRead()) {
                clicked.setRead(true);
                notifyItemChanged(pos);
                NotificationRepository.getInstance(context).markAsRead(clicked.getId());
            }
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
