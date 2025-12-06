package com.example.secureapp.network;

import com.example.secureapp.database.SubjectEntity;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    // ✅ 1. تسجيل الدخول الجديد (اسم وباسورد)
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    // ✅ 2. التحقق من الجهاز (يستخدم داخلياً أو عند التحديث)
    @POST("api/auth/check-device")
    Call<DeviceCheckResponse> checkDevice(@Body DeviceCheckRequest request);

    // ✅ 3. جلب المواد (إرسال الهوية في الهيدرز)
    @GET("api/data/get-structured-courses")
    Call<List<SubjectEntity>> getCourses(
        @Header("x-user-id") String userId,
        @Header("x-device-id") String deviceId,
        @Header("x-app-secret") String appSecret
    );

    // ✅ 4. جلب رابط الفيديو (إرسال الهوية في الهيدرز)
    @GET("api/secure/get-video-id")
    Call<VideoApiResponse> getVideoUrl(
        @Query("lessonId") int lessonId,
        @Header("x-user-id") String userId,
        @Header("x-device-id") String deviceId,
        @Header("x-app-secret") String appSecret
    );
}
