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

    private final List<FinanceItem> financeList;      // Danh sách hiển thị
    private final List<FinanceItem> financeListFull;  // Danh sách gốc (để lọc)
    private final boolean isAdmin;

    // Interface (Giữ lại nếu sau này cần dùng, hiện tại xử lý click nội bộ)
    public interface OnItemClickListener {
        void onItemClick(FinanceItem item);
    }

    // --- USER MODE ---
    public FinanceAdapter(List<FinanceItem> financeList) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
        this.isAdmin = false;
    }

    // --- ADMIN MODE ---
    // (Listener có thể null nếu không dùng, code click đã xử lý bên trong onBind)
    public FinanceAdapter(List<FinanceItem> financeList, OnItemClickListener listener) {
        this.financeList = new ArrayList<>(financeList);
        this.financeListFull = new ArrayList<>(financeList);
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

        // 1. Set Title
        holder.txtTitle.setText(item.getTitle());

        // 2. Xử lý Date (tránh null hoặc chuỗi "null")
        String safeDate = (item.getDate() == null || item.getDate().trim().isEmpty() || "null".equalsIgnoreCase(item.getDate()))
                ? "Không" : item.getDate();

        // 3. Xử lý Price (Format tiền tệ)
        if (item.getPrice() != null && item.getPrice() > 0) {
            String formatted = NumberFormat.getNumberInstance(Locale.getDefault()).format(item.getPrice()) + " đ";
            holder.txtPrice.setText(formatted);
        } else {
            holder.txtPrice.setText(R.string.contribution_text);
        }

        // 4. Logic hiển thị riêng cho Admin/User
        if (isAdmin) {
            // ADMIN VIEW
            if (item.getTotalRooms() > 0) {
                holder.txtDate.setText(
                        "Hạn đóng: " + safeDate +
                                "  •  Đã thu " + item.getPaidRooms() + "/" + item.getTotalRooms() + " phòng"
                );
            } else {
                holder.txtDate.setText("Hạn đóng: " + safeDate);
            }
            holder.itemView.setAlpha(1.0f);

        } else {
            // USER VIEW
            holder.txtDate.setText("Hạn đóng: " + safeDate);
            // Làm mờ nếu đã thanh toán
            if ("da_thanh_toan".equalsIgnoreCase(item.getStatus())) {
                holder.itemView.setAlpha(0.6f);
            } else {
                holder.itemView.setAlpha(1.0f);
            }
        }

        // 5. Xử lý sự kiện Click
        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            FinanceItem clickedItem = financeList.get(currentPosition);
            String clickedDate = (clickedItem.getDate() == null || clickedItem.getDate().trim().isEmpty() || "null".equalsIgnoreCase(clickedItem.getDate()))
                    ? "Không" : clickedItem.getDate();

            if (isAdmin) {
                // -> Mở chi tiết Admin
                Intent intent = new Intent(v.getContext(), FinanceDetailActivity_Admin.class);
                intent.putExtra("finance_id", clickedItem.getId());
                intent.putExtra("title", clickedItem.getTitle());
                intent.putExtra("due_date", clickedDate);
                v.getContext().startActivity(intent);

            } else {
                // -> Mở chi tiết User
                Intent intent = new Intent(v.getContext(), FinanceDetailActivity.class);
                intent.putExtra("financeId", clickedItem.getId());
                intent.putExtra("title", clickedItem.getTitle());
                intent.putExtra("content", clickedItem.getContent());
                intent.putExtra("due_date", clickedDate);
                intent.putExtra("type", clickedItem.getType());
                intent.putExtra("sender", clickedItem.getSender());
                long priceValue = (clickedItem.getPrice() != null) ? clickedItem.getPrice() : 0L;
                intent.putExtra("price", priceValue);
                intent.putExtra("payment_status", clickedItem.getStatus());
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return financeList.size();
    }

    // --- LOGIC LỌC DỮ LIỆU (SEARCH) ---
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
        @SuppressWarnings("unchecked") // 🔥 Fix cảnh báo "unchecked cast"
        protected void publishResults(CharSequence constraint, FilterResults results) {
            financeList.clear();
            if (results.values != null) {
                financeList.addAll((List<FinanceItem>) results.values);
            }
            notifyDataSetChanged();
        }
    };

    // --- CÁC HÀM CẬP NHẬT DỮ LIỆU ---

    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<FinanceItem> newList) {
        if (newList == null) return; // 🔥 Fix NullPointer
        financeListFull.clear();
        financeListFull.addAll(newList);
        financeList.clear();
        financeList.addAll(newList);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filterByType(String type) {
        List<FinanceItem> filteredList = new ArrayList<>();
        if (type == null || "Tất cả".equalsIgnoreCase(type)) {
            filteredList.addAll(financeListFull);
        } else if ("Bắt buộc".equalsIgnoreCase(type)) {
            // 🔥 Hiển thị tất cả các khoản thu KHÔNG phải "Tự nguyện"
            for (FinanceItem item : financeListFull) {
                if (item.getType() != null && !item.getType().equalsIgnoreCase("Tự nguyện")) {
                    filteredList.add(item);
                }
            }
        } else if ("Tự nguyện".equalsIgnoreCase(type)) {
            // 🔥 Hiển thị chỉ các khoản thu "Tự nguyện"
            for (FinanceItem item : financeListFull) {
                if (item.getType() != null && item.getType().equalsIgnoreCase("Tự nguyện")) {
                    filteredList.add(item);
                }
            }
        } else {
            // Fallback: So sánh trực tiếp với type (cho các filter khác nếu có)
            for (FinanceItem item : financeListFull) {
                if (item.getType() != null && item.getType().equalsIgnoreCase(type)) {
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