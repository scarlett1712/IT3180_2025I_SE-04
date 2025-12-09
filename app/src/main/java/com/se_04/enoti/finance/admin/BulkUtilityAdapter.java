package com.se_04.enoti.finance.admin;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import java.util.List;

public class BulkUtilityAdapter extends RecyclerView.Adapter<BulkUtilityAdapter.ViewHolder> {

    private List<UtilityInputItem> list;

    public BulkUtilityAdapter(List<UtilityInputItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 🔥 Đổi layout thành item_bulk_meter
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bulk_meter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UtilityInputItem item = list.get(position);

        // 1. Gán dữ liệu hiển thị (Phòng & Chỉ số cũ)
        holder.txtRoom.setText(item.getRoomNumber());
        holder.txtOld.setText(item.getOldIndex()); // Chỉ số cũ là TextView, không cần Watcher

        // 2. Xử lý ô nhập liệu (Chỉ số mới)
        // Xóa Listener cũ để tránh lỗi khi tái sử dụng View trong RecyclerView
        if (holder.newWatcher != null) {
            holder.edtNew.removeTextChangedListener(holder.newWatcher);
        }

        // Set giá trị hiện tại (quan trọng khi cuộn lên/xuống)
        holder.edtNew.setText(item.getNewIndex());

        // Tạo Listener mới để lưu dữ liệu khi người dùng nhập
        holder.newWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                item.setNewIndex(s.toString()); // Lưu giá trị vào Model
            }
        };
        holder.edtNew.addTextChangedListener(holder.newWatcher);
    }

    public List<UtilityInputItem> getList() { return list; }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtRoom;
        TextView txtOld; // 🔥 Đổi thành TextView (Read-only)
        EditText edtNew;
        TextWatcher newWatcher; // Chỉ cần watcher cho chỉ số mới

        ViewHolder(View itemView) {
            super(itemView);
            // Ánh xạ đúng ID trong file item_bulk_meter.xml
            txtRoom = itemView.findViewById(R.id.txtRoomName);
            txtOld = itemView.findViewById(R.id.txtOldIndex);
            edtNew = itemView.findViewById(R.id.edtNewIndex);
        }
    }
}