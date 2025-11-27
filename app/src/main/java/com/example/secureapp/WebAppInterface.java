package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebAppInterface {

    private Context mContext;

    WebAppInterface(Context c) { mContext = c; }

    /**
     * ✅ دالة الجافاسكريبت: تستقبل بيانات الفيديو والتحميل من الويب
     */
    @JavascriptInterface
    public void downloadVideoWithQualities(String youtubeId, String videoTitle, String durationStr, String qualitiesJson, String subjectName, String chapterName) {
        // [✨ الإضافة الجديدة] خط الدفاع الأول: رفض أي ID يحتوي على رموز مشبوهة
        if (youtubeId == null || !youtubeId.matches("[a-zA-Z0-9_-]+")) {
            // يمكن تسجيل محاولة اختراق هنا إذا أردت
            return; 
        }

        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> {
            try {
                // [✨ تحسين إضافي] تنظيف العنوان أيضاً لمنع مشاكل العرض
                String safeTitle = videoTitle.replaceAll("[<>\"%{};]", ""); 
                
                JSONArray jsonArray = new JSONArray(qualitiesJson);
                List<String> qualityNames = new ArrayList<>();
                List<String> qualityUrls = new ArrayList<>();

                if (jsonArray.length() == 0) {
                    Toast.makeText(mContext, "لا توجد جودات متاحة.", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject q = jsonArray.getJSONObject(i);
                    String url = q.getString("url");

                    // [🔒 أمان] السماح فقط بالروابط المشفرة HTTPS
                    if (url == null || !url.startsWith("https://")) {
                        continue; // تجاهل أي رابط غير آمن
                    }

                    qualityNames.add(q.optString("quality") + "p");
                    qualityUrls.add(url);
                }

                if (qualityUrls.isEmpty()) {
                    Toast.makeText(mContext, "عذراً، الروابط غير مدعومة.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // تمرير البيانات بعد التحقق
                showSelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls, durationStr, subjectName, chapterName);

            } catch (Exception e) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(new RuntimeException("WebAppInterface JSON Error", e));
                Toast.makeText(mContext, "Error parsing data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

  // =============================================================
    // 🛠️ دوال التحديث التلقائي
    // =============================================================

    @JavascriptInterface
    public void updateApp(String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) return;

        if (!(mContext instanceof MainActivity)) return;

        // 1. إشعار فوري للمستخدم بأن العملية بدأت
        ((MainActivity) mContext).runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري بدء التحديث... يرجى الانتظار", Toast.LENGTH_SHORT).show()
        );

        new Thread(() -> {
            try {
                // 2. تجهيز الملف في الكاش
                File file = new File(mContext.getCacheDir(), "update.apk");
                if (file.exists()) file.delete();

                // 3. التحميل
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(apkUrl).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("فشل التحميل: " + response.code());
                    
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        fos.write(response.body().bytes());
                    }
                }

                // 4. التثبيت (العودة للـ Main Thread)
                ((MainActivity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, "تم التحميل! جاري التثبيت...", Toast.LENGTH_SHORT).show();
                    installApk(file);
                });

            } catch (Exception e) {
                e.printStackTrace();
                ((MainActivity) mContext).runOnUiThread(() -> 
                    Toast.makeText(mContext, "فشل التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void installApk(File file) {
        try {
            // التحقق من إذن التثبيت (أندرويد 8+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!mContext.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(mContext, "الرجاء منح إذن تثبيت التطبيقات للمتابعة", Toast.LENGTH_LONG).show();
                    Intent permissionIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, 
                            android.net.Uri.parse("package:" + mContext.getPackageName()));
                    permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(permissionIntent);
                    return;
                }
            }

            // تشغيل ملف APK
            android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                    mContext, 
                    mContext.getApplicationContext().getPackageName() + ".provider", 
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            mContext.startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "خطأ في التثبيت: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    // =============================================================
    // دوال المساعدة (للتحميل)
    // =============================================================

    // دالة عرض قائمة الجودات
    private void showSelectionDialog(String title, String youtubeId, List<String> names, List<String> urls, String duration, String subject, String chapter) {
        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("تحميل: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    
                    String titleWithQuality = title + " (" + names.get(which) + ")";
                    String selectedUrl = urls.get(which);
                    
                    // بدء التحميل
                    startDownloadWorker(youtubeId, titleWithQuality, selectedUrl, duration, subject, chapter);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // دالة بدء الـ Worker
    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration, String subject, String chapter) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl)
                    .putString("duration", duration)
                    
                    // تمرير أسماء المجلدات
                    .putString("subjectName", subject)
                    .putString("chapterName", chapter)
                    
                    .build();

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("download_work_tag")
                    .build();

            WorkManager.getInstance(mContext).enqueue(request);
            
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "تمت الإضافة لقائمة التحميلات", Toast.LENGTH_SHORT).show()
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
