package com.se_04.enoti.notification;

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
import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    // Define view types
    public static final int VIEW_TYPE_NORMAL = 1;
    public static final int VIEW_TYPE_HIGHLIGHTED = 2;

    private List<NotificationItem> notificationList = new ArrayList<>();

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
        // The first 6 items are highlighted
        if (position < 6) {
            return VIEW_TYPE_HIGHLIGHTED;
        }
        return VIEW_TYPE_NORMAL;
    }

    @NonNull
    @Override
    public NotificationAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        // Inflate the correct layout based on view type
        if (viewType == VIEW_TYPE_HIGHLIGHTED) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_highlighted, parent, false);
        } else { // Default to NORMAL
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationAdapter.ViewHolder holder, int position) {
        NotificationItem item = notificationList.get(position);
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getExpired_date());
        holder.txtContent.setText(item.getContent());

        // Visual: unread bold + full alpha, read normal + dim
        holder.txtTitle.setTypeface(null, item.isRead() ? Typeface.NORMAL : Typeface.BOLD);
        holder.itemView.setAlpha(item.isRead() ? 0.6f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                Log.e("NotificationAdapter", "Invalid click pos");
                return;
            }
            NotificationItem clicked = notificationList.get(pos);
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("notification_id", clicked.getId());
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("expired_date", clicked.getExpired_date());
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            intent.putExtra("is_read", clicked.isRead());
            v.getContext().startActivity(intent);

            // locally mark read immediately
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
