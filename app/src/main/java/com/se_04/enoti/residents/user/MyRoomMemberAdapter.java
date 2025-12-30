package com.se_04.enoti.residents.user;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.se_04.enoti.R;

import org.json.JSONObject;

import java.util.List;

public class MyRoomMemberAdapter extends RecyclerView.Adapter<MyRoomMemberAdapter.ViewHolder> {

    private List<JSONObject> members;
    private int currentUserId;
    private Context context;

    public MyRoomMemberAdapter(Context context, List<JSONObject> members, int currentUserId) {
        this.context = context;
        this.members = members;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject member = members.get(position);

        String fullName = member.optString("full_name", "Không tên");
        String dob = member.optString("dob", "");
        String phone = member.optString("phone", "");
        String job = member.optString("job", "");
        String relation = member.optString("relationship", "");
        boolean isHead = member.optBoolean("is_head_of_household", false);
        int userId = member.optInt("user_id");

        // 1. Tên
        holder.tvName.setText(fullName);

        // 2. Logic highlight (Chủ hộ / Bản thân)
        holder.tvTag.setVisibility(View.GONE);
        holder.tvName.setTextColor(Color.BLACK);

        if (userId == currentUserId) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText("Tôi");
            holder.tvTag.setBackgroundResource(R.drawable.bg_badge_blue); // Cần tạo drawable hoặc set màu code
            holder.tvName.setTextColor(context.getResources().getColor(R.color.brand_start)); // Màu chủ đạo app
        } else if (isHead) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText("Chủ hộ");
            // holder.tvTag.setBackgroundColor(Color.parseColor("#FF9800")); // Màu cam
        }

        // 3. Quan hệ
        if (isHead) {
            holder.tvRelation.setText("Vai trò: Chủ hộ");
            holder.tvRelation.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.tvRelation.setText("Quan hệ: " + relation);
            holder.tvRelation.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        // 4. Thông tin phụ (SĐT / Job / Ngày sinh)
        StringBuilder info = new StringBuilder();
        if (!dob.isEmpty()) info.append(dob);
        if (!phone.isEmpty() && !phone.equals("null")) {
            if (info.length() > 0) info.append("  •  ");
            info.append(phone);
        }
        if (!job.isEmpty() && !job.equals("null") && !job.equals("Không")) {
            if (info.length() > 0) info.append("\n");
            info.append(job);
        }

        holder.tvInfo.setText(info.toString());
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTag, tvRelation, tvInfo;
        ImageView imgAvatar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvTag = itemView.findViewById(R.id.tvTag);
            tvRelation = itemView.findViewById(R.id.tvRelation);
            tvInfo = itemView.findViewById(R.id.tvInfo);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
        }
    }
}