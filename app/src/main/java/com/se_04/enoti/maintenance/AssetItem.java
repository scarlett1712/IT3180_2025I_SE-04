package com.se_04.enoti.maintenance;

import org.json.JSONObject;
import java.io.Serializable; // 🔥 Thêm cái này để truyền dữ liệu giữa các Activity

public class AssetItem implements Serializable {
    private int id;
    private String name;
    private String location;
    private String status;
    private String purchaseDate;

    public AssetItem(JSONObject obj) {
        this.id = obj.optInt("asset_id");
        this.name = obj.optString("asset_name");
        this.location = obj.optString("location");
        this.status = obj.optString("status");
        this.purchaseDate = obj.optString("purchase_date");
    }

    // 🔥 ĐÂY LÀ HÀM BẠN ĐANG THIẾU
    public int getId() { return id; }

    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public String getPurchaseDate() { return purchaseDate; }
}