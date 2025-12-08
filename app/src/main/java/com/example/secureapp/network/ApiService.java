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

    // ✅ 1. تسجيل الدخول
    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    // ✅ 2. إنشاء حساب جديد (جديد)
    @POST("api/auth/signup")
    Call<SignupResponse> signup(@Body SignupRequest request);

    // ✅ 3. التحقق من الجهاز
    @POST("api/auth/check-device")
    Call<DeviceCheckResponse> checkDevice(@Body DeviceCheckRequest request);

    // ✅ 4. جلب المواد
    @GET("api/data/get-structured-courses")
    Call<List<SubjectEntity>> getCourses(
        @Header("x-user-id") String userId,
        @Header("x-device-id") String deviceId,
        @Header("x-app-secret") String appSecret
    );

    // ✅ 5. جلب رابط الفيديو
    @GET("api/secure/get-video-id")
    Call<VideoApiResponse> getVideoUrl(
        @Query("lessonId") int lessonId,
        @Header("x-user-id") String userId,
        @Header("x-device-id") String deviceId,
        @Header("x-app-secret") String appSecret
    );
}
