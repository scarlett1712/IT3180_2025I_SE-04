package com.se_04.enoti.account.admin;

import org.json.JSONObject;
import java.io.Serializable;

public class ProfileRequestItem implements Serializable {
    private int requestId;
    private int userId;
    // Thông tin MỚI
    private String newFullName, newPhone, newEmail, newGender, newDob, newIdentityCard, newHomeTown;

    // Thông tin CŨ (Hiện tại)
    private String currentName, currentPhone, currentEmail, currentGender, currentDob, currentIdentityCard, currentHomeTown;

    private String createdAt;

    public ProfileRequestItem(JSONObject obj) {
        this.requestId = obj.optInt("request_id");
        this.userId = obj.optInt("user_id");

        this.newFullName = obj.optString("new_full_name");
        this.newPhone = obj.optString("new_phone");
        this.newEmail = obj.optString("new_email");
        this.newGender = obj.optString("new_gender");
        this.newDob = obj.optString("new_dob");
        this.newIdentityCard = obj.optString("new_identity_card");
        this.newHomeTown = obj.optString("new_home_town");

        this.currentName = obj.optString("current_name");
        this.currentPhone = obj.optString("current_phone");
        this.currentEmail = obj.optString("current_email");
        this.currentGender = obj.optString("current_gender");
        this.currentDob = obj.optString("current_dob");
        this.currentIdentityCard = obj.optString("current_identity_card");
        this.currentHomeTown = obj.optString("current_home_town");

        this.createdAt = obj.optString("created_at");
    }

    // Getters
    public int getRequestId() { return requestId; }
    public String getNewFullName() { return newFullName; }
    public String getNewPhone() { return newPhone; }
    public String getNewEmail() { return newEmail; }
    public String getNewGender() { return newGender; }
    public String getNewDob() { return newDob; }
    public String getNewIdentityCard() { return newIdentityCard; }
    public String getNewHomeTown() { return newHomeTown; }

    public String getCurrentName() { return currentName; }
    public String getCurrentPhone() { return currentPhone; }
    public String getCurrentEmail() { return currentEmail; }
    public String getCurrentGender() { return currentGender; }
    public String getCurrentDob() { return currentDob; }
    public String getCurrentIdentityCard() { return currentIdentityCard; }
    public String getCurrentHomeTown() { return currentHomeTown; }
}