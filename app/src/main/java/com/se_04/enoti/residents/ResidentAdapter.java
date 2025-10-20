package com.se_04.enoti.residents;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;
import java.util.List;

public class ResidentAdapter extends RecyclerView.Adapter<ResidentAdapter.ViewHolder> {

    private List<ResidentItem> residentList;

    public ResidentAdapter(List<ResidentItem> residentList) {
        this.residentList = residentList;
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
    }

    @Override
    public int getItemCount() {
        return residentList.size();
    }

    public void updateList(List<ResidentItem> newList) {
        this.residentList = newList;
        notifyDataSetChanged();
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
