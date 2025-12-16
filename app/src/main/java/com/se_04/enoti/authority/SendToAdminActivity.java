package com.se_04.enoti.authority;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class SendToAdminActivity extends AppCompatActivity {

    private EditText etSenderName, etTitle, etContent;
    private Spinner spinnerCategory;
    private CheckBox cbUrgent;
    private Button btnSend;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_to_admin);

        etSenderName = findViewById(R.id.et_sender_name);
        etTitle = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);
        spinnerCategory = findViewById(R.id.spinner_category);
        cbUrgent = findViewById(R.id.cb_urgent);
        btnSend = findViewById(R.id.btn_send);

        requestQueue = Volley.newRequestQueue(this);

        setupSpinner();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.authority_message_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    private void sendMessage() {
        String senderName = etSenderName.getText().toString().trim();
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();
        String category = spinnerCategory.getSelectedItem().toString();
        String priority = cbUrgent.isChecked() ? "Urgent" : "Normal";

        if (senderName.isEmpty() || title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.BASE_URL + "/api/authority/messages";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("sender_name", senderName);
            requestBody.put("title", title);
            requestBody.put("content", content);
            requestBody.put("category", category);
            requestBody.put("priority", priority);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi tạo dữ liệu!", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                response -> {
                    Toast.makeText(SendToAdminActivity.this, "Gửi thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                },
                error -> {
                    // START: Detailed error handling
                    String errorMessage = getVolleyError(error);
                    Toast.makeText(SendToAdminActivity.this, "Lỗi: " + errorMessage, Toast.LENGTH_LONG).show();
                    Log.e("SendToAdmin", "Volley Error: " + error.toString());
                    // END: Detailed error handling
                }
        );

        requestQueue.add(jsonObjectRequest);
    }

    // START: Helper method for detailed Volley errors
    private String getVolleyError(VolleyError error) {
        if (error instanceof com.android.volley.TimeoutError) {
            return "Hết thời gian chờ. Máy chủ không phản hồi.";
        } else if (error instanceof com.android.volley.NoConnectionError) {
            return "Không có kết nối mạng. Vui lòng kiểm tra lại.";
        } else if (error instanceof com.android.volley.ServerError) {
            int statusCode = error.networkResponse != null ? error.networkResponse.statusCode : -1;
            return "Lỗi từ máy chủ (Mã: " + statusCode + "). Vui lòng thử lại sau.";
        } else if (error instanceof com.android.volley.NetworkError) {
            return "Lỗi mạng chung. Vui lòng kiểm tra kết nối.";
        } else if (error instanceof com.android.volley.ParseError) {
            return "Lỗi phân tích dữ liệu nhận được.";
        }
        return "Lỗi không xác định. Vui lòng thử lại.";
    }
    // END: Helper method
}
