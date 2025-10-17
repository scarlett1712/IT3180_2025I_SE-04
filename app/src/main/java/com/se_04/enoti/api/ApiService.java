package com.se_04.enoti.api;

import com.se_04.enoti.notification.NotificationItem;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ApiService {

    // ✅ Lấy tất cả thông báo của người dùng
    @GET("notifications/{userId}")
    Call<List<NotificationItem>> getNotificationsByUser(@Path("userId") String userId);

    // ✅ Đánh dấu 1 thông báo là đã đọc
    @PUT("notifications/{userId}/{notificationId}/read")
    Call<Void> markNotificationAsRead(
            @Path("userId") String userId,
            @Path("notificationId") String notificationId
    );

    // ✅ Tạo thông báo mới
    @POST("notifications")
    Call<NotificationItem> createNotification(@Body NotificationItem notification);

    // ✅ Lấy chi tiết 1 thông báo
    @GET("notifications/detail/{id}")
    Call<NotificationItem> getNotificationById(@Path("id") String id);

    // ✅ Cập nhật thông báo
    @PUT("notifications/{id}")
    Call<NotificationItem> updateNotification(@Path("id") String id, @Body NotificationItem notification);

    // ✅ Xoá thông báo
    @DELETE("notifications/{id}")
    Call<Void> deleteNotification(@Path("id") String id);
}
