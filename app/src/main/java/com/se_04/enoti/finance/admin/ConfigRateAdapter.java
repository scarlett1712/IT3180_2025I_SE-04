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

public class ConfigRateAdapter extends RecyclerView.Adapter<ConfigRateAdapter.ViewHolder> {
    private List<RateItem> list;

    public ConfigRateAdapter(List<RateItem> list) { this.list = list; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rate_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RateItem item = list.get(position);

        String range = (item.getMax() == null || item.getMax() == 0) ? "Trở lên" : item.getMax() + "";
        holder.txtName.setText(item.getName() + " (" + item.getMin() + " - " + range + ")");
        holder.edtPrice.setText(String.valueOf((int)item.getPrice()));

        // Xử lý nhập liệu
        if (holder.watcher != null) holder.edtPrice.removeTextChangedListener(holder.watcher);
        holder.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    item.setPrice(Double.parseDouble(s.toString()));
                } catch (Exception e) { item.setPrice(0); }
            }
        };
        holder.edtPrice.addTextChangedListener(holder.watcher);
    }

    public List<RateItem> getList() { return list; }
    @Override public int getItemCount() { return list.size(); }

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