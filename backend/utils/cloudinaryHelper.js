import { v2 as cloudinary } from "cloudinary";
import dotenv from "dotenv";

dotenv.config();

// 1. Cấu hình Cloudinary (Lấy từ biến môi trường)
cloudinary.config({
  cloud_name: process.env.CLOUDNAME,
  api_key: process.env.CLOUDKEY,
  api_secret: process.env.CLOUDSECRET
});

/**
 * Hàm upload file Base64 lên Cloudinary
 * @param {string} fileBase64 - Chuỗi base64 của file
 * @param {string} folderName - Tên thư mục trên Cloudinary (vd: "enoti_files")
 * @param {string} fileName - Tên file muốn lưu (tùy chọn)
 * @returns {Promise<{url: string, type: string} | null>} - Trả về URL và loại file, hoặc null nếu lỗi
 */
export const uploadToCloudinary = async (fileBase64, folderName = "enoti_files", fileName = null) => {
  if (!fileBase64) return null;

  try {
    const options = {
      folder: folderName,
      resource_type: "auto", // Tự động nhận diện (image/video/raw)
    };

    // Nếu có tên file thì giữ nguyên tên đó (bỏ đuôi mở rộng)
    if (fileName) {
      options.public_id = fileName.split('.')[0];
    }

    const result = await cloudinary.uploader.upload(fileBase64, options);

    let finalType = result.resource_type; // 'image', 'video', 'raw'

    // Xử lý riêng cho PDF (Cloudinary thường trả về 'raw', ta đổi thành 'pdf' cho dễ quản lý nếu muốn)
    if (result.secure_url.endsWith(".pdf")) {
        finalType = "pdf";
    }

    return {
      url: result.secure_url,
      type: finalType
    };

  } catch (error) {
    console.error("❌ Cloudinary Upload Error:", error);
    return null;
  }
};