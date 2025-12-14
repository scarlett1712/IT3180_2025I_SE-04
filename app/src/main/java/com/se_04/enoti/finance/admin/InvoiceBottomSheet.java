package com.se_04.enoti.finance.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;
import com.se_04.enoti.utils.UserManager;
import com.se_04.enoti.utils.VnNumberToWords;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class InvoiceBottomSheet extends BottomSheetDialogFragment {

    private int financeId;
    private int userId;

    private TextView txtOrderCode, txtAmount, txtAmountWords, txtTime, txtDesc;
    private ProgressBar progressBar;
    private LinearLayout contentLayout;

    // Constructor nhận dữ liệu
    public static InvoiceBottomSheet newInstance(int financeId, int userId) {
        InvoiceBottomSheet fragment = new InvoiceBottomSheet();
        Bundle args = new Bundle();
        args.putInt("finance_id", financeId);
        args.putInt("user_id", userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_invoice, container, false);

        if (getArguments() != null) {
            financeId = getArguments().getInt("finance_id");
            userId = getArguments().getInt("user_id");
        }

        // Ánh xạ
        txtOrderCode = view.findViewById(R.id.bsTxtOrderCode);
        txtAmount = view.findViewById(R.id.bsTxtAmount);
        txtAmountWords = view.findViewById(R.id.bsTxtAmountWords);
        txtTime = view.findViewById(R.id.bsTxtTime);
        txtDesc = view.findViewById(R.id.bsTxtDesc);
        progressBar = view.findViewById(R.id.bsProgressBar);
        contentLayout = view.findViewById(R.id.bsContentLayout);
        Button btnClose = view.findViewById(R.id.bsBtnClose);

        btnClose.setOnClickListener(v -> dismiss());

        // Gọi API
        fetchInvoiceDetails();

        return view;
    }

    private void fetchInvoiceDetails() {
        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        // Gọi API lấy hóa đơn
        String url = ApiConfig.BASE_URL + "/api/invoice/by-finance/" + financeId + "?user_id=" + userId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);

                    try {
                        String code = response.optString("ordercode", "---");
                        long amount = response.optLong("amount", 0);
                        String desc = response.optString("description", "");
                        String time = response.optString("pay_time_formatted", "N/A");

                        txtOrderCode.setText(code);
                        txtAmount.setText(new DecimalFormat("#,###,###").format(amount) + " đ");
                        txtAmountWords.setText("(" + VnNumberToWords.convert(amount) + ")");
                        txtDesc.setText(desc);
                        txtTime.setText(time);

                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Lỗi hiển thị dữ liệu", Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Không tìm thấy hóa đơn chi tiết", Toast.LENGTH_SHORT).show();
                    dismiss();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = UserManager.getInstance(getContext()).getAuthToken();
                if (token != null) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        Volley.newRequestQueue(requireContext()).add(request);
    }
}