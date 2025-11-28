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

// استيراد كلاسات قاعدة البيانات (الجداول)
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.SubjectEntity;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.database.ExamEntity; // ✅

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
        // أضف هذا السطر بعد super.onCreate وقبل setContentView
getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_native_home);

        // 1. التحقق من التحديثات فور فتح الشاشة
        checkForUpdates();

        // 2. ربط العناصر بالواجهة
        recyclerView = findViewById(R.id.recycler_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        // ✅ برمجة زر التحميلات (للانتقال لمكتبة الأوفلاين)
        findViewById(R.id.btn_downloads).setOnClickListener(v -> {
            Intent intent = new Intent(NativeHomeActivity.this, DownloadsActivity.class);
            startActivity(intent);
        });

        // 3. إعداد القائمة (RecyclerView)
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectsAdapter(new ArrayList<>()); // قائمة فارغة مبدئياً
        recyclerView.setAdapter(adapter);

        // 4. تهيئة قاعدة البيانات المحلية (Room)
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries() // للسماح بعمليات القاعدة في الخيط الرئيسي
                .fallbackToDestructiveMigration() // لإعادة بناء القاعدة إذا تغير الإصدار
                .build();

        // 5. عرض البيانات المخزنة سابقاً فوراً (Offline First)
        loadLocalData();

        // 6. برمجة السحب للتحديث (Swipe to Refresh)
        swipeRefresh.setOnRefreshListener(() -> {
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
                            // ✅ الجهاز سليم: ابدأ بجلب المواد الكاملة
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
     * دالة جلب الهيكل الدراسي كاملاً (مواد > فصول > فيديوهات > امتحانات) وحفظه
     */
    private void fetchCourses(String userId) {
        RetrofitClient.getApi().getCourses(userId).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SubjectEntity> subjects = response.body();
                    
                    // قوائم لتجميع البيانات المتداخلة (Flattening)
                    List<ChapterEntity> allChapters = new ArrayList<>();
                    List<VideoEntity> allVideos = new ArrayList<>();
                    List<ExamEntity> allExams = new ArrayList<>();

                    // الدوران داخل البيانات لفك التداخل وربط الجداول
                    for (SubjectEntity subject : subjects) {
                        
                        // 1. معالجة الفصول والفيديوهات
                        if (subject.chaptersList != null) {
                            for (ChapterEntity chapter : subject.chaptersList) {
                                chapter.subjectId = subject.id; // ربط الفصل بالمادة
                                allChapters.add(chapter);

                                if (chapter.videosList != null) {
                                    for (VideoEntity video : chapter.videosList) {
                                        video.chapterId = chapter.id; // ربط الفيديو بالفصل
                                        allVideos.add(video);
                                    }
                                }
                            }
                        }

                        // 2. معالجة الامتحانات
                        if (subject.examsList != null) {
                            for (ExamEntity exam : subject.examsList) {
                                exam.subjectId = subject.id; // ربط الامتحان بالمادة
                                allExams.add(exam);
                            }
                        }
                    }

                    // عمليات الحفظ في قاعدة البيانات
                    // 1. تنظيف القديم
                    db.examDao().deleteAll();
                    db.videoDao().deleteAll();
                    db.chapterDao().deleteAll();
                    db.subjectDao().deleteAll();

                    // 2. حفظ الجديد
                    db.subjectDao().insertAll(subjects);
                    db.chapterDao().insertAll(allChapters);
                    db.videoDao().insertAll(allVideos);
                    db.examDao().insertAll(allExams); // حفظ الامتحانات
                    
                    // 3. تحديث الشاشة
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
                // رابط الـ API الخاص بـ Releases
                Request request = new Request.Builder()
                        .url("https://api.github.com/repos/aw4788260/Apk-code-/releases/latest")
                        .build();

                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JSONObject release = new JSONObject(json);
                    
                    String tagName = release.getString("tag_name");
                    // استخراج الأرقام فقط من التاج (مثل v350 -> 350)
                    int latestVersionCode = Integer.parseInt(tagName.replaceAll("[^0-9]", ""));
                    int currentVersionCode = BuildConfig.VERSION_CODE;

                    if (latestVersionCode > currentVersionCode) {
                        // البحث عن ملف APK
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
     * إظهار نافذة التحديث
     */
    private void showUpdateDialog(String apkUrl) {
        if (isFinishing()) return;
        
        new AlertDialog.Builder(this)
            .setTitle("تحديث جديد متوفر 🚀")
            .setMessage("يوجد إصدار جديد من التطبيق. يرجى التحديث لضمان عمل كافة الميزات.")
            .setCancelable(false)
            .setPositiveButton("تحديث الآن", (dialog, which) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                startActivity(browserIntent);
            })
            .show();
    }
}
