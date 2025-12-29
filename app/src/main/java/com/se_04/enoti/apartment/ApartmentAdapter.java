package com.se_04.enoti.apartment;

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
        holder.txtName.setText("Phòng " + item.getApartmentNumber());
        holder.txtDetails.setText("Tầng: " + item.getFloor() + "  |  DT: " + item.getArea() + "m²");

        if ("occupied".equals(item.getStatus())) {
            holder.txtStatus.setText("Đã có người");
            holder.txtStatus.setTextColor(0xFFFF5722); // Màu cam
        } else {
            holder.txtStatus.setText("Trống");
            holder.txtStatus.setTextColor(0xFF4CAF50); // Màu xanh
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtDetails, txtStatus;
        ViewHolder(View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtRoomName);
            txtDetails = itemView.findViewById(R.id.txtDetails);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }
}