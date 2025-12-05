package com.se_04.enoti.residents;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import java.io.File;
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

        // LOAD ẢNH AVATAR
        loadResidentAvatar(holder.imgResident, String.valueOf(resident.getUserId()), resident.getGender());

        boolean isSelected = selectedResidents.contains(resident);
        holder.itemView.setBackgroundColor(isSelected ? Color.parseColor("#D6EAF8") : Color.WHITE);

        if (mode == MODE_VIEW_DETAIL) {
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ResidentDetailActivity.class);
                intent.putExtra("name", resident.getName());
                intent.putExtra("gender", resident.getGender());
                intent.putExtra("dob", resident.getDob());
                intent.putExtra("email", resident.getEmail());
                intent.putExtra("phone", resident.getPhone());
                intent.putExtra("relationship", resident.getRelationship());
                intent.putExtra("room", resident.getRoom());
                intent.putExtra("is_living", resident.isLiving());
                intent.putExtra("user_id", resident.getUserId());

                // 🔥 Truyền thêm 2 trường mới sang màn hình chi tiết
                // (ResidentItem đã có getter getIdentityCard() và getHomeTown())
                intent.putExtra("identity_card", resident.getIdentityCard());
                intent.putExtra("home_town", resident.getHomeTown());

                v.getContext().startActivity(intent);
            });
        } else if (mode == MODE_SELECT_FOR_NOTIFICATION) {
            holder.itemView.setOnClickListener(v -> {
                if (isSelected) selectedResidents.remove(resident);
                else selectedResidents.add(resident);

                notifyItemChanged(position);
                if (listener != null) listener.onSelectionChanged(selectedResidents);
            });
        }
    }

    private void loadResidentAvatar(ImageView imageView, String userId, String gender) {
        try {
            File avatarFile = getAvatarFile(imageView.getContext(), userId);
            if (avatarFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                imageView.setImageBitmap(bitmap);
            } else {
                setDefaultAvatar(imageView, gender);
            }
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultAvatar(imageView, gender);
        }
    }

    private void setDefaultAvatar(ImageView imageView, String gender) {
        if (gender != null && gender.toLowerCase().contains("nữ")) {
            imageView.setImageResource(R.drawable.ic_person_female);
        } else {
            imageView.setImageResource(R.drawable.ic_person);
        }
    }

    private File getAvatarFile(android.content.Context context, String userId) {
        File avatarDir = new File(context.getFilesDir(), "avatars");
        return new File(avatarDir, userId + ".jpg");
    }

    @Override
    public int getItemCount() {
        return residentList.size();
    }

    public void updateList(List<ResidentItem> newList) {
        this.residentList = newList;
        notifyDataSetChanged();
    }

    // HÀM DÙNG BOOL CHO "CHỌN TẤT CẢ" VÀ "BỎ CHỌN"
    public void selectAllResidents(boolean selectAll) {
        if (mode == MODE_SELECT_FOR_NOTIFICATION) {
            selectedResidents.clear();
            if (selectAll && residentList != null) {
                selectedResidents.addAll(residentList);
            }
            notifyDataSetChanged();
            if (listener != null) listener.onSelectionChanged(selectedResidents);
        }
    }

    public Set<ResidentItem> getSelectedResidents() {
        return selectedResidents;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtResidentName, txtResidentInfo;
        ImageView imgResident;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtResidentName = itemView.findViewById(R.id.txtName);
            txtResidentInfo = itemView.findViewById(R.id.txtInfo);
            imgResident = itemView.findViewById(R.id.imgResident);
        }
    }
}