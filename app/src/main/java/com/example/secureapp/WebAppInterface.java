package com.example.secureapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WebAppInterface {

    private Context mContext;
    // ثوابت للإشعارات
    private static final String UPDATE_CHANNEL_ID = "app_update_channel";
    private static final int NOTIFICATION_ID = 1001;

    WebAppInterface(Context c) {
        mContext = c;
        createNotificationChannel(); // إنشاء القناة عند تهيئة الكلاس
    }

    /**
     * ✅ دالة الجافاسكريبت: تستقبل بيانات الفيديو والتحميل من الويب
     */
    @JavascriptInterface
    public void downloadVideoWithQualities(String youtubeId, String videoTitle, String durationStr, String qualitiesJson, String subjectName, String chapterName) {
        // [✨ الإضافة الجديدة] خط الدفاع الأول: رفض أي ID يحتوي على رموز مشبوهة
        if (youtubeId == null || !youtubeId.matches("[a-zA-Z0-9_-]+")) {
            return; 
        }

        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> {
            try {
                // [✨ تحسين إضافي] تنظيف العنوان
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
                showSelectionDialog(safeTitle, youtubeId, qualityNames, qualityUrls, durationStr, subjectName, chapterName);

            } catch (Exception e) {
                // تسجيل الخطأ في Firebase
                FirebaseCrashlytics.getInstance().recordException(new RuntimeException("WebAppInterface JSON Error", e));
                Toast.makeText(mContext, "Error parsing data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // =============================================================
    // 🛠️ دوال التحديث التلقائي (المعدلة مع Progress Bar و Firebase)
    // =============================================================

    @JavascriptInterface
    public void updateApp(String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) return;

        if (!(mContext instanceof MainActivity)) return;

        // 1. إشعار فوري للمستخدم
        ((MainActivity) mContext).runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري بدء التحديث... تابع شريط الإشعارات", Toast.LENGTH_SHORT).show()
        );

        // تشغيل في الخلفية
        new Thread(() -> {
            // إعداد الإشعار
            NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, UPDATE_CHANNEL_ID)
                    .setContentTitle("تحديث التطبيق")
                    .setContentText("جاري التحميل...")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true) // لا يمكن حذفه أثناء التحميل
                    .setOnlyAlertOnce(true)
                    .setProgress(100, 0, false);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

            try {
                // 2. تجهيز الملف
                File file = new File(mContext.getCacheDir(), "update.apk");
                if (file.exists()) file.delete();

                // 3. التحميل
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(apkUrl).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("فشل التحميل: كود " + response.code());
                    
                    InputStream inputStream = response.body().byteStream();
                    long totalBytes = response.body().contentLength();
                    FileOutputStream fos = new FileOutputStream(file);

                    byte[] buffer = new byte[8 * 1024]; // 8KB
                    int bytesRead;
                    long downloadedBytes = 0;
                    int lastProgress = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        // حساب وتحديث النسبة المئوية
                        if (totalBytes > 0) {
                            int progress = (int) ((downloadedBytes * 100) / totalBytes);
                            // نحدث الإشعار فقط إذا زادت النسبة (لتقليل الضغط على النظام)
                            if (progress > lastProgress) {
                                builder.setProgress(100, progress, false);
                                builder.setContentText("جاري التحميل: " + progress + "%");
                                notificationManager.notify(NOTIFICATION_ID, builder.build());
                                lastProgress = progress;
                            }
                        }
                    }
                    fos.flush();
                    fos.close();
                    inputStream.close();
                }

                // 4. اكتمال التحميل
                builder.setContentText("تم التحميل. جاري التثبيت...")
                       .setProgress(0, 0, false)
                       .setOngoing(false);
                notificationManager.notify(NOTIFICATION_ID, builder.build());

                // انتظار بسيط ثم إزالة الإشعار
                Thread.sleep(500);
                notificationManager.cancel(NOTIFICATION_ID);

                // 5. التثبيت (العودة للـ Main Thread)
                ((MainActivity) mContext).runOnUiThread(() -> installApk(file));

            } catch (Exception e) {
                e.printStackTrace();
                // تسجيل الخطأ في Firebase
                FirebaseCrashlytics.getInstance().recordException(new Exception("Update Failed: " + e.getMessage()));

                // تحديث الإشعار ليظهر الفشل
                builder.setContentTitle("فشل التحديث")
                       .setContentText("حدث خطأ أثناء التحميل")
                       .setOngoing(false)
                       .setProgress(0, 0, false);
                notificationManager.notify(NOTIFICATION_ID, builder.build());

                ((MainActivity) mContext).runOnUiThread(() -> 
                    Toast.makeText(mContext, "فشل التحديث: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    // إنشاء قناة الإشعارات (مطلوب لأندرويد 8+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "تحديثات التطبيق",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("إشعارات تحميل التحديثات الجديدة");
            NotificationManager manager = mContext.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void installApk(File file) {
        try {
            // التحقق من إذن التثبيت (أندرويد 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            android.net.Uri apkUri = FileProvider.getUriForFile(
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
            FirebaseCrashlytics.getInstance().recordException(new Exception("Install APK Error: " + e.getMessage()));
            Toast.makeText(mContext, "خطأ في التثبيت: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // =============================================================
    // دوال المساعدة (للتحميل العادي - الفيديوهات)
    // =============================================================

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

    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration, String subject, String chapter) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl)
                    .putString("duration", duration)
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
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }
}
