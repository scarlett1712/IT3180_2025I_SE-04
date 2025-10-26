package com.se_04.enoti.finance;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinanceAdapter extends RecyclerView.Adapter<FinanceAdapter.ViewHolder> implements Filterable {

    private List<FinanceItem> financeList;
    private final List<FinanceItem> financeListFull;
    private OnItemClickListener listener; // Listener for admin clicks

    // Interface for click events (for admin)
    public interface OnItemClickListener {
        void onItemClick(FinanceItem item);
    }

    // Constructor for User flow
    public FinanceAdapter(List<FinanceItem> financeList) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
        this.listener = null; // No listener, will use default user behavior
    }

    // NEW: Constructor for Admin flow
    public FinanceAdapter(List<FinanceItem> financeList, OnItemClickListener listener) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
        this.listener = listener; // Set listener for admin behavior
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finance, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FinanceItem item = financeList.get(position);
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getDate());

        if (item.getPrice() != null && item.getPrice() > 0) {
            String formatted = NumberFormat.getNumberInstance(Locale.getDefault()).format(item.getPrice()) + " đ";
            holder.txtPrice.setText(formatted);
        } else {
            holder.txtPrice.setText(R.string.contribution_text);
        }

        holder.itemView.setOnClickListener(v -> {
            FinanceItem clickedItem = financeList.get(holder.getBindingAdapterPosition());

            if (listener != null) {
                // --- ADMIN FLOW ---
                // If a listener is set, use the callback. The fragment will decide where to go.
                listener.onItemClick(clickedItem);
            } else {
                // --- USER FLOW ---
                // If no listener, proceed with the default user behavior.
                Intent intent = new Intent(v.getContext(), FinanceDetailActivity.class);

                intent.putExtra("title", clickedItem.getTitle());
                intent.putExtra("content", clickedItem.getContent());
                intent.putExtra("date", clickedItem.getDate());
                intent.putExtra("type", clickedItem.getType());
                intent.putExtra("sender", clickedItem.getSender());

                long priceValue = (clickedItem.getPrice() != null) ? clickedItem.getPrice() : 0L;
                intent.putExtra("price", priceValue);

                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return financeList.size();
    }

    @Override
    public Filter getFilter() {
        return searchFilter;
    }

    private final Filter searchFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<FinanceItem> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(financeListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (FinanceItem item : financeListFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            financeList.clear();
            financeList.addAll((List<FinanceItem>) results.values);
            notifyDataSetChanged();
        }
    };

    @SuppressLint("NotifyDataSetChanged")
    public void filterByType(String type) {
        List<FinanceItem> filteredList = new ArrayList<>();
        if ("Tất cả".equalsIgnoreCase(type)) {
            filteredList.addAll(financeListFull);
        } else {
            for (FinanceItem item : financeListFull) {
                if (item.getType().equalsIgnoreCase(type)) {
                    filteredList.add(item);
                }
            }
        }
        financeList.clear();
        financeList.addAll(filteredList);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<FinanceItem> newList) {
        financeListFull.clear();
        financeListFull.addAll(newList);
        financeList.clear();
        financeList.addAll(newList);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDate, txtPrice;

        ViewHolder(View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtPrice = itemView.findViewById(R.id.txtPrice);
        }
    }
}
