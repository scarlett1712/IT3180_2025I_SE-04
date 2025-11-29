package com.se_04.enoti.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class createQRCode {

    private final String bank_id;
    private final String account_no;
    private final String amount;
    private final String description;
    private final String account_name;
    private static final String TEMPLATE = "3AoGQeA";

    public createQRCode(String bank_id, String account_no, String amount, String description, String account_name) {
        this.bank_id = bank_id;
        this.account_no = account_no;
        this.amount = amount;
        this.description = description;
        this.account_name = account_name;
    }

    /**
     * Tạo URL để lấy ảnh QR từ VietQR dựa trên các thông tin đã được cung cấp trong constructor.
     * @return Một chuỗi URL hoàn chỉnh để tải ảnh.
     */
    public String generateQrUrl() {
        String linkTemplate = "https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-<TEMPLATE>.png?amount=<AMOUNT>&addInfo=<DESCRIPTION>&accountName=<ACCOUNT_NAME>";

        String encodedDescription = "";
        String encodedAccountName = "";
        try {
            // Mã hóa nội dung và tên để đảm bảo URL hợp lệ
            if (this.description != null) {
                encodedDescription = URLEncoder.encode(this.description, StandardCharsets.UTF_8.toString());
            }
            if (this.account_name != null) {
                encodedAccountName = URLEncoder.encode(this.account_name, StandardCharsets.UTF_8.toString());
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            // This should not happen with UTF-8, but handle it just in case
        }

        return linkTemplate
                .replace("<BANK_ID>", this.bank_id)
                .replace("<ACCOUNT_NO>", this.account_no)
                .replace("<TEMPLATE>", TEMPLATE)
                .replace("<AMOUNT>", this.amount)
                .replace("<DESCRIPTION>", encodedDescription)
                .replace("<ACCOUNT_NAME>", encodedAccountName);
    }
}
