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
    public void onBindViewHolder(@NonNull NotificationAdapter.ViewHolder holder, int position) {
        NotificationItem item = notificationList.get(position);
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getExpired_date());
        holder.txtContent.setText(item.getContent());

        // Định dạng: chưa đọc = đậm, đã đọc = nhạt
        holder.txtTitle.setTypeface(null, item.isRead() ? Typeface.NORMAL : Typeface.BOLD);
        holder.itemView.setAlpha(item.isRead() ? 0.6f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                Log.e("NotificationAdapter", "Invalid click pos");
                return;
            }

            NotificationItem clicked = notificationList.get(pos);
            Context context = v.getContext();

            // 🔹 Lấy role từ UserManager
            boolean isAdmin = false;
            try {
                String role = String.valueOf(UserManager.getInstance(context)
                        .getCurrentUser()
                        .getRole());

                Log.d("NotificationAdapter", "Current user role: " + role);

                // Cho phép nhiều định dạng role khác nhau
                if (role != null) {
                    role = role.trim().toLowerCase();
                    if (role.equals("2") || role.equals("admin") || role.equals("role_admin")) {
                        isAdmin = true;
                    }
                }

            } catch (Exception e) {
                Log.e("NotificationAdapter", "Error reading role", e);
            }

            // 🔹 Chọn activity phù hợp
            Intent intent;
            if (isAdmin) {
                Log.d("NotificationAdapter", "→ Opening admin detail activity");
                intent = new Intent(context, NotificationDetailActivity_Admin.class);
            } else {
                Log.d("NotificationAdapter", "→ Opening user detail activity");
                intent = new Intent(context, NotificationDetailActivity.class);
            }

            // 🔹 Truyền dữ liệu
            intent.putExtra("notification_id", clicked.getId());
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("expired_date", clicked.getExpired_date());
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            intent.putExtra("is_read", clicked.isRead());

            context.startActivity(intent);

            // 🔹 Cập nhật local: đánh dấu đã đọc
            if (!clicked.isRead()) {
                clicked.setRead(true);
                notifyItemChanged(pos);
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
