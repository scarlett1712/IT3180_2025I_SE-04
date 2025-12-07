package com.se_04.enoti.maintenance;

import org.json.JSONObject;

public class AssetHistoryItem {
    private String type; // "Maintenance" hoặc "MyReport"
    private String status;
    private String description;
    private String date;

    public AssetHistoryItem(JSONObject obj) {
        this.type = obj.optString("type");
        this.status = obj.optString("status");
        this.description = obj.optString("description");
        this.date = obj.optString("date");
    }

    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
}