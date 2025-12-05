package com.se_04.enoti.account.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.se_04.enoti.R;
import java.util.List;

public class ApproveRequestsAdapter extends RecyclerView.Adapter<ApproveRequestsAdapter.ViewHolder> {

    private List<ProfileRequestItem> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ProfileRequestItem item);
    }

    public ApproveRequestsAdapter(List<ProfileRequestItem> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProfileRequestItem item = list.get(position);

        // Hiển thị tên người gửi (ưu tiên tên cũ để dễ nhận diện)
        String name = (item.getCurrentName() != null && !item.getCurrentName().isEmpty())
                ? item.getCurrentName()
                : "Không rõ";
        holder.txtRequestUser.setText("Người gửi: " + name);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtRequestUser;
        ViewHolder(View itemView) {
            super(itemView);
            txtRequestUser = itemView.findViewById(R.id.txtRequestUser);
        }
    }
}