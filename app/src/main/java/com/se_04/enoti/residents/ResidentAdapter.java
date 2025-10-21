package com.se_04.enoti.residents;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResidentAdapter extends RecyclerView.Adapter<ResidentAdapter.ViewHolder> {

    public static final int MODE_VIEW_DETAIL = 0;
    public static final int MODE_SELECT_FOR_NOTIFICATION = 1;

    private List<ResidentItem> residentList;
    private Set<ResidentItem> selectedResidents = new HashSet<>();
    private OnResidentSelectListener listener;
    private int mode;

    public interface OnResidentSelectListener {
        void onSelectionChanged(Set<ResidentItem> selectedResidents);
    }

    public ResidentAdapter(List<ResidentItem> residentList, int mode, OnResidentSelectListener listener) {
        this.residentList = residentList;
        this.mode = mode;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResidentAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resident, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResidentAdapter.ViewHolder holder, int position) {
        ResidentItem resident = residentList.get(position);
        holder.txtResidentName.setText(resident.getName());
        holder.txtResidentInfo.setText(resident.getRoom());

        boolean isSelected = selectedResidents.contains(resident);
        holder.itemView.setBackgroundColor(isSelected ? Color.parseColor("#D6EAF8") : Color.WHITE);

        // 🔹 Tùy hành vi theo mode
        if (mode == MODE_VIEW_DETAIL) {
            // 👉 1 chạm để mở chi tiết
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ResidentDetailActivity.class);
                intent.putExtra("name", resident.getName());
                intent.putExtra("gender", resident.getGender());
                intent.putExtra("dob", resident.getDob());
                intent.putExtra("email", resident.getEmail());
                intent.putExtra("phone", resident.getPhone());
                intent.putExtra("relationship", resident.getRelationship());
                intent.putExtra("role", resident.getRole());
                intent.putExtra("familyID", resident.getFamilyId());
                intent.putExtra("isLiving", resident.isLiving());
                v.getContext().startActivity(intent);
            });
        } else if (mode == MODE_SELECT_FOR_NOTIFICATION) {
            // 👉 1 chạm để chọn / bỏ chọn
            holder.itemView.setOnClickListener(v -> {
                if (isSelected) selectedResidents.remove(resident);
                else selectedResidents.add(resident);

                notifyItemChanged(position);
                if (listener != null) listener.onSelectionChanged(selectedResidents);
            });
        }
    }

    @Override
    public int getItemCount() {
        return residentList.size();
    }

    public void updateList(List<ResidentItem> newList) {
        this.residentList = newList;
        notifyDataSetChanged();
    }

    public Set<ResidentItem> getSelectedResidents() {
        return selectedResidents;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtResidentName, txtResidentInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtResidentName = itemView.findViewById(R.id.txtName);
            txtResidentInfo = itemView.findViewById(R.id.txtInfo);
        }
    }
}
