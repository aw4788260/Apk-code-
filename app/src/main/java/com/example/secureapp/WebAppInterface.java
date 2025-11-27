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
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WebAppInterface {

    private Context mContext;
    private long downloadId = -1;
    // متغير لتخزين اسم الملف الجاري تحميله حالياً لضمان التثبيت الصحيح عند الانتهاء
    private String currentFileName = "update.apk";

    WebAppInterface(Context c) {
        mContext = c;
        // تسجيل مستقبل لحدث اكتمال التحميل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
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
    // 🛠️ نظام التحديث التلقائي (الذكي والمستقر)
    // =============================================================

  // ✅ دالة جديدة: إغلاق التطبيق نهائياً (للإجبار على التحديث)
    @JavascriptInterface
    public void closeApp() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).runOnUiThread(() -> {
                ((MainActivity) mContext).finishAffinity(); // إغلاق كل الواجهات
                System.exit(0); // قتل العملية تماماً
            });
        }
    }

    // ✅ استبدل دالة updateApp القديمة بهذه النسخة (التي تستقبل رقم الإصدار)
    @JavascriptInterface
    public void updateApp(String apkUrl, String versionStr) {
        if (apkUrl == null || apkUrl.isEmpty()) return;
        if (!(mContext instanceof MainActivity)) return;

        // تسمية الملف برقم الإصدار لضمان الدقة (مثلاً update_320.apk)
        final String targetFileName = "update_" + versionStr + ".apk";
        // تحديث المتغير العام لاسم الملف (تأكد من تعريف private String currentFileName = "update.apk"; في بداية الكلاس)
        // this.currentFileName = targetFileName; // (عليك إضافة هذا المتغير في أعلى الكلاس)

        File updateFile = new File(mContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), targetFileName);
        
        // 1. الفحص الذكي: هل الملف موجود وصالح؟
        if (updateFile.exists() && updateFile.length() > 0) {
            if (isPackageValid(updateFile)) {
                ((MainActivity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, "التحديث جاهز، جاري التثبيت...", Toast.LENGTH_SHORT).show();
                    installApk(updateFile);
                });
                return;
            } else {
                updateFile.delete(); // ملف تالف، نحذفه
            }
        }

        // 2. تنظيف أي تحديثات قديمة لتوفير المساحة
        cleanupOldUpdates(targetFileName);

        // 3. بدء التحميل
        ((MainActivity) mContext).runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري تحميل التحديث (" + versionStr + ")... تابع الإشعارات", Toast.LENGTH_SHORT).show()
        );

        // ... (باقي كود التحميل عبر DownloadManager كما هو، لكن تأكد من استخدام targetFileName كاسم للملف)
        // عند استخدام request.setDestinationInExternalFilesDir تأكد من تمرير targetFileName
        
        // (للإيجاز، استخدم نفس منطق DownloadManager الذي لديك لكن مع الاسم الجديد)
        startDownloadManagerRequest(apkUrl, targetFileName, versionStr);
    }

    // --- دوال مساعدة جديدة (أضفها في نهاية الكلاس) ---

    // دالة مساعدة لبدء DownloadManager (لترتيب الكود)
    private void startDownloadManagerRequest(String url, String fileName, String version) {
        try {
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
            request.setTitle("تحديث التطبيق (" + version + ")");
            request.setDescription("جاري التحميل...");
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(mContext, android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            // ... باقي إعدادات الشبكة
            
            android.app.DownloadManager manager = (android.app.DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                // downloadId = manager.enqueue(request); // تأكد من تعريف downloadId في أعلى الكلاس
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isPackageValid(File file) {
        try {
            android.content.pm.PackageManager pm = mContext.getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            return info != null;
        } catch (Exception e) { return false; }
    }

    private void cleanupOldUpdates(String keepFileName) {
        try {
            File dir = mContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
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
        } catch (Exception e) {}
    }

    // مستقبل لحدث انتهاء التحميل
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
                        
                        // التثبيت فوراً باستخدام الاسم المحفوظ
                        File file = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), currentFileName);
                        installApk(file);
                        
                        downloadId = -1; 
                    }
                }
                cursor.close();
            }
        }
    };

    private void installApk(File file) {
        try {
            if (!file.exists()) {
                Toast.makeText(mContext, "ملف التحديث غير موجود!", Toast.LENGTH_SHORT).show();
                return;
            }

            // التحقق من إذن التثبيت (أندرويد 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!mContext.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(mContext, "الرجاء منح إذن تثبيت التطبيقات للمتابعة", Toast.LENGTH_LONG).show();
                    Intent permissionIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, 
                            Uri.parse("package:" + mContext.getPackageName()));
                    permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(permissionIntent);
                    return;
                }
            }

            // تجهيز الـ URI الآمن
            Uri apkUri = FileProvider.getUriForFile(
                    mContext, 
                    mContext.getApplicationContext().getPackageName() + ".provider", 
                    file
            );

            // إطلاق أمر التثبيت
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
