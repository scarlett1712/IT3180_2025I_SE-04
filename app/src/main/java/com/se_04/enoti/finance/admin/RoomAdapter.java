package com.se_04.enoti.finance.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;

import java.util.*;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private List<String> roomList;
    private Set<String> selectedRooms = new HashSet<>();
    private final OnSelectionChangedListener listener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selected);
    }

    public RoomAdapter(List<String> roomList, OnSelectionChangedListener listener) {
        this.roomList = roomList;
        this.listener = listener;
    }

    // Cập nhật danh sách phòng mới (khi chọn tầng)
    public void updateRooms(List<String> newRooms) {
        this.roomList = newRooms;
        // Khi load list mới thì clear selection cũ đi để tránh lỗi data ảo
        // Hoặc giữ lại nếu bạn muốn tính năng "nhớ" lựa chọn qua các tầng
        selectedRooms.clear();
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionChanged(selectedRooms);
        }
    }

    // 🔥 HÀM MỚI: Được gọi từ Activity khi bấm Checkbox "Chọn tất cả"
    public void selectAll(boolean isSelected) {
        selectedRooms.clear();
        if (isSelected) {
            selectedRooms.addAll(roomList);
        }
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionChanged(selectedRooms);
        }
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout item phòng đơn giản
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_select, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        String room = roomList.get(position);

        holder.txtRoom.setText(room);

        // Xóa listener cũ trước khi set trạng thái để tránh trigger loop
        holder.checkBoxRoom.setOnCheckedChangeListener(null);

        // Set trạng thái check dựa trên Set
        holder.checkBoxRoom.setChecked(selectedRooms.contains(room));

        // Gán listener mới
        holder.checkBoxRoom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedRooms.add(room);
            } else {
                selectedRooms.remove(room);
            }

            if (listener != null) {
                listener.onSelectionChanged(selectedRooms);
            }
        });

        // Cho phép bấm vào cả item để check (tăng trải nghiệm UX)
        holder.itemView.setOnClickListener(v -> {
            holder.checkBoxRoom.toggle();
        });
    }

    @Override
    public int getItemCount() {
        return roomList != null ? roomList.size() : 0;
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView txtRoom;
        CheckBox checkBoxRoom;

        RoomViewHolder(View itemView) {
            super(itemView);
            txtRoom = itemView.findViewById(R.id.txtRoom);
            checkBoxRoom = itemView.findViewById(R.id.checkboxRoom);
        }
    }
}