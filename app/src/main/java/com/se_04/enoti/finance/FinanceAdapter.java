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
import com.se_04.enoti.finance.admin.FinanceDetailActivity_Admin;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinanceAdapter extends RecyclerView.Adapter<FinanceAdapter.ViewHolder> implements Filterable {

    private List<FinanceItem> financeList;
    private final List<FinanceItem> financeListFull;
    private final OnItemClickListener listener;
    private final boolean isAdmin;

    // Giao diện click cho admin
    public interface OnItemClickListener {
        void onItemClick(FinanceItem item);
    }

    // --- USER MODE ---
    public FinanceAdapter(List<FinanceItem> financeList) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
        this.listener = null;
        this.isAdmin = false;
    }

    // --- ADMIN MODE ---
    public FinanceAdapter(List<FinanceItem> financeList, OnItemClickListener listener) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
        this.listener = listener;
        this.isAdmin = true;
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

        // ✅ Xử lý ngày hợp lệ
        String safeDate = (item.getDate() == null || item.getDate().trim().isEmpty() || item.getDate().equalsIgnoreCase("null"))
                ? "Không" : item.getDate();

        // 💰 Hiển thị giá tiền
        if (item.getPrice() != null && item.getPrice() > 0) {
            String formatted = NumberFormat.getNumberInstance(Locale.getDefault()).format(item.getPrice()) + " đ";
            holder.txtPrice.setText(formatted);
        } else {
            holder.txtPrice.setText(R.string.contribution_text);
        }

        // 🧾 Hiển thị khác nhau tùy vai trò
        if (isAdmin) {
            if (item.getTotalUsers() > 0) {
                holder.txtDate.setText(
                        "Ngày tạo: " + safeDate +
                                "  •  Đã thu " + item.getPaidUsers() + "/" + item.getTotalUsers()
                );
            } else {
                holder.txtDate.setText("Ngày tạo: " + safeDate);
            }
        } else {
            holder.txtDate.setText("Hạn đóng: " + safeDate);
        }

        // 👇 Xử lý sự kiện click
        holder.itemView.setOnClickListener(v -> {
            FinanceItem clickedItem = financeList.get(holder.getBindingAdapterPosition());

            String clickedDate = (clickedItem.getDate() == null || clickedItem.getDate().trim().isEmpty() || clickedItem.getDate().equalsIgnoreCase("null"))
                    ? "Không" : clickedItem.getDate();

            if (isAdmin) {
                // ✅ ADMIN FLOW — mở màn admin
                Intent intent = new Intent(v.getContext(), FinanceDetailActivity_Admin.class);
                intent.putExtra("finance_id", clickedItem.getId());
                intent.putExtra("title", clickedItem.getTitle());
                intent.putExtra("due_date", clickedDate);
                v.getContext().startActivity(intent);
            } else {
                // 👤 USER FLOW — mở màn user
                Intent intent = new Intent(v.getContext(), FinanceDetailActivity.class);
                intent.putExtra("title", clickedItem.getTitle());
                intent.putExtra("content", clickedItem.getContent());
                intent.putExtra("date", clickedDate);
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

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            financeList.clear();
            financeList.addAll((List<FinanceItem>) results.values);
            notifyDataSetChanged();
        }
    };

    // 🔄 Cập nhật danh sách mới
    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<FinanceItem> newList) {
        financeListFull.clear();
        financeListFull.addAll(newList);
        financeList.clear();
        financeList.addAll(newList);
        notifyDataSetChanged();
    }

    // 🔍 Lọc theo loại
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