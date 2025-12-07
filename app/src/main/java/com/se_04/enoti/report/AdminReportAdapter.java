package com.se_04.enoti.report;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import java.util.List;

public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.ViewHolder> {

    private List<ReportItem> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ReportItem item);
    }

    public AdminReportAdapter(List<ReportItem> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportItem item = list.get(position);

        holder.txtAssetName.setText(item.getAssetName());
        holder.txtLocation.setText("📍 " + item.getLocation());
        holder.txtDescription.setText(item.getDescription());
        holder.txtReporter.setText("Người báo: " + item.getReporterName());
        holder.txtDate.setText(item.getDate());

        // 🔥 Xử lý màu sắc trạng thái chuyên nghiệp hơn
        String status = item.getStatus();
        holder.txtStatus.setText(status);

        if ("Pending".equalsIgnoreCase(status)) {
            holder.txtStatus.setTextColor(Color.parseColor("#D32F2F")); // Đỏ đậm
            holder.txtStatus.setText("Chờ xử lý");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_pending); // 🔥 Set Background

        } else if ("Processing".equalsIgnoreCase(status)) {
            holder.txtStatus.setTextColor(Color.parseColor("#1976D2")); // Xanh dương đậm
            holder.txtStatus.setText("Đang xử lý");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_processing); // 🔥 Set Background

        } else if ("Completed".equalsIgnoreCase(status)) {
            holder.txtStatus.setTextColor(Color.parseColor("#388E3C")); // Xanh lá đậm
            holder.txtStatus.setText("Hoàn thành");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_completed); // 🔥 Set Background

        } else {
            holder.txtStatus.setTextColor(Color.GRAY);
            holder.txtStatus.setText("Từ chối");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_rejected); // 🔥 Set Background
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtAssetName, txtLocation, txtStatus, txtDescription, txtReporter, txtDate;

        ViewHolder(View itemView) {
            super(itemView);
            txtAssetName = itemView.findViewById(R.id.txtAssetName);
            txtLocation = itemView.findViewById(R.id.txtLocation); // Nhớ thêm ID này vào XML
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtDescription = itemView.findViewById(R.id.txtDescription);
            txtReporter = itemView.findViewById(R.id.txtReporter);
            txtDate = itemView.findViewById(R.id.txtDate);
        }
    }
}