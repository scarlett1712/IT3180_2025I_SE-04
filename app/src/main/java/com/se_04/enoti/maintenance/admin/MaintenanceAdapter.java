package com.se_04.enoti.maintenance.admin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.MaintenanceItem;

import java.util.List;

public class MaintenanceAdapter extends RecyclerView.Adapter<MaintenanceAdapter.ViewHolder> {

    private List<MaintenanceItem> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MaintenanceItem item);
    }

    public MaintenanceAdapter(List<MaintenanceItem> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_maintenance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MaintenanceItem item = list.get(position);

        holder.txtAssetName.setText(item.getAssetName());
        holder.txtLocation.setText("📍 " + item.getLocation());
        holder.txtDate.setText("📅 " + item.getScheduledDate());
        holder.txtStaff.setText("👤 KT: " + item.getStaffName());
        holder.txtDescription.setText(item.getDescription());

        // Xử lý màu sắc trạng thái
        String status = item.getStatus();
        holder.txtStatus.setText(status);

        if ("Completed".equalsIgnoreCase(status)) {
            holder.txtStatus.setBackgroundColor(Color.parseColor("#4CAF50")); // Xanh lá
        } else if ("Pending".equalsIgnoreCase(status)) {
            holder.txtStatus.setBackgroundColor(Color.parseColor("#FF9800")); // Cam
        } else {
            holder.txtStatus.setBackgroundColor(Color.GRAY);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtAssetName, txtLocation, txtDate, txtStatus, txtStaff, txtDescription;

        ViewHolder(View itemView) {
            super(itemView);
            txtAssetName = itemView.findViewById(R.id.txtAssetName);
            txtLocation = itemView.findViewById(R.id.txtLocation);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtStaff = itemView.findViewById(R.id.txtStaff);
            txtDescription = itemView.findViewById(R.id.txtDescription);
        }
    }
}