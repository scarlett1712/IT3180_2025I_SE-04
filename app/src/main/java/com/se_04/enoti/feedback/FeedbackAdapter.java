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

    public FeedbackAdapter(List<FeedbackItem> feedbackList) {
        this.feedbackList = feedbackList;
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

        // Đổi màu chấm trạng thái
        int colorResId = R.color.status_default;
        
        if (item.isReplied()) {
            colorResId = R.color.status_replied; // xanh lá
        } else if (item.isRead()) {
            colorResId = R.color.status_read;    // cam
        } else if (item.isSent()) {
            colorResId = R.color.status_sent;    // xanh dương
        }

        holder.statusIndicator.getBackground().setTint(
                ContextCompat.getColor(holder.itemView.getContext(), colorResId)
        );

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();

            if (pos == RecyclerView.NO_POSITION) {
                Log.e("AdapterClick", "Clicked an item with no position, aborting.");
                return;
            }
            FeedbackItem clicked = feedbackList.get(pos);

            Intent intent = new Intent(v.getContext(), FeedbackDetailActivity.class);

            intent.putExtra("position", pos);
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("type", clicked.getType());
            intent.putExtra("date", clicked.getDate());
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("repliedNotification", clicked.getRepliedNotification());
            v.getContext().startActivity(intent);

        });
    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    // ViewHolder
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txyType, txtDate, txtContent, txtRepliedNotification;
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
