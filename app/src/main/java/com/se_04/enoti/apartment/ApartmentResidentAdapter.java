package com.se_04.enoti.apartment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import com.se_04.enoti.residents.ResidentItem; // Import đúng package của bạn
import java.util.List;

public class ApartmentResidentAdapter extends RecyclerView.Adapter<ApartmentResidentAdapter.ViewHolder> {

    private List<ResidentItem> list;
    private OnRemoveClickListener listener;

    public interface OnRemoveClickListener {
        void onRemove(ResidentItem resident);
    }

    public ApartmentResidentAdapter(List<ResidentItem> list, OnRemoveClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_apartment_resident, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ResidentItem item = list.get(position);
        holder.tvName.setText(item.getName());

        // Hiển thị quan hệ (Chủ hộ / Thành viên)
        String relation = item.getRelationship();
        if (relation == null || relation.equals("null") || relation.isEmpty()) {
            holder.tvRole.setText("Thành viên");
        } else {
            holder.tvRole.setText(relation);
        }

        holder.btnRemove.setOnClickListener(v -> listener.onRemove(item));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRole;
        ImageView btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvRole = itemView.findViewById(R.id.tvRole);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}