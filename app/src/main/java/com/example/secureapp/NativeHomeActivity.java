package com.example.secureapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.room.Room;

import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.SubjectEntity;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.database.ExamEntity;

import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.DeviceCheckRequest; // ✅ تمت الإعادة
import com.example.secureapp.network.DeviceCheckResponse; // ✅ تمت الإعادة

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NativeHomeActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private SubjectsAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_native_home);

        checkForUpdates();

        recyclerView = findViewById(R.id.recycler_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        findViewById(R.id.btn_downloads).setOnClickListener(v -> {
            Intent intent = new Intent(NativeHomeActivity.this, DownloadsActivity.class);
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectsAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build();

        // عرض البيانات المخزنة فوراً
        loadLocalData();

        swipeRefresh.setOnRefreshListener(this::fetchDataFromServer);

        // ✅ التحديث التلقائي عند الفتح (يفحص البصمة + الاشتراكات)
        swipeRefresh.post(() -> {
            swipeRefresh.setRefreshing(true);
            fetchDataFromServer();
        });
    }

    private void loadLocalData() {
        List<SubjectEntity> data = db.subjectDao().getAllSubjects();
        if (data != null && !data.isEmpty()) {
            adapter.updateData(data);
        }
    }

    // ✅ دالة التحديث (مع فحص البصمة)
    private void fetchDataFromServer() {
        String userId = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE)
                        .getString("TelegramUserId", "");
        
        // جلب بصمة الجهاز الحالية
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (userId.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        // 1. أولاً: التحقق من البصمة
        RetrofitClient.getApi().checkDevice(new DeviceCheckRequest(userId, deviceId))
            .enqueue(new Callback<DeviceCheckResponse>() {
                @Override
                public void onResponse(Call<DeviceCheckResponse> call, Response<DeviceCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().success) {
                            // ✅ البصمة سليمة: ابدأ جلب الكورسات
                            fetchCourses(userId);
                        } else {
                            // ❌ البصمة خطأ: احذف البيانات واطرد المستخدم
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(NativeHomeActivity.this, "تنبيه: هذا الحساب مسجل على جهاز آخر!", Toast.LENGTH_LONG).show();
                            
                            // مسح البيانات المحلية لمنع الوصول
                            db.examDao().deleteAll();
                            db.videoDao().deleteAll();
                            db.chapterDao().deleteAll();
                            db.subjectDao().deleteAll();
                            loadLocalData(); // تحديث الواجهة لتصبح فارغة
                        }
                    } else {
                        swipeRefresh.setRefreshing(false);
                        // فشل التحقق من السيرفر (خطأ تقني)، لا نحذف البيانات ولكن ننبه المستخدم
                        Toast.makeText(NativeHomeActivity.this, "فشل التحقق من الجهاز", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<DeviceCheckResponse> call, Throwable t) {
                    swipeRefresh.setRefreshing(false);
                    // فشل الاتصال بالإنترنت: نبقي البيانات القديمة ليعمل أوفلاين
                    Toast.makeText(NativeHomeActivity.this, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void fetchCourses(String userId) {
        RetrofitClient.getApi().getCourses(userId).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SubjectEntity> subjects = response.body();
                    
                    List<ChapterEntity> allChapters = new ArrayList<>();
                    List<VideoEntity> allVideos = new ArrayList<>();
                    List<ExamEntity> allExams = new ArrayList<>();

                    for (SubjectEntity subject : subjects) {
                        if (subject.chaptersList != null) {
                            for (ChapterEntity chapter : subject.chaptersList) {
                                chapter.subjectId = subject.id;
                                allChapters.add(chapter);

                                if (chapter.videosList != null) {
                                    for (VideoEntity video : chapter.videosList) {
                                        video.chapterId = chapter.id;
                                        allVideos.add(video);
                                    }
                                }
                            }
                        }
                        if (subject.examsList != null) {
                            for (ExamEntity exam : subject.examsList) {
                                exam.subjectId = subject.id;
                                allExams.add(exam);
                            }
                        }
                    }

                    db.examDao().deleteAll();
                    db.videoDao().deleteAll();
                    db.chapterDao().deleteAll();
                    db.subjectDao().deleteAll();

                    if (!subjects.isEmpty()) {
                        db.subjectDao().insertAll(subjects);
                        db.chapterDao().insertAll(allChapters);
                        db.videoDao().insertAll(allVideos);
                        db.examDao().insertAll(allExams);
                        Toast.makeText(NativeHomeActivity.this, "تم التحديث ✅", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(NativeHomeActivity.this, "لا توجد مواد متاحة (أو تم سحب الصلاحيات)", Toast.LENGTH_LONG).show();
                    }
                    
                    loadLocalData();
                    
                } else {
                    Toast.makeText(NativeHomeActivity.this, "لا توجد بيانات", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<SubjectEntity>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(NativeHomeActivity.this, "فشل تحديث المواد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://api.github.com/repos/aw4788260/Apk-code-/releases/latest")
                        .build();

                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JSONObject release = new JSONObject(json);
                    
                    String tagName = release.getString("tag_name");
                    int latestVersionCode = Integer.parseInt(tagName.replaceAll("[^0-9]", ""));
                    int currentVersionCode = BuildConfig.VERSION_CODE;

                    if (latestVersionCode > currentVersionCode) {
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
