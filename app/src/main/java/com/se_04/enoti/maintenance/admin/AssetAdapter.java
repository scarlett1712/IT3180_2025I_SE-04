package com.se_04.enoti.maintenance.admin; // Đảm bảo package này đúng với cấu trúc thư mục của bạn

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import com.se_04.enoti.maintenance.AssetItem;

import java.util.List;

public class AssetAdapter extends RecyclerView.Adapter<AssetAdapter.ViewHolder> {

    private List<AssetItem> list;
    private OnItemClickListener listener;

    // 🔥 Interface để bắt sự kiện click (Activity/Fragment sẽ implement cái này)
    public interface OnItemClickListener {
        void onItemClick(AssetItem item);
    }

    // Hàm để set listener từ bên ngoài
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public AssetAdapter(List<AssetItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout custom item_asset.xml
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asset, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AssetItem item = list.get(position);

        // Gán dữ liệu
        holder.txtName.setText(item.getName());
        holder.txtLocation.setText("📍 " + item.getLocation());

        // Xử lý trạng thái và màu sắc
        String status = item.getStatus();
        if ("Good".equalsIgnoreCase(status)) {
            holder.txtStatus.setText("Hoạt động tốt");
            holder.txtStatus.setTextColor(Color.parseColor("#388E3C")); // Xanh đậm
            holder.txtStatus.setBackgroundColor(Color.parseColor("#E8F5E9")); // Nền xanh nhạt
        } else {
            holder.txtStatus.setText("Đang bảo trì");
            holder.txtStatus.setTextColor(Color.parseColor("#D32F2F")); // Đỏ đậm
            holder.txtStatus.setBackgroundColor(Color.parseColor("#FFEBEE")); // Nền đỏ nhạt
        }

        // Bắt sự kiện click vào toàn bộ item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    // ViewHolder ánh xạ các view trong item_asset.xml
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtLocation, txtStatus;
        ImageView imgIcon;

        ViewHolder(View itemView) {
            super(itemView);
            // Các ID này phải khớp với file res/layout/item_asset.xml
            txtName = itemView.findViewById(R.id.txtAssetName);
            txtLocation = itemView.findViewById(R.id.txtAssetLocation);
            txtStatus = itemView.findViewById(R.id.txtAssetStatus);
            imgIcon = itemView.findViewById(R.id.imgIcon);
        }
    }
}