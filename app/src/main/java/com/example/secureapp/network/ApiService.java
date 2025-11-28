package com.example.secureapp.network;

import com.example.secureapp.database.SubjectEntity;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST; // تأكد من إضافة هذا
import retrofit2.http.Query;

public interface ApiService {
    // (الكود القديم لجلب المواد)
    @GET("api/data/get-structured-courses")
    Call<List<SubjectEntity>> getCourses(@Query("userId") String userId);

    // 👇👇 (الكود الجديد للتحقق من الجهاز) 👇👇
    @POST("api/auth/check-device")
    Call<DeviceCheckResponse> checkDevice(@Body DeviceCheckRequest request);
}
