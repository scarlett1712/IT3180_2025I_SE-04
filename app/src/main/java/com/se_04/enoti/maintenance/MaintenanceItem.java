package com.se_04.enoti.maintenance;

import org.json.JSONObject;
import java.io.Serializable;

public class MaintenanceItem implements Serializable {
    private int scheduleId;
    private int assetId;
    private String assetName;
    private String location;
    private String scheduledDate;
    private String status; // Pending, Completed, Cancelled
    private String description;
    private String staffName;
    private String resultNote;

    public MaintenanceItem(JSONObject obj) {
        this.scheduleId = obj.optInt("schedule_id");
        this.assetId = obj.optInt("asset_id");
        this.assetName = obj.optString("asset_name", "Thiết bị không tên");
        this.location = obj.optString("location", "Chưa cập nhật");
        // Cắt chuỗi ngày tháng cho gọn (YYYY-MM-DD)
        String rawDate = obj.optString("scheduled_date");
        this.scheduledDate = rawDate.length() >= 10 ? rawDate.substring(0, 10) : rawDate;

        this.status = obj.optString("status", "Pending");
        this.description = obj.optString("description", "");
        this.staffName = obj.optString("staff_name", "Chưa giao");
        this.resultNote = obj.optString("result_note", "");
    }

    // Getters
    public int getScheduleId() { return scheduleId; }
    public String getAssetName() { return assetName; }
    public String getLocation() { return location; }
    public String getScheduledDate() { return scheduledDate; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public String getStaffName() { return staffName; }
    public String getResultNote() { return resultNote; }
}