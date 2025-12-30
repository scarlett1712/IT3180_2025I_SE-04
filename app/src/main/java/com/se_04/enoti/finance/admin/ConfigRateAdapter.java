package com.se_04.enoti.finance.admin;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.util.List;
import java.util.Locale;

public class ConfigRateAdapter extends RecyclerView.Adapter<ConfigRateAdapter.ViewHolder> {

    private List<RateItem> list;
    private String currentType; // electricity, water, management_fee, service_fee

    // Constructor mới: nhận thêm currentType
    public ConfigRateAdapter(List<RateItem> list, String currentType) {
        this.list = list;
        this.currentType = currentType;
    }

    // Method để cập nhật type khi người dùng chuyển tab (gọi từ Activity)
    public void updateType(String newType) {
        this.currentType = newType;
        notifyDataSetChanged(); // Cập nhật toàn bộ giao diện
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rate_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RateItem item = list.get(position);

        boolean isFixedFee = "management_fee".equals(currentType) || "service_fee".equals(currentType);

        if (isFixedFee) {
            // === PHÍ QUẢN LÝ / PHÍ DỊCH VỤ: chỉ 1 bậc, tính theo m² ===
            String feeName = "management_fee".equals(currentType) ? "Phí quản lý" : "Phí dịch vụ";
            holder.txtName.setText(feeName + " (theo m²)");

            // Hiển thị giá đầy đủ, có số lẻ nếu cần
            holder.edtPrice.setText(String.format(Locale.US, "%.0f", item.getPrice()));
            holder.edtPrice.setHint("VNĐ/m²");

        } else {
            // === ĐIỆN / NƯỚC: bậc thang ===
            String range = (item.getMax() == null || item.getMax() == 0)
                    ? "trở lên"
                    : String.valueOf(item.getMax());

            String unit = currentType.equals("electricity") ? "kWh" : "m³";

            holder.txtName.setText(String.format(Locale.US, "%s (%d - %s %s)",
                    item.getName(),
                    item.getMin(),
                    range,
                    unit));

            holder.edtPrice.setText(String.format(Locale.US, "%.0f", item.getPrice()));
            holder.edtPrice.setHint("VNĐ/" + unit);
        }

        // Xử lý nhập giá - hỗ trợ số thực (có thể có chữ số thập phân sau này)
        if (holder.watcher != null) {
            holder.edtPrice.removeTextChangedListener(holder.watcher);
        }

        holder.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (input.isEmpty()) {
                    item.setPrice(0.0);
                } else {
                    try {
                        item.setPrice(Double.parseDouble(input));
                    } catch (NumberFormatException e) {
                        item.setPrice(0.0);
                    }
                }
            }
        };

        holder.edtPrice.addTextChangedListener(holder.watcher);
    }

    public List<RateItem> getList() {
        return list;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName;
        EditText edtPrice;
        TextWatcher watcher;

        ViewHolder(View v) {
            super(v);
            txtName = v.findViewById(R.id.txtTierName);
            edtPrice = v.findViewById(R.id.edtPrice);
        }
    }
}