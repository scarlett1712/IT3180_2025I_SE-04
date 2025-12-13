package com.se_04.enoti.maintenance.user;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.AssetHistoryItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssetHistoryAdapter extends RecyclerView.Adapter<AssetHistoryAdapter.ViewHolder> {

    private List<AssetHistoryItem> list;

    // Format đầu vào từ server (thường là yyyy-MM-dd HH:mm:ss)
    private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // Format đầu ra cho giao diện mới
    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private final SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public AssetHistoryAdapter(List<AssetHistoryItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 🔥 ĐỔI LAYOUT Ở ĐÂY: Sử dụng item_history_log mới tạo
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AssetHistoryItem item = list.get(position);

        // 1. Xử lý hiển thị Ngày/Giờ tách biệt
        try {
            // Giả sử item.getDate() trả về chuỗi "2025-12-20 14:30:00"
            Date date = inputFormat.parse(item.getDate());
            if (date != null) {
                holder.txtDate.setText(dateOnlyFormat.format(date)); // VD: 20/12
                holder.txtTime.setText(timeOnlyFormat.format(date)); // VD: 14:30
            } else {
                holder.txtDate.setText(item.getDate());
                holder.txtTime.setText("");
            }
        } catch (Exception e) {
            // Fallback nếu lỗi parse
            holder.txtDate.setText(item.getDate());
            holder.txtTime.setText("");
        }

        // 2. Hiển thị Hành động (Tiêu đề)
        holder.txtAction.setText(item.getAction());

        // 3. Hiển thị Người thực hiện
        holder.txtPerformer.setText("Thực hiện bởi: " + item.getPerformerName());

        // 4. Hiển thị Kết quả/Ghi chú (Nếu có)
        if (item.getResult() != null && !item.getResult().isEmpty()) {
            holder.txtResult.setVisibility(View.VISIBLE);
            holder.txtResult.setText(item.getResult());
        } else {
            holder.txtResult.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Khai báo các view trong layout item_history_log.xml
        TextView txtDate, txtTime;
        TextView txtAction, txtPerformer, txtResult;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            // Ánh xạ ID chuẩn theo file XML mới
            txtDate = itemView.findViewById(R.id.txtLogDate);
            txtTime = itemView.findViewById(R.id.txtLogTime);

            txtAction = itemView.findViewById(R.id.txtLogAction);
            txtPerformer = itemView.findViewById(R.id.txtLogPerformer);
            txtResult = itemView.findViewById(R.id.txtLogResult);
        }
    }
}