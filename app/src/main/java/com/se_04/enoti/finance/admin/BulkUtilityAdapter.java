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
    private boolean isInputMode = true; // true = Điện/Nước (cần nhập chỉ số), false = Phí cố định

    public BulkUtilityAdapter(List<UtilityInputItem> list) {
        this.list = list;
    }

    // 🔥 Method để Activity gọi khi chuyển tab dịch vụ
    public void setInputMode(boolean enableInput) {
        this.isInputMode = enableInput;
        notifyDataSetChanged(); // Cập nhật toàn bộ giao diện
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bulk_meter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UtilityInputItem item = list.get(position);

        // 1. Luôn hiển thị tên phòng
        holder.txtRoom.setText(item.getRoomNumber());

        if (isInputMode) {
            // === CHẾ ĐỘ ĐIỆN / NƯỚC: Hiển thị chỉ số cũ + ô nhập mới ===
            holder.txtOld.setVisibility(View.VISIBLE);
            holder.edtNew.setVisibility(View.VISIBLE);

            holder.txtOld.setText(item.getOldIndex().isEmpty() ? "0" : item.getOldIndex());

            // Xử lý EditText mới
            if (holder.newWatcher != null) {
                holder.edtNew.removeTextChangedListener(holder.newWatcher);
            }

            holder.edtNew.setText(item.getNewIndex());

            holder.newWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    item.setNewIndex(s.toString().trim());
                }
            };
            holder.edtNew.addTextChangedListener(holder.newWatcher);

            // Hint rõ ràng
            holder.edtNew.setHint("Nhập chỉ số mới");

        } else {
            // === CHẾ ĐỘ PHÍ QUẢN LÝ / DỊCH VỤ: Ẩn input, chỉ hiện thông báo ===
            holder.txtOld.setVisibility(View.GONE);
            holder.edtNew.setVisibility(View.GONE);

            // Có thể dùng txtOld để hiện thông báo (tái sử dụng view)
            holder.txtOld.setVisibility(View.VISIBLE);
            holder.txtOld.setText("Sẵn sàng chốt phí");
            holder.txtOld.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.darker_gray));
        }
    }

    public List<UtilityInputItem> getList() {
        return list;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtRoom;
        TextView txtOld;
        EditText edtNew;
        TextWatcher newWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            txtRoom = itemView.findViewById(R.id.txtRoomName);
            txtOld = itemView.findViewById(R.id.txtOldIndex);
            edtNew = itemView.findViewById(R.id.edtNewIndex);
        }
    }
}