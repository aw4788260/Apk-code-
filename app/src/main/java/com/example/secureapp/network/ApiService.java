package com.example.secureapp.network;

import com.example.secureapp.database.SubjectEntity;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    // 1. جلب المواد والفصول والفيديوهات
    @GET("api/data/get-structured-courses")
    Call<List<SubjectEntity>> getCourses(@Query("userId") String userId);

    // 2. التحقق من بصمة الجهاز للأمان
    @POST("api/auth/check-device")
    Call<DeviceCheckResponse> checkDevice(@Body DeviceCheckRequest request);

    // 3. جلب رابط الفيديو المباشر (للمشاهدة أونلاين)
    @GET("api/secure/get-video-id")
    Call<VideoApiResponse> getVideoUrl(
        @Query("lessonId") int lessonId,
        @Query("userId") String userId,
        @Query("deviceId") String deviceId
    );
}
