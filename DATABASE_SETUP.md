# Hướng dẫn cấu hình Database trên Render

## Vấn đề
Bạn đã seeding dữ liệu vào Aiven nhưng app không thấy dữ liệu. Điều này thường xảy ra khi backend trên Render không kết nối đúng database.

## Connection String từ Aiven
```
postgres://avnadmin:YOUR_PASSWORD_HERE@se-04-enoti-hoangduytan1225.c.aivencloud.com:21067/defaultdb?sslmode=require
```

**Lưu ý**: Thay `YOUR_PASSWORD_HERE` bằng password thực tế từ Aiven Console của bạn.

## Cách cấu hình trên Render

### Bước 1: Đăng nhập vào Render Dashboard
- Truy cập: https://dashboard.render.com
- Đăng nhập vào tài khoản của bạn

### Bước 2: Chọn Web Service (Backend)
- Tìm service backend của bạn (thường có tên như `nmcnpm-se-04` hoặc tương tự)
- Click vào service đó

### Bước 3: Cấu hình Environment Variables
- Trong menu bên trái, chọn **"Environment"**
- Scroll xuống phần **"Environment Variables"**
- Thêm hoặc cập nhật biến môi trường sau:

#### Cách 1: Sử dụng DATABASE_URL (KHUYẾN NGHỊ)
Thêm biến môi trường:
- **Key**: `DATABASE_URL`
- **Value**: 
  ```
  postgres://avnadmin:YOUR_PASSWORD_HERE@se-04-enoti-hoangduytan1225.c.aivencloud.com:21067/defaultdb?sslmode=require
  ```
  
  **Lưu ý**: Thay `YOUR_PASSWORD_HERE` bằng password thực tế từ Aiven Console.

#### Cách 2: Sử dụng các biến riêng lẻ
Nếu không dùng DATABASE_URL, thêm các biến sau:
- **Key**: `PGHOST`
  **Value**: `se-04-enoti-hoangduytan1225.c.aivencloud.com`

- **Key**: `PGPORT`
  **Value**: `21067`

- **Key**: `PGDATABASE`
  **Value**: `defaultdb`

- **Key**: `PGUSER`
  **Value**: `avnadmin`

- **Key**: `PGPASSWORD`
  **Value**: `YOUR_PASSWORD_HERE` (thay bằng password thực tế từ Aiven Console)

### Bước 4: Lưu và Deploy lại
- Click **"Save Changes"**
- Render sẽ tự động deploy lại service
- Đợi quá trình deploy hoàn tất (thường mất 2-5 phút)

### Bước 5: Kiểm tra kết nối
Sau khi deploy xong, test kết nối database bằng cách:

1. Truy cập endpoint test:
   ```
   https://nmcnpm-se-04.onrender.com/api/test/test-connection
   ```

2. Bạn sẽ thấy response như sau nếu kết nối thành công:
   ```json
   {
     "success": true,
     "message": "✅ Database kết nối thành công!",
     "database_info": {
       "current_time": "...",
       "pg_version": "PostgreSQL ...",
       "tables_count": 15,
       "tables": ["users", "user_item", ...],
       "users_count": 10
     }
   }
   ```

3. Nếu có lỗi, bạn sẽ thấy thông tin lỗi chi tiết để debug.

## Lưu ý quan trọng

1. **Database name**: Đảm bảo bạn seeding dữ liệu vào database `defaultdb` (theo connection string)

2. **Kiểm tra database name trên Aiven**:
   - Vào Aiven Console
   - Chọn service PostgreSQL
   - Kiểm tra tên database bạn đang seed dữ liệu
   - Nếu không phải `defaultdb`, cần cập nhật connection string

3. **SSL Mode**: Connection string đã có `sslmode=require`, code đã được cấu hình để hỗ trợ SSL

4. **Restart service**: Sau khi thay đổi environment variables, Render sẽ tự động restart, nhưng bạn có thể restart thủ công nếu cần

## Troubleshooting

### Vấn đề: Vẫn không thấy dữ liệu sau khi cấu hình
**Giải pháp:**
1. Kiểm tra xem bạn đã seed vào đúng database chưa
2. Kiểm tra logs trên Render để xem có lỗi kết nối không
3. Test endpoint `/api/test/test-connection` để xem thông tin chi tiết
4. Đảm bảo tên database trong connection string khớp với database bạn đã seed

### Vấn đề: Lỗi "relation does not exist"
**Giải pháp:**
- Có thể bạn đã seed vào database khác
- Kiểm tra lại schema/database name trên Aiven
- Đảm bảo các bảng đã được tạo đúng

### Vấn đề: Connection timeout
**Giải pháp:**
- Kiểm tra firewall rules trên Aiven
- Đảm bảo Aiven cho phép kết nối từ Render IPs
- Kiểm tra connection string có đúng không

