package com.se_04.enoti.maintenance;

import org.json.JSONObject;

public class AssetHistoryItem {
    // 1. Khai báo đầy đủ các biến (Fields)
    private String date;
    private String action;
    private String performerName;
    private String result;
    private String status;

    // 2. Constructor: Parse dữ liệu từ JSON API
    public AssetHistoryItem(JSONObject obj) {
        // Lấy ngày tháng
        this.date = obj.optString("date"); // Hoặc "created_at" tùy API của bạn

        // Lấy trạng thái (để tô màu icon nếu cần)
        this.status = obj.optString("status");

        // Lấy Tiêu đề hành động (Action)
        // Ưu tiên lấy field "action", nếu không có thì lấy "type" (VD: Maintenance)
        String act = obj.optString("action");
        if (act.isEmpty()) {
            act = obj.optString("type");
        }
        this.action = act;

        // Lấy Tên người thực hiện
        // Field này tùy thuộc vào JSON server trả về (VD: "performer_name", "staff_name", "user_name")
        this.performerName = obj.optString("performer_name", "Ban quản lý");

        // Lấy Kết quả / Mô tả
        // Ưu tiên "result", nếu không có thì lấy "description"
        String res = obj.optString("result");
        if (res.isEmpty()) {
            res = obj.optString("description");
        }
        this.result = res;
    }

    // 3. Các hàm Getter (Để Adapter gọi)
    public String getDate() {
        return date;
    }

    public String getAction() {
        return action;
    }

    public String getPerformerName() {
        return performerName;
    }

    public String getResult() {
        return result;
    }

    public String getStatus() {
        return status;
    }
}