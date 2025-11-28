package com.example.secureapp.network;

import com.example.secureapp.database.SubjectEntity;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    @GET("api/data/get-structured-courses")
    Call<List<SubjectEntity>> getCourses(@Query("userId") String userId);
}
