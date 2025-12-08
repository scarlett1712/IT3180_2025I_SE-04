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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_utility_input, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UtilityInputItem item = list.get(position);
        holder.txtRoom.setText(item.getRoomNumber());

        // 1. Xóa Listener cũ để tránh loop vô tận khi tái sử dụng view
        if (holder.oldWatcher != null) holder.edtOld.removeTextChangedListener(holder.oldWatcher);
        if (holder.newWatcher != null) holder.edtNew.removeTextChangedListener(holder.newWatcher);

        // 2. Set giá trị hiện tại từ Model
        holder.edtOld.setText(item.getOldIndex());
        holder.edtNew.setText(item.getNewIndex());

        // 3. Tạo Listener mới để lưu dữ liệu khi nhập
        holder.oldWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                item.setOldIndex(s.toString()); // Lưu vào model
            }
        };
        holder.edtOld.addTextChangedListener(holder.oldWatcher);

        holder.newWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                item.setNewIndex(s.toString()); // Lưu vào model
            }
        };
        holder.edtNew.addTextChangedListener(holder.newWatcher);
    }

    public List<UtilityInputItem> getList() { return list; }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtRoom;
        EditText edtOld, edtNew;
        TextWatcher oldWatcher, newWatcher; // Giữ tham chiếu để remove

        ViewHolder(View itemView) {
            super(itemView);
            txtRoom = itemView.findViewById(R.id.txtRoomNumber);
            edtOld = itemView.findViewById(R.id.edtOld);
            edtNew = itemView.findViewById(R.id.edtNew);
        }
    }
}