package com.se_04.enoti.apartment; // Đổi package nếu cần

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import java.util.List;

public class ApartmentAdapter extends RecyclerView.Adapter<ApartmentAdapter.ViewHolder> {

    private List<Apartment> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Apartment apartment);
        void onItemLongClick(Apartment apartment);
    }

    public ApartmentAdapter(List<Apartment> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_apartment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Apartment item = list.get(position);

        // 1. Hiển thị số phòng
        holder.txtName.setText("Phòng " + item.getApartmentNumber());

        // 2. Hiển thị chi tiết (Tầng | Diện tích)
        holder.txtDetails.setText("Tầng " + item.getFloor() + " | " + item.getArea() + "m²");

        // 3. 🔥 CẬP NHẬT TRẠNG THÁI (STATUS)
        String status = item.getStatus(); // Lấy từ DB (ví dụ: "Occupied", "trong", "null")

        // Kiểm tra null để tránh lỗi
        if (status != null && (status.equalsIgnoreCase("Occupied") || status.equalsIgnoreCase("da_co_nguoi"))) {
            // TRƯỜNG HỢP: ĐÃ CÓ NGƯỜI
            holder.txtStatus.setText("Đã có người");
            holder.txtStatus.setTextColor(Color.parseColor("#F44336")); // Màu Đỏ
            // Hoặc màu cam: "#FF9800"
        } else {
            // TRƯỜNG HỢP: TRỐNG (bao gồm null, "", "trong", "Empty")
            holder.txtStatus.setText("Trống");
            holder.txtStatus.setTextColor(Color.parseColor("#4CAF50")); // Màu Xanh lá (Giống trong XML)
        }

        // Sự kiện Click
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtDetails, txtStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ ID từ file XML bạn cung cấp
            txtName = itemView.findViewById(R.id.txtRoomName);
            txtDetails = itemView.findViewById(R.id.txtDetails);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }
}