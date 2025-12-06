package com.example.secureapp;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.room.Room;

import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.SubjectEntity;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.database.ExamEntity;
import com.example.secureapp.database.PdfEntity;

import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.DeviceCheckRequest;
import com.example.secureapp.network.DeviceCheckResponse;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.json.JSONObject;

import java.io.File;
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

    // متغيرات نظام التحديث
    private long downloadId = -1;
    private String currentUpdateFileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // حماية أمنية (منع لقطة الشاشة)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_native_home);

        // تسجيل مستقبل التحميلات للتحديث
        registerDownloadReceiver();

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

        // ✅ بناء قاعدة البيانات
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build();

        loadLocalData();

        swipeRefresh.setOnRefreshListener(this::fetchDataFromServer);

        // تحديث البيانات تلقائياً عند الفتح
        swipeRefresh.post(() -> {
            swipeRefresh.setRefreshing(true);
            fetchDataFromServer();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (Exception e) {
            // تجاهل الخطأ
        }
    }

    private void loadLocalData() {
        List<SubjectEntity> data = db.subjectDao().getAllSubjects();
        if (data != null) {
            adapter.updateData(data);
        }
    }

    private void fetchDataFromServer() {
        String userId = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE)
                        .getString("TelegramUserId", "");
        
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        if (userId.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        // 1. التحقق من الجهاز أولاً (Handshake)
        RetrofitClient.getApi().checkDevice(new DeviceCheckRequest(userId, deviceId))
            .enqueue(new Callback<DeviceCheckResponse>() {
                @Override
                public void onResponse(Call<DeviceCheckResponse> call, Response<DeviceCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().success) {
                            // الجهاز صحيح -> جلب الكورسات بالهيدرز
                            fetchCourses(userId);
                        } else {
                            handleDeviceMismatch();
                        }
                    } 
                    else if (response.code() == 403) {
                        handleDeviceMismatch();
                    }
                    else {
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(NativeHomeActivity.this, "خطأ في السيرفر: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<DeviceCheckResponse> call, Throwable t) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(NativeHomeActivity.this, "تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void fetchCourses(String userId) {
        // ✅ تجهيز البيانات للهيدرز
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String appSecret = MainActivity.APP_SECRET;

        // ✅ استدعاء الدالة الجديدة التي ترسل الهيدرز
        RetrofitClient.getApi().getCourses(userId, deviceId, appSecret).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SubjectEntity> subjects = response.body();
                    
                    if (subjects.isEmpty()) {
                        // قد يكون المستخدم ليس لديه كورسات، أو تم سحب الصلاحية
                        // (يمكن التعامل معها كقائمة فارغة أو تنبيه)
                         updateLocalDatabase(subjects); 
                    } else {
                        updateLocalDatabase(subjects);
                        Toast.makeText(NativeHomeActivity.this, "تم تحديث المواد والصلاحيات ✅", Toast.LENGTH_SHORT).show();
                    }
                    
                } else if (response.code() == 403) {
                     // رفض أمني من الهيدرز
                     handleDeviceMismatch();
                } else {
                    Toast.makeText(NativeHomeActivity.this, "تعذر تحديث المحتوى (Code: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<SubjectEntity>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(NativeHomeActivity.this, "فشل الاتصال لتحديث المحتوى", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ✅ دالة تحديث قاعدة البيانات المحلية
    private void updateLocalDatabase(List<SubjectEntity> subjects) {
        // 1. تنظيف البيانات القديمة
        db.examDao().deleteAll();
        db.videoDao().deleteAll();
        db.pdfDao().deleteAll();
        db.chapterDao().deleteAll();
        db.subjectDao().deleteAll();

        // 2. تجهيز القوائم الجديدة
        List<ChapterEntity> allChapters = new ArrayList<>();
        List<VideoEntity> allVideos = new ArrayList<>();
        List<ExamEntity> allExams = new ArrayList<>();
        List<PdfEntity> allPdfs = new ArrayList<>();

        for (SubjectEntity subject : subjects) {
            // معالجة الشباتر ومحتوياتها
            if (subject.chaptersList != null) {
                for (ChapterEntity chapter : subject.chaptersList) {
                    chapter.subjectId = subject.id; // ربط الشابتر بالمادة
                    allChapters.add(chapter);
                    
                    // أ) الفيديوهات
                    if (chapter.videosList != null) {
                        for (VideoEntity video : chapter.videosList) {
                            video.chapterId = chapter.id; // ربط الفيديو للشابتر
                            allVideos.add(video);
                        }
                    }

                    // ب) ملفات PDF
                    if (chapter.pdfsList != null) {
                        for (PdfEntity pdf : chapter.pdfsList) {
                            pdf.chapterId = chapter.id; // ربط الـ PDF للشابتر
                            allPdfs.add(pdf);
                        }
                    }
                }
            }

            // معالجة الامتحانات
            if (subject.examsList != null) {
                for (ExamEntity exam : subject.examsList) {
                    exam.subjectId = subject.id; // ربط الامتحان بالمادة
                    allExams.add(exam);
                }
            }
        }

        // 3. الحفظ في قاعدة البيانات
        db.subjectDao().insertAll(subjects);
        db.chapterDao().insertAll(allChapters);
        db.videoDao().insertAll(allVideos);
        db.pdfDao().insertAll(allPdfs);
        db.examDao().insertAll(allExams);

        // 4. تحديث الواجهة
        loadLocalData();
    }

    private void handleDeviceMismatch() {
        swipeRefresh.setRefreshing(false);
        clearLocalData();
        
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                .setTitle("⛔ تنبيه أمني (جهاز مختلف)")
                .setMessage("تم ربط هذا الحساب بجهاز آخر \n \n الرجاء التواصل مع الدعم لحل المشكلة ")
                .setCancelable(false)
                .setPositiveButton("تسجيل الخروج", (dialog, which) -> logoutUser())
                .show();
        }
    }

    private void handleFullRevocation() {
        swipeRefresh.setRefreshing(false);
        clearLocalData();
        
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ تنبيه اشتراك")
                .setMessage("تم تغيير بيانات اشتراكك وسحب الصلاحيات الحالية بالكامل.\n\nيرجى مراجعة الإدارة أو تسجيل الدخول بحساب مفعل.")
                .setCancelable(false)
                .setPositiveButton("تسجيل الخروج", (dialog, which) -> logoutUser())
                .show();
        }
    }

    private void clearLocalData() {
        db.examDao().deleteAll();
        db.videoDao().deleteAll();
        db.pdfDao().deleteAll();
        db.chapterDao().deleteAll();
        db.subjectDao().deleteAll();
        loadLocalData();
    }

    private void logoutUser() {
        getSharedPreferences("SecureAppPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(NativeHomeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // 🚀 نظام التحديث الذكي والداخلي (Download Manager)
    // =========================================================================

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
                            String versionStr = String.valueOf(latestVersionCode);
                            runOnUiThread(() -> showUpdateDialog(finalUrl, versionStr));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showUpdateDialog(String apkUrl, String versionStr) {
        if (isFinishing()) return;
        
        File targetFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update_" + versionStr + ".apk");
        String buttonText = (targetFile.exists() && targetFile.length() > 0) ? "تثبيت الآن (جاهز ✅)" : "تحميل وتثبيت ⬇️";

        new AlertDialog.Builder(this)
            .setTitle("تحديث جديد متوفر 🚀")
            .setMessage("يوجد إصدار جديد (" + versionStr + ").\nتم تحسين سرعة التطبيق وإضافة ميزات جديدة.")
            .setCancelable(false)
            .setPositiveButton(buttonText, (dialog, which) -> {
                startInAppUpdate(apkUrl, versionStr);
            })
            .show();
    }

    private void startInAppUpdate(String apkUrl, String versionStr) {
        final String fileName = "update_" + versionStr + ".apk";
        this.currentUpdateFileName = fileName;

        File updateFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        if (updateFile.exists() && updateFile.length() > 0) {
            if (isPackageValid(updateFile)) {
                Toast.makeText(this, "التحديث محمل مسبقاً، جاري التثبيت...", Toast.LENGTH_SHORT).show();
                installApk(updateFile);
                return;
            } else {
                updateFile.delete();
            }
        }

        cleanupOldUpdates(fileName);
        downloadUpdate(apkUrl, fileName, versionStr);
    }

    private void downloadUpdate(String url, String fileName, String versionStr) {
        Toast.makeText(this, "جاري تحميل التحديث (" + versionStr + ")... تابع الإشعارات", Toast.LENGTH_SHORT).show();

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("تحديث التطبيق (" + versionStr + ")");
            request.setDescription("جاري التحميل...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
            
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverMetered(true);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                downloadId = manager.enqueue(request);
            }
        } catch (Exception e) {
            Toast.makeText(this, "فشل بدء التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installApk(File file) {
        try {
            if (!file.exists()) {
                Toast.makeText(this, "ملف التحديث غير موجود!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(this, "الرجاء منح إذن تثبيت التطبيقات للمتابعة", Toast.LENGTH_LONG).show();
                    Intent permissionIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, 
                            Uri.parse("package:" + getPackageName()));
                    startActivity(permissionIntent);
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(
                    this, 
                    getApplicationContext().getPackageName() + ".provider", 
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في التثبيت: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void cleanupOldUpdates(String keepFileName) {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("update_") && f.getName().endsWith(".apk") && !f.getName().equals(keepFileName)) {
                            f.delete();
                        }
                    }
                }
            }
        } catch (Exception e) { }
    }

    private boolean isPackageValid(File file) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void registerDownloadReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            
            if (downloadId == id) {
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                Cursor cursor = manager.query(query);
                
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), currentUpdateFileName);
                        installApk(file);
                        downloadId = -1; 
                    }
                }
                cursor.close();
            }
        }
    };
}
