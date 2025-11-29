package com.se_04.enoti.utils;

public class VnNumberToWords {

    private static final String[] units = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] largeUnits = {
            "", "nghìn", "triệu", "tỷ"
    };

    /**
     * Converts a number to Vietnamese words.
     * @param number The number to convert.
     * @return The Vietnamese word representation.
     */
    public static String convert(long number) {
        if (number == 0) {
            return "Không đồng";
        }

        String result = "";
        int index = 0;
        long num = number;

        do {
            long chunk = num % 1000;
            if (chunk > 0) {
                String chunkString = readGroupOfThree(chunk);
                result = chunkString + " " + largeUnits[index] + " " + result;
            }
            num /= 1000;
            index++;
        } while (num > 0);

        // Cleanup: remove extra spaces and capitalize first letter
        result = result.trim().replaceAll("\\s+", " ");
        result = result.substring(0, 1).toUpperCase() + result.substring(1);

        return result + " đồng";
    }

    /**
     * Reads a 3-digit number (0-999) and converts it to words.
     */
    private static String readGroupOfThree(long num) {
        int tram = (int) (num / 100);
        int chuc = (int) ((num % 100) / 10);
        int donvi = (int) (num % 10);

        StringBuilder res = new StringBuilder();

        // Read hundreds
        if (tram > 0) {
            res.append(units[tram]).append(" trăm ");
        }

        // Read tens and units
        if (chuc > 1) { // 20-99
            res.append(units[chuc]).append(" mươi ");
            if (donvi == 1) {
                res.append("mốt");
            } else if (donvi == 5) {
                res.append("lăm");
            } else if (donvi > 0) {
                res.append(units[donvi]);
            }
        } else if (chuc == 1) { // 10-19
            res.append("mười ");
            if (donvi == 5) {
                res.append("lăm");
            } else if (donvi > 0) {
                res.append(units[donvi]);
            }
        } else { // 0-9
            if (donvi > 0 && (tram > 0 || num >= 1000)) {
                // Handle cases like 101 ("một trăm linh một")
                // or 1,000,005 ("một triệu không trăm linh năm nghìn")
                res.append("linh ");
            }
            if (donvi > 0) {
                res.append(units[donvi]);
            }
        }

        return res.toString().trim();
    }
}