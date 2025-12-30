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
        // 🔥 QUAN TRỌNG: Lọc dữ liệu rác ngay khi khởi tạo
        this.roomList = filterInvalidRooms(roomList);
        this.listener = listener;
    }

    // Cập nhật danh sách phòng mới (Ví dụ khi chọn tầng khác)
    public void updateRooms(List<String> newRooms) {
        // 1. Lọc sạch dữ liệu đầu vào
        this.roomList = filterInvalidRooms(newRooms);

        // 2. Xóa các lựa chọn cũ để tránh lỗi logic
        selectedRooms.clear();

        // 3. Cập nhật giao diện
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionChanged(selectedRooms);
        }
    }

    // 🔥 HÀM HELPER: Lọc bỏ null, "null", rỗng, "Vô gia cư"
    private List<String> filterInvalidRooms(List<String> inputList) {
        List<String> cleanList = new ArrayList<>();
        if (inputList == null) return cleanList;

        for (String room : inputList) {
            if (isValidRoom(room)) {
                cleanList.add(room);
            }
        }
        return cleanList;
    }

    // Kiểm tra điều kiện hợp lệ của một phòng
    private boolean isValidRoom(String room) {
        return room != null
                && !room.trim().isEmpty()
                && !room.equalsIgnoreCase("null")
                && !room.equals("Vô gia cư"); // Chặn không cho tạo phí cho nhóm này
    }

    // Chọn tất cả / Bỏ chọn tất cả
    public void selectAll(boolean isSelected) {
        selectedRooms.clear();
        if (isSelected) {
            // Chỉ thêm những phòng đã được lọc sạch (roomList)
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_select, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        String room = roomList.get(position);

        holder.txtRoom.setText(room);

        // ⚠️ RẤT QUAN TRỌNG: Xóa listener cũ trước khi set trạng thái check
        // Nếu không làm bước này, khi RecyclerView cuộn, các item sẽ bị check loạn xạ
        holder.checkBoxRoom.setOnCheckedChangeListener(null);

        // Set trạng thái check dựa trên dữ liệu đã lưu
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

        // Cho phép bấm vào cả dòng (item) để check/uncheck cho tiện tay
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