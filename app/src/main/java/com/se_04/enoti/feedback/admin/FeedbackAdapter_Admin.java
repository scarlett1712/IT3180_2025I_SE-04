package com.se_04.enoti.feedback.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.feedback.admin.FeedbackItem_Admin;

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
