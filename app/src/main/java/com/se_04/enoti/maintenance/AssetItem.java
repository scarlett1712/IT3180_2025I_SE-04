package com.se_04.enoti.maintenance;

import org.json.JSONObject;
import java.io.Serializable;

public class AssetItem implements Serializable {
    private int id;
    private String name;
    private String location;
    private String status;
    private String purchaseDate;
    private String thumbnail; // 🔥 Thêm trường ảnh đại diện

    public AssetItem(JSONObject obj) {
        this.id = obj.optInt("asset_id");
        this.name = obj.optString("asset_name");
        this.location = obj.optString("location");
        this.status = obj.optString("status");
        this.purchaseDate = obj.optString("purchase_date");
        // 🔥 Lấy thumbnail từ API (trường này được thêm trong query SQL ở backend)
        this.thumbnail = obj.optString("thumbnail");
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public String getPurchaseDate() { return purchaseDate; }
    public String getThumbnail() { return thumbnail; } // 🔥 Getter mới
}