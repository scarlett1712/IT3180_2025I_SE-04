package com.se_04.enoti.authority;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.util.List;

public class AuthorityMessageAdapter extends RecyclerView.Adapter<AuthorityMessageAdapter.MessageViewHolder> {

    private final Context context;
    private final List<AuthorityMessage> messageList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AuthorityMessage message);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public AuthorityMessageAdapter(Context context, List<AuthorityMessage> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_authority_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AuthorityMessage message = messageList.get(position);

        holder.tvTitle.setText(message.getTitle());
        holder.tvSender.setText("Từ: " + message.getSenderName());
        holder.tvTimestamp.setText(message.getCreatedAt());

        // Set icon based on category
        switch (message.getCategory()) {
            case "PCCC":
                holder.ivCategory.setImageResource(R.drawable.ic_fire);
                break;
            case "Hành chính":
                holder.ivCategory.setImageResource(R.drawable.ic_document);
                break;
            case "An ninh":
                holder.ivCategory.setImageResource(R.drawable.ic_security);
                break;
            case "Họp":
                holder.ivCategory.setImageResource(R.drawable.ic_meeting);
                break;
            default:
                holder.ivCategory.setImageResource(R.drawable.ic_notification);
                break;
        }

        // Highlight urgent messages
        if (message.isUrgent()) {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.urgent_background));
            holder.ivPriority.setVisibility(View.VISIBLE);
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.ivPriority.setVisibility(View.GONE);
        }
        
        // Set read status
        if(message.isRead()) {
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(message);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivCategory, ivPriority;
        TextView tvTitle, tvSender, tvTimestamp;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            ivCategory = itemView.findViewById(R.id.iv_category_icon);
            ivPriority = itemView.findViewById(R.id.iv_priority_icon);
            tvTitle = itemView.findViewById(R.id.tv_message_title);
            tvSender = itemView.findViewById(R.id.tv_sender_name);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }
    }
}
