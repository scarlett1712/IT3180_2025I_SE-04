package com.se_04.enoti.apartment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Đừng quên import ImageView
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentItem;

import java.util.ArrayList;
import java.util.List;

public class SelectResidentAdapter extends RecyclerView.Adapter<SelectResidentAdapter.ViewHolder> {

    private List<ResidentItem> originalList; // Danh sách gốc
    private List<ResidentItem> filteredList; // Danh sách đang hiển thị
    private OnAddClickListener listener;

    public interface OnAddClickListener {
        void onAdd(ResidentItem item);
    }

    public SelectResidentAdapter(List<ResidentItem> list, OnAddClickListener listener) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
        this.listener = listener;
    }

    // Hàm lọc dữ liệu (Search)
    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (ResidentItem item : originalList) {
                // Tìm theo tên hoặc ID hoặc SĐT
                if (item.getName().toLowerCase().contains(lowerQuery) ||
                        String.valueOf(item.getUserId()).contains(lowerQuery)) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_resident, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResidentItem item = filteredList.get(position);

        holder.tvName.setText(item.getName());

        String phone = (item.getPhone() == null || item.getPhone().isEmpty()) ? "Không có SĐT" : item.getPhone();
        holder.tvInfo.setText("ID: " + item.getUserId() + " | " + phone);

        // Sự kiện click vào cả dòng
        holder.itemView.setOnClickListener(v -> listener.onAdd(item));

        // Sự kiện click vào nút Add (đã khai báo trong ViewHolder)
        holder.btnAdd.setOnClickListener(v -> listener.onAdd(item));
    }

    @Override
    public int getItemCount() { return filteredList.size(); }

    // ==================================================================
    // 🔥 SỬA LỖI Ở ĐÂY: Khai báo btnAdd trong ViewHolder
    // ==================================================================
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvInfo;
        ImageView btnAdd; // Thêm biến này

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            btnAdd = itemView.findViewById(R.id.btnAdd); // Ánh xạ ở đây
        }
    }
}