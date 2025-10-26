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

public class RoomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SELECT_ALL = 0;
    private static final int VIEW_TYPE_ROOM = 1;

    private List<String> roomList;
    private Set<String> selectedRooms = new HashSet<>();
    private final OnSelectionChangedListener listener;
    private boolean isSelectAllChecked = false;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selected);
    }

    public RoomAdapter(List<String> roomList, OnSelectionChangedListener listener) {
        this.roomList = roomList;
        this.listener = listener;
    }

    public void updateRooms(List<String> newRooms) {
        this.roomList = newRooms;
        selectedRooms.clear();
        isSelectAllChecked = false;
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedRooms);
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? VIEW_TYPE_SELECT_ALL : VIEW_TYPE_ROOM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SELECT_ALL) {
            View view = inflater.inflate(R.layout.item_select_all, parent, false);
            return new SelectAllViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_room_select, parent, false);
            return new RoomViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SELECT_ALL) {
            SelectAllViewHolder h = (SelectAllViewHolder) holder;
            h.checkBoxSelectAll.setOnCheckedChangeListener(null);
            h.checkBoxSelectAll.setChecked(isSelectAllChecked);
            h.checkBoxSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> toggleSelectAll(isChecked));
        } else {
            int realPos = position - 1;
            String room = roomList.get(realPos);
            RoomViewHolder h = (RoomViewHolder) holder;
            h.txtRoom.setText(room);
            h.checkBoxRoom.setOnCheckedChangeListener(null);
            h.checkBoxRoom.setChecked(selectedRooms.contains(room));

            h.checkBoxRoom.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedRooms.add(room);
                else selectedRooms.remove(room);
                updateSelectAllState();
                listener.onSelectionChanged(selectedRooms);
            });
        }
    }

    @Override
    public int getItemCount() {
        // +1 vì có thêm dòng "Chọn tất cả"
        return roomList.size() + 1;
    }

    private void toggleSelectAll(boolean selectAll) {
        isSelectAllChecked = selectAll;
        selectedRooms.clear();
        if (selectAll) selectedRooms.addAll(roomList);
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedRooms);
    }

    private void updateSelectAllState() {
        boolean newState = (selectedRooms.size() == roomList.size() && !roomList.isEmpty());
        if (newState != isSelectAllChecked) {
            isSelectAllChecked = newState;
            notifyItemChanged(0);
        }
    }

    static class SelectAllViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBoxSelectAll;
        SelectAllViewHolder(View itemView) {
            super(itemView);
            checkBoxSelectAll = itemView.findViewById(R.id.checkboxSelectAll);
        }
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
