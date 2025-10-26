package com.se_04.enoti.finance;

import android.annotation.SuppressLint;
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class FinanceAdapter extends RecyclerView.Adapter<FinanceAdapter.ViewHolder> implements Filterable {

    private final List<FinanceItem> financeList;
    private final List<FinanceItem> financeListFull;

    // Constructor
    public FinanceAdapter(List<FinanceItem> financeList) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_finance, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FinanceItem item = financeList.get(position);
        holder.txtTitle.setText(item.getTitle());
        holder.txtDate.setText(item.getDate());

        if (item.getPrice() != null) {
            String formatted = NumberFormat.getNumberInstance(Locale.getDefault())
                    .format(item.getPrice()) + " đ";
            holder.txtPrice.setText(formatted);
        } else {
            holder.txtPrice.setText("Tùy theo đóng góp");
        }

        holder.itemView.setAlpha(item.isPaid() ? 0.5f : 1f);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                Log.e("FinanceAdapter", "Invalid position clicked");
                return;
            }

            FinanceItem clicked = financeList.get(pos);
            Intent intent = new Intent(v.getContext(), FinanceDetailActivity.class);
            intent.putExtra("position", pos);
            intent.putExtra("title", clicked.getTitle());
            intent.putExtra("date", clicked.getDate());
            intent.putExtra("type", clicked.getType());
            intent.putExtra("price", clicked.getPrice());
            intent.putExtra("sender", clicked.getSender());
            v.getContext().startActivity(intent);
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
            List<FinanceItem> filtered = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filtered.addAll(financeListFull);
            } else {
                String query = removeAccents(constraint.toString().toLowerCase(Locale.ROOT).trim());
                String[] keywords = query.split("\\s+");

                for (FinanceItem item : financeListFull) {
                    String combined = (item.getTitle() + " " +
                            item.getType() + " " +
                            item.getSender() + " " +
                            item.getDate());

                    String normalized = removeAccents(combined.toLowerCase(Locale.ROOT));
                    String[] words = normalized.split("\\W+");

                    // ✅ All keywords must be found in the text (AND logic)
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
            financeList.clear();
            //noinspection unchecked
            financeList.addAll((List<FinanceItem>) results.values);
            notifyDataSetChanged();
        }
    };

    /** Remove Vietnamese accents */
    private static String removeAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        normalized = pattern.matcher(normalized).replaceAll("");
        return normalized.replaceAll("đ", "d").replaceAll("Đ", "D");
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<FinanceItem> newList) {
        financeList.clear();
        financeList.addAll(newList);
        financeListFull.clear();
        financeListFull.addAll(newList);
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
