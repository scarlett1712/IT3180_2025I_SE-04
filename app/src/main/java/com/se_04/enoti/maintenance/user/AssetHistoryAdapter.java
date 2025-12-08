package com.se_04.enoti.maintenance.user;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.AssetHistoryItem;
import java.util.List;

public class AssetHistoryAdapter extends RecyclerView.Adapter<AssetHistoryAdapter.ViewHolder> {

    private List<AssetHistoryItem> list;

    public AssetHistoryAdapter(List<AssetHistoryItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tái sử dụng layout item_asset_history.xml (Tạo đơn giản textview)
        // Hoặc dùng android.R.layout.simple_list_item_2 để test nhanh
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AssetHistoryItem item = list.get(position);

        String typeText = item.getType().equals("Maintenance") ? "[BẢO TRÌ]" : "[BẠN BÁO CÁO]";
        holder.text1.setText(typeText + " " + item.getDescription());

        holder.text2.setText("Ngày: " + item.getDate() + " | Trạng thái: " + item.getStatus());

        if (item.getType().equals("Maintenance")) {
            holder.text1.setTextColor(Color.parseColor("#1976D2")); // Xanh
        } else {
            holder.text1.setTextColor(Color.parseColor("#E65100")); // Cam
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}