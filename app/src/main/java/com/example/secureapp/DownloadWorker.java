package com.example.secureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// [ ✅✅ جديد: imports لـ OkHttp و JSON ]
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject; // تأكد من وجود هذا

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class DownloadWorker extends Worker {

    private static final String CHANNEL_ID = "download_channel";
    private NotificationManager notificationManager;
    private final Context context;

    public static final String KEY_YOUTUBE_ID = "youtubeId";
    public static final String KEY_VIDEO_TITLE = "videoTitle";

    // [ ✅✅✅ بداية: إصلاح خطأ "cannot find symbol" ]
    // (إضافة المتغيرات التي تحتاجها DownloadsActivity)
    public static final String DOWNLOADS_PREFS = "DownloadPrefs";
    public static final String KEY_DOWNLOADS_SET = "CompletedDownloads";
    // [ ✅✅✅ نهاية: إصلاح خطأ "cannot find symbol" ]

    // [ ✅✅ جديد: رابط السيرفر الوسيط الخاص بك ]
    private static final String API_ENDPOINT = "https://web-production-22bc.up.railway.app/api/get-video-info?youtubeId=";


    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        String youtubeId = getInputData().getString(KEY_YOUTUBE_ID);
        String videoTitle = getInputData().getString(KEY_VIDEO_TITLE); // العنوان الأولي من الجافاسكريبت
        DownloadLogger.logError(context, "DownloadWorker", "doWork() started for: " + videoTitle);

        if (youtubeId == null || videoTitle == null) {
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: youtubeId or videoTitle is null.");
            return Result.failure();
        }

        Data progressData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("progress", "0%")
                .build();
        setProgressAsync(progressData);

        try {
            DownloadLogger.logError(context, "DownloadWorker", "Calling setForegroundAsync...");
            setForegroundAsync(createForegroundInfo("جاري سحب الرابط...", videoTitle, 0, true));
            DownloadLogger.logError(context, "DownloadWorker", "setForegroundAsync completed successfully.");
        
        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "CRITICAL: setForegroundAsync failed: " + e.getMessage());
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");
        int notificationId = getId().hashCode();
        
        // [ ✅✅✅ بداية: الكود النهائي المعتمد على السيرفر ]
        String officialTitle = videoTitle; // (سنستخدم العنوان الأولي كاحتياطي)

        try {
            // 1. الاتصال بالسيرفر الوسيط (Vercel)
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS) // زيادة وقت الانتظار
                    .build();
            
            String apiUrl = API_ENDPOINT + youtubeId;
            DownloadLogger.logError(context, "DownloadWorker", "Requesting streamUrl from Vercel: " + apiUrl);
            
            Request apiRequest = new Request.Builder().url(apiUrl).build();
            String streamUrl;
            
            try (Response apiResponse = client.newCall(apiRequest).execute()) {
                if (!apiResponse.isSuccessful()) {
                    throw new IOException("Server API failed: " + apiResponse.code() + " " + apiResponse.message());
                }
                
                String jsonBody = apiResponse.body().string();
                JSONObject json = new JSONObject(jsonBody);
                streamUrl = json.getString("streamUrl");
                officialTitle = json.getString("videoTitle"); // (جلب العنوان الحقيقي من السيرفر)
            }
            
            DownloadLogger.logError(context, "DownloadWorker", "Got streamUrl successfully. Starting download...");

            // 2. تحميل الرابط المسروق (الذي جاء من السيرفر)
            String userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
            Request videoRequest = new Request.Builder()
                    .url(streamUrl)
                    .addHeader("User-Agent", userAgent)
                    .build();

            Response videoResponse = client.newCall(videoRequest).execute();
            
            if (!videoResponse.isSuccessful()) {
                throw new IOException("Failed to download file (googlevideo): " + videoResponse.code());
            }

            inputStream = videoResponse.body().byteStream();
            long fileLength = videoResponse.body().contentLength();
            
            // 3. إعداد ملف التشفير
            DownloadLogger.logError(context, "DownloadWorker", "Target file path: " + encryptedFile.getAbsolutePath());
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            outputStream = encryptedFileObj.openFileOutput();
            DownloadLogger.logError(context, "DownloadWorker", "Encrypted output stream created. Writing file...");

            // 4. عملية النسخ والتشفير
            byte[] data = new byte[4096];
            int count;
            int lastProgress = -1;
            long total = 0;

            while ((count = inputStream.read(data)) != -1) {
                total += count;
                outputStream.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        Data progressUpdate = new Data.Builder()
                            .putString(KEY_YOUTUBE_ID, youtubeId)
                            .putString(KEY_VIDEO_TITLE, officialTitle)
                            .putString("progress", progress + "%")
                            .build();
                        setProgressAsync(progressUpdate);
                        
                        setForegroundAsync(createForegroundInfo("جاري تحميل الفيديو...", officialTitle, progress, true));
                        lastProgress = progress;
                    }
                }
            }
            outputStream.flush();
            DownloadLogger.logError(context, "DownloadWorker", "File write/encrypt complete.");

            // 5. حفظ البيانات في SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            
            String cleanTitle = officialTitle.replaceAll("[^a-zA-Z0-9.-_ ]", "").trim();
            completed.add(youtubeId + "|" + cleanTitle); 
            
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
            DownloadLogger.logError(context, "DownloadWorker", "Saved to SharedPreferences: " + youtubeId);

            sendNotification(notificationId, "اكتمل التحميل", cleanTitle, 100, false);
            
            Data finalOutput = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, cleanTitle)
                .build();
            return Result.success(finalOutput);

        } catch (Exception e) {
            Log.e("DownloadWorker", "DownloadWorker failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: " + e.getMessage());
            
            if(encryptedFile.exists()) encryptedFile.delete(); 
            
            sendNotification(getId().hashCode(), "فشل التحميل", videoTitle, 0, false);

            Data failureData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                .build();
            return Result.failure(failureData);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close(); 
            } catch (IOException e) {
                DownloadLogger.logError(context, "DownloadWorker", "Failed to close streams: " + e.getMessage());
            }
        }
    }
    
    // (باقي الدوال المساعدة كما هي)
    @NonNull
    private ForegroundInfo createForegroundInfo(String title, String message, int progress, boolean ongoing) {
        int notificationId = getId().hashCode();
        Notification notification = buildNotification(notificationId, title, message, progress, ongoing);
        return new ForegroundInfo(notificationId, notification);
    }
    
    private void sendNotification(int id, String title, String message, int progress, boolean ongoing) {
         Notification notification = buildNotification(id, title, message, progress, ongoing);
         notificationManager.notify(id, notification);
    }

    private Notification buildNotification(int id, String title, String message, int progress, boolean ongoing) {
        Intent intent = new Intent(context, DownloadsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);

        if (ongoing) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, false);
            builder.setAutoCancel(true);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Notifications",
                    NotificationManager.IMPORTANCE_MIN
            );
            notificationManager.createNotificationChannel(channel);
        }
    }
}
