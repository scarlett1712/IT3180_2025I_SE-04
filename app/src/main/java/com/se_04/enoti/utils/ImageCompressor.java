package com.se_04.enoti.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageCompressor {

    public static File compressImage(String imagePath, int maxWidth, int maxHeight, int quality) {
        try {
            // Đọc file ảnh gốc
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) return new File(imagePath);

            // Xử lý xoay ảnh
            bitmap = rotateImageIfRequired(bitmap, imagePath);

            // Tính toán kích thước mới
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            if (width > maxWidth || height > maxHeight) {
                float ratio = Math.min((float) maxWidth / width, (float) maxHeight / height);
                int newWidth = (int) (width * ratio);
                int newHeight = (int) (height * ratio);

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                bitmap.recycle();
                bitmap = resizedBitmap;
            }

            // Nén ảnh
            File compressedFile = new File(imagePath.replace(".jpg", "_compressed.jpg"));
            FileOutputStream fos = new FileOutputStream(compressedFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();

            return compressedFile;

        } catch (IOException e) {
            e.printStackTrace();
            return new File(imagePath);
        }
    }

    private static Bitmap rotateImageIfRequired(Bitmap img, String path) throws IOException {
        ExifInterface ei = new ExifInterface(path);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }
}