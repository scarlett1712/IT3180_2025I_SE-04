package com.se_04.enoti.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.se_04.enoti.R;
import com.se_04.enoti.utils.ApiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdminReportBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private AdminReportAdapter adapter;
    private List<ReportItem> reportList = new ArrayList<>();
    private TextView txtEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_report_bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewReports);
        txtEmpty = view.findViewById(R.id.txtEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminReportAdapter(reportList, this::showActionDialog);
        recyclerView.setAdapter(adapter);

        loadReports();

        return view;
    }

    private void loadReports() {
        String url = ApiConfig.BASE_URL + "/api/reports/all";
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    reportList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            reportList.add(new ReportItem(response.getJSONObject(i)));
                        }
                        adapter.notifyDataSetChanged();

                        if (reportList.isEmpty()) txtEmpty.setVisibility(View.VISIBLE);
                        else txtEmpty.setVisibility(View.GONE);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Lỗi tải báo cáo", Toast.LENGTH_SHORT).show();
                }
        );
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void showActionDialog(ReportItem item) {
        String[] actions = {"Đang xử lý", "Hoàn thành", "Từ chối"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Xử lý: " + item.getDescription())
                .setItems(actions, (dialog, which) -> {
                    String status = "";
                    switch (which) {
                        case 0: status = "Processing"; break;
                        case 1: status = "Completed"; break;
                        case 2: status = "Rejected"; break;
                    }
                    updateStatus(item.getId(), status);
                })
                .show();
    }

    private void updateStatus(int reportId, String status) {
        String url = ApiConfig.BASE_URL + "/api/reports/update-status";
        JSONObject body = new JSONObject();
        try {
            body.put("report_id", reportId);
            body.put("status", status);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    Toast.makeText(getContext(), "Đã cập nhật trạng thái!", Toast.LENGTH_SHORT).show();
                    loadReports(); // Tải lại danh sách sau khi update
                },
                error -> Toast.makeText(getContext(), "Lỗi cập nhật", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(requireContext()).add(request);
    }
}