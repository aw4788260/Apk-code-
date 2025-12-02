package com.example.secureapp.network;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static ApiService getApi() {
        if (retrofit == null) {
            // ✅ زيادة مهلة الاتصال لـ 60 ثانية لتجنب أخطاء الشبكة مع الفيديوهات البطيئة
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            retrofit = new Retrofit.Builder()
                    // استبدل الرابط القديم بهذا:
.baseUrl("https://courses.aw478260.dpdns.org/")
                    .client(client) // ✅ ربط العميل الجديد
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
