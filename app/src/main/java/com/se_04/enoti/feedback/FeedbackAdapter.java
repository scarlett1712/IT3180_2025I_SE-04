package com.se_04.enoti.feedback;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.util.List;

public class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.ViewHolder> {

    private final List<FeedbackItem> feedbackList;
    private OnItemClickListener listener;

    // Interface for click events
    public interface OnItemClickListener {
        void onItemClick(FeedbackItem item);
    }

    // Constructor for User
    public FeedbackAdapter(List<FeedbackItem> feedbackList) {
        this.feedbackList = feedbackList;
        this.listener = null;
    }

    // Constructor for Admin
    public FeedbackAdapter(List<FeedbackItem> feedbackList, OnItemClickListener listener) {
        this.feedbackList = feedbackList;
        this.listener = listener;
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
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getDate());
        holder.txtRepliedNotification.setText(item.getRepliedNotification());

        int colorResId = R.color.status_default;
        if (item.isReplied()) {
            colorResId = R.color.status_replied;
        } else if (item.isRead()) {
            colorResId = R.color.status_read;
        } else if (item.isSent()) {
            colorResId = R.color.status_sent;
        }

        holder.statusIndicator.getBackground().setTint(
                ContextCompat.getColor(holder.itemView.getContext(), colorResId)
        );

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            FeedbackItem clicked = feedbackList.get(pos);

            if (listener != null) {
                // Admin flow: Use the callback
                listener.onItemClick(clicked);
            } else {
                // User flow: Open detail activity
                Intent intent = new Intent(v.getContext(), FeedbackDetailActivity.class);
                intent.putExtra("position", pos);
                intent.putExtra("title", clicked.getTitle());
                intent.putExtra("type", clicked.getType());
                intent.putExtra("date", clicked.getDate());
                intent.putExtra("content", clicked.getContent());
                intent.putExtra("repliedNotification", clicked.getRepliedNotification());
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtRepliedNotification;
        View statusIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtRepliedNotification = itemView.findViewById(R.id.txtRepliedNotification);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }
}
