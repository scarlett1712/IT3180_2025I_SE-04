package com.se_04.enoti.feedback.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager; // Cần thiết để lấy Token và Admin ID

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class FeedbackReplyBottomSheet_Admin extends BottomSheetDialogFragment {

    private int feedbackId;
    private String originalContent;
    // 🔥 Cập nhật tên biến để khớp với XML mới
    private TextView txtFeedbackContent;
    private EditText edtReplyContent;
    private MaterialButton btnSendReply;

    private RequestQueue queue;

    public static FeedbackReplyBottomSheet_Admin newInstance(int feedbackId, String originalContent) {
        FeedbackReplyBottomSheet_Admin fragment = new FeedbackReplyBottomSheet_Admin();
        Bundle args = new Bundle();
        args.putInt("feedback_id", feedbackId);
        args.putString("original_content", originalContent);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // ✅ Giả định layout mới của bạn tên là activity_feedback_reply.xml
        return inflater.inflate(R.layout.fragment_reply_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        queue = Volley.newRequestQueue(requireContext());

        // 🔥 Mapping Views với ID mới trong layout
        // TextView txtTitle = view.findViewById(R.id.feedback_title); // Có thể không cần dùng
        txtFeedbackContent = view.findViewById(R.id.feedback_content);
        edtReplyContent = view.findViewById(R.id.edt_reply_content);
        btnSendReply = view.findViewById(R.id.btn_send_reply);

        // --- Lấy dữ liệu ---
        if (getArguments() != null) {
            feedbackId = getArguments().getInt("feedback_id", -1);
            originalContent = getArguments().getString("original_content");
        }

        // Hiển thị nội dung feedback gốc
        txtFeedbackContent.setText(originalContent != null ? originalContent : "(Không có nội dung)");

        // Lắng nghe sự kiện
        btnSendReply.setOnClickListener(v -> sendReply());
    }

    private void sendReply() {
        // Lấy nội dung từ biến đã đổi tên
        String replyText = edtReplyContent.getText().toString().trim();
        if (replyText.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập nội dung phản hồi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lấy Token và User ID
        UserManager userManager = UserManager.getInstance(requireContext());
        String adminId = userManager.getCurrentUser() != null ? userManager.getCurrentUser().getId() : "1";
        String token = userManager.getAuthToken();


        String url = ApiConfig.BASE_URL + "/api/feedback/" + feedbackId + "/reply";
        JSONObject body = new JSONObject();
        try {
            // 🔥 Sử dụng ID admin động
            body.put("admin_id", Integer.parseInt(adminId));
            body.put("reply_content", replyText);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(requireContext(), "Phản hồi đã được gửi!", Toast.LENGTH_SHORT).show();
                    // Nếu cần refresh màn hình cha, bạn có thể dùng LocalBroadcastManager tại đây
                    dismiss();
                },
                error -> {
                    Log.e("ReplyFeedback", "Error: " + error);
                    // Xử lý lỗi 401 nếu cần
                    Toast.makeText(requireContext(), "Không thể gửi phản hồi", Toast.LENGTH_SHORT).show();
                })
        {
            // 🔥 Ghi đè getHeaders để gửi Token (Bảo mật Admin API)
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        queue.add(request);
    }
}