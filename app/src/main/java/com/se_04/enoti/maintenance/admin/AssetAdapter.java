package com.se_04.enoti.maintenance.admin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.AssetItem;

import java.util.List;

public class AssetAdapter extends RecyclerView.Adapter<AssetAdapter.ViewHolder> {

    private List<AssetItem> list;

    public AssetAdapter(List<AssetItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Tái sử dụng layout item_maintenance hoặc tạo mới item_asset.xml tương tự
        // Ở đây tôi dùng tạm layout mặc định để demo, bạn nên tạo layout riêng
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AssetItem item = list.get(position);
        holder.text1.setText(item.getName());

        String statusText = item.getStatus().equals("Good") ? "Tốt" : "Đang bảo trì";
        holder.text2.setText("📍 " + item.getLocation() + " | Trạng thái: " + statusText);

        if (!item.getStatus().equals("Good")) {
            holder.text1.setTextColor(Color.RED);
        } else {
            holder.text1.setTextColor(Color.BLACK);
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