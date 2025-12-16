package com.se_04.enoti.authority;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.BaseActivity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AuthorityInboxActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private AuthorityMessageAdapter adapter;
    private List<AuthorityMessage> messageList = new ArrayList<>();
    private ProgressBar progressBar;
    private RequestQueue requestQueue;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authority_inbox);

        recyclerView = findViewById(R.id.recycler_view_messages);
        progressBar = findViewById(R.id.progress_bar);
        toolbar = findViewById(R.id.toolbar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AuthorityMessageAdapter(this, messageList);
        recyclerView.setAdapter(adapter);

        // Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Thông báo từ CQCN");
            toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        requestQueue = Volley.newRequestQueue(this);

        fetchMessages();

        adapter.setOnItemClickListener(message -> {
            AuthorityDetailBottomSheet bottomSheet = AuthorityDetailBottomSheet.newInstance(message);
            bottomSheet.show(getSupportFragmentManager(), "AuthorityDetailBottomSheet");
        });
    }

    private void fetchMessages() {
        progressBar.setVisibility(View.VISIBLE);

        String url = ApiConfig.BASE_URL + "/api/authority/messages";

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    Gson gson = new Gson();
                    Type listType = new TypeToken<List<AuthorityMessage>>() {}.getType();
                    List<AuthorityMessage> messages = gson.fromJson(response.toString(), listType);
                    messageList.clear();
                    if (messages != null) {
                        messageList.addAll(messages);
                    }
                    adapter.notifyDataSetChanged();
                },
                error -> {
                    // START: Detailed error handling
                    progressBar.setVisibility(View.GONE);
                    String errorMessage = getVolleyError(error);
                    Toast.makeText(AuthorityInboxActivity.this, "Lỗi: " + errorMessage, Toast.LENGTH_LONG).show();
                    Log.e("AuthorityInbox", "Volley Error: " + error.toString());
                    // END: Detailed error handling
                }
        );

        requestQueue.add(jsonArrayRequest);
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
