package com.se_04.enoti.report;

import org.json.JSONObject;
import java.io.Serializable;

public class ReportItem implements Serializable {
    private int id;
    private String description;
    private String status;
    private String date;
    private String reporterName;
    private String reporterPhone;
    private String assetName;
    private String location;
    private String adminNote; // 🔥 Thêm trường ghi chú của Admin

    public ReportItem(JSONObject obj) {
        this.id = obj.optInt("report_id"); // Khớp với cột trong bảng mới
        this.description = obj.optString("description");
        this.status = obj.optString("status");
        this.date = obj.optString("created_at");
        this.adminNote = obj.optString("admin_note");

        this.reporterName = obj.optString("reporter_name", "Ẩn danh");
        this.reporterPhone = obj.optString("reporter_phone", "");
        this.assetName = obj.optString("asset_name", "Thiết bị chung");
        this.location = obj.optString("location", "");
    }

    public int getId() { return id; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
    public String getReporterName() { return reporterName; }
    public String getReporterPhone() { return reporterPhone; }
    public String getAssetName() { return assetName; }
    public String getLocation() { return location; }
    public String getAdminNote() { return adminNote; }
}