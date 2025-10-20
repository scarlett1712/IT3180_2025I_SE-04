package com.se_04.enoti.notification;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> implements Filterable {

    public static final int VIEW_TYPE_NORMAL = 1;
    public static final int VIEW_TYPE_HIGHLIGHTED = 2;

    private List<NotificationItem> notificationList;
    private final List<NotificationItem> notificationListFull;
    private final int viewType;

    public NotificationAdapter(List<NotificationItem> notificationList) {
        this.notificationList = new ArrayList<>(notificationList);
        this.notificationListFull = new ArrayList<>(notificationList);
        this.viewType = VIEW_TYPE_NORMAL;
    }

    public NotificationAdapter(List<NotificationItem> notificationList, int viewType) {
        this.notificationList = new ArrayList<>(notificationList);
        this.notificationListFull = new ArrayList<>(notificationList);
        this.viewType = viewType;
    }

    public void updateData(List<NotificationItem> newItems) {
        notificationList.clear();
        notificationListFull.clear();
        if (newItems != null) {
            notificationList.addAll(newItems);
            notificationListFull.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return this.viewType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = notificationList.get(position);
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getDate());

        if (holder.txtContent != null) {
            holder.txtContent.setText(item.getContent());
        }

        holder.itemView.setAlpha(item.isRead() ? 0.6f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                Log.e("NotificationAdapter", "Invalid item click position");
                return;
            }

            NotificationItem clicked = notificationList.get(pos);
            Intent intent = new Intent(v.getContext(), NotificationDetailActivity.class);
            intent.putExtra("position", pos);
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("date", clicked.getDate());
            intent.putExtra("content", clicked.getContent());
            intent.putExtra("sender", clicked.getSender());
            v.getContext().startActivity(intent);

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

    @Override
    public Filter getFilter() {
        return searchFilter;
    }

    private final Filter searchFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<NotificationItem> filtered = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filtered.addAll(notificationListFull);
            } else {
                String query = removeAccents(constraint.toString().toLowerCase(Locale.ROOT).trim());
                String[] keywords = query.split("\\s+");

                for (NotificationItem item : notificationListFull) {
                    String combined = (item.getTitle() + " " +
                            item.getContent() + " " +
                            item.getSender() + " " +
                            item.getDate());

                    String normalized = removeAccents(combined.toLowerCase(Locale.ROOT));
                    String[] words = normalized.split("\\W+");

                    boolean allMatch = true;
                    for (String key : keywords) {
                        boolean found = false;
                        for (String w : words) {
                            if (w.equals(key)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            allMatch = false;
                            break;
                        }
                    }

                    if (allMatch) filtered.add(item);
                }
            }

            FilterResults results = new FilterResults();
            results.values = filtered;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            notificationList.clear();
            //noinspection unchecked
            notificationList.addAll((List<NotificationItem>) results.values);
            notifyDataSetChanged();
        }
    };

    /** Remove Vietnamese accent marks */
    private static String removeAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        normalized = pattern.matcher(normalized).replaceAll("");
        return normalized.replaceAll("đ", "d").replaceAll("Đ", "D");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtContent;

        ViewHolder(View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtContent = itemView.findViewById(R.id.txtContent);
        }
    }

    public void updateList(List<NotificationItem> newList) {
        this.notificationList = newList;
        notifyDataSetChanged();
    }
}
