package com.se_04.enoti.utils;

import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidatePhoneNumberUtil {
    /**
     * Validates a Vietnamese phone number.
     * A valid number starts with '0' followed by 9 digits,
     * OR starts with '+84' followed by 9 digits.
     */
    public static boolean isValidVietnamesePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        // Regex for Vietnamese phone numbers:
        // Starts with 0 followed by 9 digits OR starts with +84 followed by 9 digits
        // Allows for common mobile prefixes like 09, 08, 07, 05, 03
        String regex = "^(0[35789]\\d{8}|\\+84[35789]\\d{8})$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(phone);
        return matcher.matches();
    }

    /**
     * Normalizes a Vietnamese phone number to start with +84 format.
     * Example: 0912345678 -> +84912345678
     *          +84912345678 -> +84912345678 (no change)
     */
    public static String normalizePhoneNumber(String phone) {
        if (phone.startsWith("0")) {
            return "+84" + phone.substring(1);
        }
        return phone;
    }
}
