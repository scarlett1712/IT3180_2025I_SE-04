package com.se_04.enoti.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class StringUtils {

    // Remove Vietnamese accent marks
    public static String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }

    // Accent-insensitive, whole-word match
    public static boolean containsWholeWord(String source, String query) {
        if (source == null || query == null || query.trim().isEmpty()) return false;
        String normalizedSource = normalize(source);
        String normalizedQuery = normalize(query).trim();

        // Match whole words only
        String regex = "\\b" + Pattern.quote(normalizedQuery) + "\\b";
        return Pattern.compile(regex).matcher(normalizedSource).find();
    }
}
