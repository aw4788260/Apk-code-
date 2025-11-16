package com.example.secureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// [ ✅✅ جديد: imports لـ OkHttp ]
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;


public class DownloadWorker extends Worker {

    private static final String CHANNEL_ID = "download_channel";
    private NotificationManager notificationManager;
    private final Context context;

    public static final String KEY_YOUTUBE_ID = "youtubeId";
    public static final String KEY_VIDEO_TITLE = "videoTitle";

    // [ ✅✅ جديد: رابط السيرفر الوسيط الخاص بك ]
    private static final String API_ENDPOINT = "https://secured-bot.vercel.app/api/secure/get-video-id?youtubeId=";


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
        String videoTitle = getInputData().getString(KEY_VIDEO_TITLE);
        DownloadLogger.logError(context, "DownloadWorker", "TEST: doWork() started for: " + videoTitle);

        try {
            setForegroundAsync(createForegroundInfo("جاري اختبار السيرفر...", videoTitle, 0, true));
        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "CRITICAL: setForegroundAsync failed: " + e.getMessage());
        }

        // [ ✅✅✅ بداية: كود الاختبار ]
        OkHttpClient client = new OkHttpClient();
        String apiUrl = API_ENDPOINT + youtubeId;
        Request apiRequest = new Request.Builder().url(apiUrl).build();
        DownloadLogger.logError(context, "DownloadWorker", "TEST: Calling Vercel API: " + apiUrl);

        try (Response apiResponse = client.newCall(apiRequest).execute()) {
            
            String responseBody = apiResponse.body().string(); // قراءة الرد
            
            if (!apiResponse.isSuccessful()) {
                DownloadLogger.logError(context, "DownloadWorker", "TEST FAILED: Server API failed: " + apiResponse.code());
                DownloadLogger.logError(context, "DownloadWorker", "Response: " + responseBody);
                sendNotification(getId().hashCode(), "فشل الاختبار", "السيرفر فشل", 0, false);
                return Result.failure();
            }

            // [ ✅✅✅ نجاح الاختبار ]
            DownloadLogger.logError(context, "DownloadWorker", "TEST SUCCESS: Server Response: " + responseBody);
            DownloadLogger.logError(context, "DownloadWorker", ">>> اذهب الآن وافحص لوج Vercel! <<<");
            
            sendNotification(getId().hashCode(), "نجح الاختبار", "افحص لوج Vercel", 100, false);
            
            // (سنرجع نجاحاً مؤقتاً لنوقف المهمة)
            Data outputData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle) // (سنحتاج لتعديل هذا لاحقاً)
                .build();
            return Result.success(outputData);

        } catch (Exception e) {
            Log.e("DownloadWorker", "TEST FAILED", e);
            DownloadLogger.logError(context, "DownloadWorker", "TEST FAILED (Exception): " + e.getMessage());
            sendNotification(getId().hashCode(), "فشل الاختبار", e.getMessage(), 0, false);
            return Result.failure();
        }
        // [ ✅✅✅ نهاية: كود الاختبار ]
    }
    
    // (الدوال المساعدة كما هي)
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
