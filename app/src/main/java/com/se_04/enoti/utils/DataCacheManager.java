package com.se_04.enoti.utils;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class DataCacheManager {

    // Định nghĩa tên file cache
    public static final String CACHE_ASSETS = "cache_assets.json";
    public static final String CACHE_RESIDENTS = "cache_residents.json";
    public static final String CACHE_FINANCE = "cache_finance_admin.json"; // 🔥 Mới
    public static final String CACHE_ADMIN_NOTIFS = "cache_notifs_admin.json"; // 🔥 Mới

    private static DataCacheManager instance;
    private final Context context;

    private DataCacheManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DataCacheManager getInstance(Context context) {
        if (instance == null) instance = new DataCacheManager(context);
        return instance;
    }

    public void saveCache(String fileName, String jsonData) {
        try {
            FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(jsonData.getBytes());
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String readCache(String fileName) {
        try {
            File file = new File(context.getFilesDir(), fileName);
            if (!file.exists()) return null;
            FileInputStream fis = context.openFileInput(fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public void clearAllCache() {
        context.deleteFile(CACHE_ASSETS);
        context.deleteFile(CACHE_RESIDENTS);
        context.deleteFile(CACHE_FINANCE);
        context.deleteFile(CACHE_ADMIN_NOTIFS);

        // Xóa cache thông báo user (file động)
        File dir = context.getFilesDir();
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith("cache_notifs_")) file.delete();
                }
            }
        }
    }
}