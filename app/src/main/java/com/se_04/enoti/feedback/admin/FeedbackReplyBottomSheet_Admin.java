package com.se_04.enoti.feedback.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class FeedbackReplyBottomSheet_Admin extends BottomSheetDialogFragment {

    private int feedbackId;
    private String originalContent;
    private EditText edtReply;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_feedback_reply_admin, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        queue = Volley.newRequestQueue(requireContext());

        TextView txtTitle = view.findViewById(R.id.txtFeedbackReplyTitle);
        TextView txtOriginal = view.findViewById(R.id.txtOriginalFeedback);
        edtReply = view.findViewById(R.id.edtReplyContent);
        MaterialButton btnSend = view.findViewById(R.id.btnSendReply);

        if (getArguments() != null) {
            feedbackId = getArguments().getInt("feedback_id", -1);
            originalContent = getArguments().getString("original_content");
        }

        txtOriginal.setText(originalContent != null ? originalContent : "(Không có nội dung)");

        btnSend.setOnClickListener(v -> sendReply());
    }

    private void sendReply() {
        String replyText = edtReply.getText().toString().trim();
        if (replyText.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập nội dung phản hồi", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/feedback/" + feedbackId + "/reply";
        JSONObject body = new JSONObject();
        try {
            body.put("admin_id", 1); // ✅ Có thể lấy từ UserManager nếu có đăng nhập admin
            body.put("reply_content", replyText);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(requireContext(), "Phản hồi đã được gửi!", Toast.LENGTH_SHORT).show();
                    dismiss(); // đóng popup
                },
                error -> {
                    Log.e("ReplyFeedback", "Error: " + error);
                    Toast.makeText(requireContext(), "Không thể gửi phản hồi", Toast.LENGTH_SHORT).show();
                });

        queue.add(request);
    }
}
