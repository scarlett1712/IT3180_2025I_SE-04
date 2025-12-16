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

    // 🔥 Callback interface
    public interface OnNotificationClickListener {
        void onNotificationClicked(long notificationId);
    }

    public NotificationAdapter(List<NotificationItem> initial, int viewTypeHighlighted) {
        if (initial != null) notificationList.addAll(initial);
    }

    // 🔥 Set click listener
    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.clickListener = listener;
    }

    public void updateList(List<NotificationItem> newList) {
        notificationList.clear();
        if (newList != null) notificationList.addAll(newList);
        notifyDataSetChanged();
        Log.d(TAG, "📋 List updated with " + notificationList.size() + " items");
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

        // Display date logic
        String displayDate = notification.getExpired_date();
        if (displayDate == null || displayDate.isEmpty()) {
            displayDate = notification.getDate();
        }
        holder.txtDate.setText(displayDate);

        holder.txtContent.setText(notification.getContent());

        // 🔥 Visual indicator for read/unread status
        if (notification.isRead()) {
            holder.itemView.setAlpha(0.7f); // Dim read notifications
        } else {
            holder.itemView.setAlpha(1.0f); // Full opacity for unread
        }

        // Handle click event
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            NotificationItem clicked = notificationList.get(pos);
            Context context = holder.itemView.getContext();

            Log.d(TAG, "🖱️ Clicked notification ID: " + clicked.getId() + " (isRead: " + clicked.isRead() + ")");

            // 🔥 Mark as read locally FIRST for immediate UI feedback
            boolean wasUnread = !clicked.isRead();
            if (wasUnread) {
                clicked.setRead(true);
                notifyItemChanged(pos);
                Log.d(TAG, "📝 Updated local item to isRead=true");

                // Notify fragment to update cache
                if (clickListener != null) {
                    Log.d(TAG, "📞 Calling fragment callback");
                    clickListener.onNotificationClicked(clicked.getId());
                } else {
                    Log.e(TAG, "❌ WARNING: clickListener is NULL! Fragment won't be notified!");
                }

                // Mark as read on server
                NotificationRepository.getInstance(context).markAsRead(clicked.getId(), new NotificationRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "✅ Server confirmed notification " + clicked.getId() + " marked as read");
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "❌ Server error marking as read: " + message);
                    }
                });
            } else {
                Log.d(TAG, "ℹ️ Notification already marked as read, skipping update");
            }

            // Open detail activity
            Intent intent;
            if (UserManager.getInstance(context).isAdmin()) {
                intent = new Intent(context, NotificationDetailActivity_Admin.class);
            } else {
                intent = new Intent(context, NotificationDetailActivity.class);
            }

            intent.putExtra("notification_id", clicked.getId());
            intent.putExtra("title", clicked.getTitle());

            String expDate = clicked.getExpired_date();
            if (expDate == null || expDate.equals("null")) expDate = "";
            intent.putExtra("expired_date", expDate);
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            intent.putExtra("is_read", clicked.isRead());

            // 🔥 4. TRUYỀN DỮ LIỆU FILE QUA INTENT (QUAN TRỌNG)
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