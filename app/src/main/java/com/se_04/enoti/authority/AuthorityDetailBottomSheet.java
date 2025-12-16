package com.se_04.enoti.authority;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.se_04.enoti.R;

public class AuthorityDetailBottomSheet extends BottomSheetDialogFragment {

    private AuthorityMessage message;

    // Singleton pattern để truyền dữ liệu vào Fragment
    public static AuthorityDetailBottomSheet newInstance(AuthorityMessage message) {
        AuthorityDetailBottomSheet fragment = new AuthorityDetailBottomSheet();
        fragment.message = message;
        return fragment;
    }

    @Override
    public int getTheme() {
        // Sử dụng theme này để nền phía sau mờ đi và bo góc hoạt động tốt
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Quan trọng: Phải có layout này (xem mục 2 bên dưới)
        return inflater.inflate(R.layout.bottom_sheet_authority_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (message == null) {
            dismiss();
            return;
        }

        // Ánh xạ view
        TextView txtTitle = view.findViewById(R.id.txtDetailTitle);
        TextView txtSender = view.findViewById(R.id.txtSender);
        TextView txtDate = view.findViewById(R.id.txtDate);
        TextView txtContent = view.findViewById(R.id.txtDetailContent);
        TextView txtPriority = view.findViewById(R.id.txtPriority);
        MaterialCardView cardPriority = view.findViewById(R.id.cardPriority);
        MaterialButton btnAttachment = view.findViewById(R.id.btnAttachment);
        MaterialButton btnClose = view.findViewById(R.id.btnClose);

        // Gán dữ liệu (ĐÃ SỬA TÊN HÀM GETTER TẠI ĐÂY)
        txtTitle.setText(message.getTitle());
        txtSender.setText(message.getSenderName()); // Sửa từ getSender_name -> getSenderName
        txtDate.setText(formatDate(message.getCreatedAt())); // Sửa từ getCreated_at -> getCreatedAt
        txtContent.setText(message.getContent());

        // Xử lý Priority (Màu sắc)
        String priority = message.getPriority();
        txtPriority.setText(priority);
        if ("High".equalsIgnoreCase(priority) || "Urgent".equalsIgnoreCase(priority)) {
            cardPriority.setCardBackgroundColor(Color.parseColor("#FFEBEE")); // Đỏ nhạt
            txtPriority.setTextColor(Color.parseColor("#D32F2F")); // Đỏ đậm
        } else {
            cardPriority.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Xanh nhạt
            txtPriority.setTextColor(Color.parseColor("#1976D2")); // Xanh đậm
        }

        // Xử lý File đính kèm
        String attachmentUrl = message.getAttachmentUrl(); // Sửa từ getAttachment_url -> getAttachmentUrl
        if (attachmentUrl != null && !attachmentUrl.isEmpty() && !attachmentUrl.equals("null")) {
            btnAttachment.setVisibility(View.VISIBLE);
            btnAttachment.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(attachmentUrl));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnAttachment.setVisibility(View.GONE);
        }

        btnClose.setOnClickListener(v -> dismiss());
    }

    // Hàm format ngày tháng đơn giản
    private String formatDate(String rawDate) {
        if(rawDate == null) return "";
        // Cắt chuỗi ngày cho gọn (VD: 2025-12-12T10:00:00 -> 2025-12-12 10:00)
        return rawDate.replace("T", " ").substring(0, Math.min(rawDate.length(), 16));
    }
}