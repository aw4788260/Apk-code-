package com.example.secureapp;

import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.room.Room;

// استيراد كلاسات قاعدة البيانات
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.SubjectEntity;

// استيراد كلاسات الشبكة
import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.DeviceCheckRequest;
import com.example.secureapp.network.DeviceCheckResponse;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NativeHomeActivity extends AppCompatActivity {

    // تعريف عناصر الواجهة
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private SubjectsAdapter adapter;
    
    // تعريف قاعدة البيانات
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_native_home);

        // 1. ربط العناصر بالواجهة
        recyclerView = findViewById(R.id.recycler_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        // 2. إعداد القائمة (RecyclerView)
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectsAdapter(new ArrayList<>()); // قائمة فارغة مبدئياً
        recyclerView.setAdapter(adapter);

        // 3. تهيئة قاعدة البيانات المحلية (Room)
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries() // للسماح بعمليات القاعدة في الخيط الرئيسي (للتسهيل)
                .build();

        // 4. عرض البيانات المخزنة سابقاً فوراً (Offline First)
        loadLocalData();

        // 5. برمجة السحب للتحديث (Swipe to Refresh)
        swipeRefresh.setOnRefreshListener(() -> {
            // عند السحب، قم بطلب التحديث من السيرفر
            fetchDataFromServer();
        });

        // 6. إذا كانت القائمة فارغة (أول مرة)، اطلب البيانات تلقائياً
        if (adapter.getItemCount() == 0) {
            swipeRefresh.setRefreshing(true);
            fetchDataFromServer();
        }
    }

    /**
     * دالة لقراءة البيانات من قاعدة البيانات المحلية وعرضها
     */
    private void loadLocalData() {
        List<SubjectEntity> data = db.subjectDao().getAllSubjects();
        if (data != null && !data.isEmpty()) {
            adapter.updateData(data);
        }
    }

    /**
     * الدالة الرئيسية لجلب البيانات من السيرفر
     * (تقوم بالتحقق من الجهاز أولاً للأمان)
     */
    private void fetchDataFromServer() {
        // جلب الـ User ID المخزن
        String userId = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE)
                        .getString("TelegramUserId", "");
        
        // جلب بصمة الجهاز (Android ID)
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (userId.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        // --- الخطوة 1: التحقق من أمان الجهاز ---
        RetrofitClient.getApi().checkDevice(new DeviceCheckRequest(userId, deviceId))
            .enqueue(new Callback<DeviceCheckResponse>() {
                @Override
                public void onResponse(Call<DeviceCheckResponse> call, Response<DeviceCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        
                        if (response.body().success) {
                            // ✅ الجهاز سليم ومطابق: ابدأ بجلب المواد
                            fetchCourses(userId);
                        } else {
                            // ❌ جهاز غير مصرح به (مخالف)
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(NativeHomeActivity.this, "تنبيه: هذا الحساب مسجل على جهاز آخر!", Toast.LENGTH_LONG).show();
                            
                            // إجراء أمني: مسح البيانات المحلية لمنع الوصول
                            db.subjectDao().deleteAll();
                            loadLocalData(); // تحديث الشاشة لتصبح فارغة
                        }
                    } else {
                        // خطأ في السيرفر أثناء التحقق
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(NativeHomeActivity.this, "فشل التحقق من السيرفر", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<DeviceCheckResponse> call, Throwable t) {
                    // فشل الاتصال بالإنترنت
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(NativeHomeActivity.this, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * دالة جلب المواد الدراسية
     * (يتم استدعاؤها فقط بعد نجاح التحقق من الجهاز)
     */
    private void fetchCourses(String userId) {
        RetrofitClient.getApi().getCourses(userId).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false); // إخفاء علامة التحميل
                
                if (response.isSuccessful() && response.body() != null) {
                    // 1. مسح البيانات القديمة لضمان عدم التكرار
                    db.subjectDao().deleteAll();
                    
                    // 2. حفظ البيانات الجديدة في قاعدة البيانات
                    db.subjectDao().insertAll(response.body());
                    
                    // 3. تحديث الشاشة من القاعدة
                    loadLocalData();
                    
                    Toast.makeText(NativeHomeActivity.this, "تم التحديث بنجاح ✅", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(NativeHomeActivity.this, "لا توجد مواد متاحة حالياً", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<SubjectEntity>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(NativeHomeActivity.this, "فشل تحميل المواد", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
