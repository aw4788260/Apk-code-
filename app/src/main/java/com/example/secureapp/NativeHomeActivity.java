package com.example.secureapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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

// مكتبات للتعامل مع التحديثات
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.json.JSONObject;

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

        // 1. التحقق من التحديثات فور فتح الشاشة (الميزة الجديدة)
        checkForUpdates();

        // 2. ربط العناصر بالواجهة
        recyclerView = findViewById(R.id.recycler_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        // 3. إعداد القائمة (RecyclerView)
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectsAdapter(new ArrayList<>()); // قائمة فارغة مبدئياً
        recyclerView.setAdapter(adapter);

        // 4. تهيئة قاعدة البيانات المحلية (Room)
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries() // للسماح بعمليات القاعدة في الخيط الرئيسي
                .build();

        // 5. عرض البيانات المخزنة سابقاً فوراً (Offline First)
        loadLocalData();

        // 6. برمجة السحب للتحديث (Swipe to Refresh)
        swipeRefresh.setOnRefreshListener(() -> {
            // عند السحب، قم بطلب التحديث من السيرفر
            fetchDataFromServer();
        });

        // 7. إذا كانت القائمة فارغة (أول مرة)، اطلب البيانات تلقائياً
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
        String userId = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE)
                        .getString("TelegramUserId", "");
        
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (userId.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        // --- الخطوة الأمنية: التحقق من الجهاز ---
        RetrofitClient.getApi().checkDevice(new DeviceCheckRequest(userId, deviceId))
            .enqueue(new Callback<DeviceCheckResponse>() {
                @Override
                public void onResponse(Call<DeviceCheckResponse> call, Response<DeviceCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().success) {
                            // ✅ الجهاز سليم: ابدأ بجلب المواد
                            fetchCourses(userId);
                        } else {
                            // ❌ جهاز مخالف
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(NativeHomeActivity.this, "تنبيه: هذا الحساب مسجل على جهاز آخر!", Toast.LENGTH_LONG).show();
                            db.subjectDao().deleteAll(); // مسح البيانات للأمان
                            loadLocalData();
                        }
                    } else {
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(NativeHomeActivity.this, "فشل التحقق من السيرفر", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<DeviceCheckResponse> call, Throwable t) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(NativeHomeActivity.this, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * دالة جلب المواد الدراسية (بعد نجاح التحقق)
     */
    private void fetchCourses(String userId) {
        RetrofitClient.getApi().getCourses(userId).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    // تحديث القاعدة المحلية
                    db.subjectDao().deleteAll();
                    db.subjectDao().insertAll(response.body());
                    // تحديث الشاشة
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

    /**
     * دالة فحص التحديثات من GitHub
     */
    private void checkForUpdates() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                // رابط الـ API الخاص بـ Releases في مستودعك
                Request request = new Request.Builder()
                        .url("https://api.github.com/repos/aw4788260/Apk-code-/releases/latest")
                        .build();

                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JSONObject release = new JSONObject(json);
                    
                    // استخراج رقم الإصدار من الـ Tag (مثلاً v350 -> 350)
                    String tagName = release.getString("tag_name");
                    // إزالة أي حروف وترك الأرقام فقط
                    int latestVersionCode = Integer.parseInt(tagName.replaceAll("[^0-9]", ""));
                    
                    // مقارنة بالإصدار الحالي للتطبيق
                    int currentVersionCode = BuildConfig.VERSION_CODE;

                    if (latestVersionCode > currentVersionCode) {
                        // يوجد تحديث! ابحث عن رابط الـ APK في الأصول (Assets)
                        String downloadUrl = "";
                        org.json.JSONArray assets = release.getJSONArray("assets");
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url");
                                break;
                            }
                        }

                        if (!downloadUrl.isEmpty()) {
                            String finalUrl = downloadUrl;
                            // العودة للخيط الرئيسي لإظهار النافذة
                            runOnUiThread(() -> showUpdateDialog(finalUrl));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * إظهار نافذة التحديث الإجبارية/الاختيارية
     */
    private void showUpdateDialog(String apkUrl) {
        if (isFinishing()) return; // تجنب الأخطاء إذا أغلقت الشاشة
        
        new AlertDialog.Builder(this)
            .setTitle("تحديث جديد متوفر 🚀")
            .setMessage("يوجد إصدار جديد من التطبيق. يرجى التحديث لضمان عمل كافة الميزات.")
            .setCancelable(false) // منع الإغلاق باللمس خارج النافذة
            .setPositiveButton("تحديث الآن", (dialog, which) -> {
                // فتح الرابط في المتصفح للتحميل
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                startActivity(browserIntent);
            })
            // يمكنك إضافة زر "لاحقاً" هنا إذا أردت، لكن يفضل الإجبار للتطبيقات التعليمية
            .show();
    }
}
