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
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
    private long downloadId = -1;
    // متغير لتخزين اسم الملف الجاري تحميله حالياً
    private String currentFileName = "update.apk";

    public WebAppInterface(Context c) {
        mContext = c;
        // تسجيل مستقبل لحدث اكتمال التحميل
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    /**
     * ✅ دالة إغلاق التطبيق نهائياً (للإجبار على التحديث)
     */
    @JavascriptInterface
    public void closeApp() {
        if (mContext instanceof MainActivity) {
            ((MainActivity) mContext).runOnUiThread(() -> {
                ((MainActivity) mContext).finishAffinity(); // إغلاق كل الواجهات
                System.exit(0); // إنهاء العملية تماماً
            });
        }
    }

    /**
     * ✅ دالة الجافاسكريبت: تستقبل بيانات الفيديو والتحميل من الويب
     */
    @JavascriptInterface
    public void downloadVideoWithQualities(String youtubeId, String videoTitle, String durationStr, String qualitiesJson, String subjectName, String chapterName) {
        if (youtubeId == null || !youtubeId.matches("[a-zA-Z0-9_-]+")) {
            return; 
        }

        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> {
            try {
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

                    if (url == null || !url.startsWith("https://")) {
                        continue; 
                    }

                    qualityNames.add(q.optString("quality") + "p");
                    qualityUrls.add(url);
                }

                if (qualityUrls.isEmpty()) {
                    Toast.makeText(mContext, "عذراً، الروابط غير مدعومة.", Toast.LENGTH_SHORT).show();
                    return;
                }

                showSelectionDialog(safeTitle, youtubeId, qualityNames, qualityUrls, durationStr, subjectName, chapterName);

            } catch (Exception e) {
                FirebaseCrashlytics.getInstance().recordException(new RuntimeException("WebAppInterface JSON Error", e));
                Toast.makeText(mContext, "Error parsing data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    @JavascriptInterface
    public void closeWebView() {
        if (mContext instanceof android.app.Activity) {
            ((android.app.Activity) mContext).finish();
        }
    }
    // =============================================================
    // 🛠️ نظام التحديث التلقائي (الذكي والمستقر)
    // =============================================================

    /**
     * @param apkUrl رابط التحميل المباشر
     * @param versionStr رقم الإصدار الجديد (مثلاً "320") لتمييز الملف
     */
    @JavascriptInterface
    public void updateApp(String apkUrl, String versionStr) {
        if (apkUrl == null || apkUrl.isEmpty()) return;
        if (!(mContext instanceof MainActivity)) return;

        // تحديد اسم الملف بناءً على الإصدار (مثلاً: update_320.apk)
        final String targetFileName = "update_" + versionStr + ".apk";
        this.currentFileName = targetFileName;

        // تحديد المسار في التخزين الخارجي الخاص بالتطبيق (المسموح لـ DownloadManager)
        File updateFile = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), targetFileName);
        
        // 1. التحقق الذكي: هل الملف موجود وصالح؟
        if (updateFile.exists() && updateFile.length() > 0) {
            if (isPackageValid(updateFile)) {
                ((MainActivity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, "التحديث جاهز، جاري التثبيت...", Toast.LENGTH_SHORT).show();
                    installApk(updateFile);
                });
                return; // تم العثور على الملف، لا داعي للتحميل
            } else {
                // الملف موجود لكنه تالف -> نحذفه
                updateFile.delete();
            }
        }

        // 2. تنظيف الإصدارات القديمة لتوفير المساحة
        cleanupOldUpdates(targetFileName);

        // 3. بدء التحميل عبر DownloadManager
        ((MainActivity) mContext).runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري تحميل التحديث (" + versionStr + ")... تابع الإشعارات", Toast.LENGTH_SHORT).show()
        );

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("تحديث التطبيق (" + versionStr + ")");
            request.setDescription("جاري التحميل...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            // الحفظ في المجلد العام للتطبيق بالاسم الجديد
            request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, targetFileName);
            
            // السماح بالتحميل على كل الشبكات
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                downloadId = manager.enqueue(request);
            }

        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(new Exception("DownloadManager Error: " + e.getMessage()));
            ((MainActivity) mContext).runOnUiThread(() -> 
                Toast.makeText(mContext, "فشل بدء التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    // فحص صلاحية ملف الـ APK
    private boolean isPackageValid(File file) {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    // حذف ملفات التحديث القديمة
    private void cleanupOldUpdates(String keepFileName) {
        try {
            File dir = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        // نحذف أي ملف يبدأ بـ update_ ولا يطابق الاسم الجديد
                        if (f.getName().startsWith("update_") && f.getName().endsWith(".apk") && !f.getName().equals(keepFileName)) {
                            f.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // تجاهل أخطاء التنظيف
        }
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

    // =============================================================
    // 🧹 دالة تنظيف مخلفات التحديث (توفير المساحة)
    // =============================================================
    public static void cleanUpInstalledApks(Context context) {
        new Thread(() -> { // العمل في الخلفية لمنع تهنيج الواجهة
            try {
                // الوصول لمجلد التحميلات الخاص بالتطبيق
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        // جلب رقم إصدار التطبيق الحالي
                        int currentAppVersion = BuildConfig.VERSION_CODE;

                        for (File f : files) {
                            String name = f.getName();
                            // البحث عن الملفات التي تبدأ بـ update_ وتنتهي بـ .apk
                            if (name.startsWith("update_") && name.endsWith(".apk")) {
                                try {
                                    // استخراج الرقم من الاسم: update_105.apk -> 105
                                    String verStr = name.replace("update_", "").replace(".apk", "");
                                    int fileVersion = Integer.parseInt(verStr);
                                    
                                    // الشرط: إذا كان إصدار الملف <= الإصدار الحالي، يعني أنه تم تثبيته أو قديم جداً
                                    if (fileVersion <= currentAppVersion) {
                                        boolean deleted = f.delete();
                                        if (deleted) {
                                            android.util.Log.d("AutoCleanup", "Deleted old APK: " + name);
                                        }
                                    }
                                } catch (Exception e) {
                                    // تجاهل الملفات ذات التسمية الخاطئة
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
}
