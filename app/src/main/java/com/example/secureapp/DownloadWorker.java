package com.example.secureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // [ ✅ إضافة ]
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
// [ ✅✅ إضافات هامة للتشفير ]
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data; // [ ✅ إضافة ]
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

import java.io.File; // [ ✅ إضافة ]
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet; // [ ✅ إضافة ]
import java.util.Set; // [ ✅ إضافة ]

public class DownloadWorker extends Worker {

    private static final String CHANNEL_ID = "download_channel";
    private NotificationManager notificationManager;
    private final Context context;

    public static final String KEY_YOUTUBE_ID = "youtubeId";
    public static final String KEY_VIDEO_TITLE = "videoTitle";
    public static final String DOWNLOADS_PREFS = "DownloadPrefs";
    public static final String KEY_DOWNLOADS_SET = "CompletedDownloads";


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
        DownloadLogger.logError(context, "DownloadWorker", "doWork() started for: " + videoTitle); // [ ✅ لوج ]

        if (youtubeId == null || videoTitle == null) {
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: youtubeId or videoTitle is null."); // [ ✅ لوج ]
            return Result.failure();
        }

        // [ ✅✅ تعديل: تجهيز بيانات الإخراج (النجاح) مقدماً ]
        Data outputData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .build();

        try {
            setForegroundAsync(createForegroundInfo("جاري سحب الرابط...", videoTitle, 0, true));
        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "setForegroundAsync failed: " + e.getMessage()); // [ ✅ لوج ]
        }

        try {
            YoutubeDownloader downloader = new YoutubeDownloader();
            RequestVideoInfo request = new RequestVideoInfo(youtubeId);
            DownloadLogger.logError(context, "DownloadWorker", "Requesting video info..."); // [ ✅ لوج ]
            Response<VideoInfo> response = downloader.getVideoInfo(request);
            VideoInfo video = response.data();

            if (video == null || video.videoWithAudioFormats().isEmpty()) {
                throw new IOException("Video info not found or no formats available.");
            }
            DownloadLogger.logError(context, "DownloadWorker", "Video info fetched."); // [ ✅ لوج ]

            VideoFormat format = video.bestVideoWithAudioFormat();
            if (format == null) {
                format = video.videoWithAudioFormats().get(0);
            }

            if (format == null) {
                throw new IOException("No video with audio format found.");
            }

            String videoUrl = format.url();
            // [ ✅ تعديل: سنستخدم العنوان الأصلي لـ YouTube ]
            String officialTitle = video.details().title(); 
            
            // [ ✅✅✅ هذا هو التعديل الجوهري ]
            // (سيقوم الآن بالحفظ والتشفير في المكان الصحيح)
            downloadAndEncryptFile(videoUrl, officialTitle);

            // [ ✅✅ تعديل: إرجاع بيانات النجاح ]
            return Result.success(outputData);

        } catch (Exception e) {
            Log.e("DownloadWorker", "Youtube-Downloader-Java failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: " + e.getMessage()); // [ ✅ لوج ]
            sendNotification(getId().hashCode(), "فشل سحب الرابط", e.getMessage() != null ? e.getMessage() : "Error", 0, false);

            // [ ✅✅ تعديل: إرجاع بيانات الفشل (مع الخطأ) ]
            Data failureData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                .build();
            return Result.failure(failureData);
        }
    }

    /**
     * [ ✅✅✅ دالة معدلة بالكامل ]
     * تقوم بالتحميل والتشفير والحفظ في التخزين الداخلي
     */
    private void downloadAndEncryptFile(String url, String videoTitle) throws IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        DownloadLogger.logError(context, "DownloadWorker", "Starting file download..."); // [ ✅ لوج ]
        okhttp3.Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            DownloadLogger.logError(context, "DownloadWorker", "Download failed. Response code: " + response.code()); // [ ✅ لوج ]
            throw new IOException("Failed to download file: " + response);
        }
        DownloadLogger.logError(context, "DownloadWorker", "File download response OK. Starting encryption..."); // [ ✅ لوج ]

        // [ ✅✅✅ بداية: الكود الجديد للتشفير والحفظ الداخلي ]
        String youtubeId = getInputData().getString(KEY_YOUTUBE_ID);
        if (youtubeId == null || youtubeId.isEmpty()) {
            throw new IOException("YouTube ID is missing, cannot create encrypted file.");
        }

        // 1. تحديد المسار: التخزين الداخلي (الآمن)
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");
        DownloadLogger.logError(context, "DownloadWorker", "Target file path: " + encryptedFile.getAbsolutePath()); // [ ✅ لوج ]

        InputStream inputStream = null;
        OutputStream outputStream = null;
        long fileLength = response.body().contentLength();
        long total = 0;
        int notificationId = getId().hashCode();

        try {
            inputStream = response.body().byteStream();

            // 2. إعداد مفتاح التشفير
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            // 3. إعداد ملف التشفير
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            // 4. فتح ستريم للكتابة المشفرة
            outputStream = encryptedFileObj.openFileOutput();
            DownloadLogger.logError(context, "DownloadWorker", "Encrypted output stream created. Writing file..."); // [ ✅ لوج ]

            byte[] data = new byte[4096];
            int count;
            int lastProgress = -1;

            // 5. تحميل الملف وكتابته مشفراً في نفس الوقت
            while ((count = inputStream.read(data)) != -1) {
                total += count;
                outputStream.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        setForegroundAsync(createForegroundInfo("جاري تحميل الفيديو...", videoTitle, progress, true));
                        lastProgress = progress;
                    }
                }
            }
            outputStream.flush();
            DownloadLogger.logError(context, "DownloadWorker", "File write/encrypt complete."); // [ ✅ لوج ]

            // 6. حفظ حالة الاكتمال في SharedPreferences (نستخدم videoTitle هنا)
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            
            // تنظيف العنوان قبل حفظه (لأننا سنعرضه في القائمة)
            String cleanTitle = videoTitle.replaceAll("[^a-zA-Z0-9.-_ ]", "").trim();
            completed.add(youtubeId + "|" + cleanTitle); // حفظ كـ "ID|Title"
            
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
            DownloadLogger.logError(context, "DownloadWorker", "Saved to SharedPreferences: " + youtubeId); // [ ✅ لوج ]

            sendNotification(notificationId, "اكتمل التحميل", cleanTitle, 100, false);

        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "downloadFile() failed during write/encrypt: " + e.getMessage()); // [ ✅ لوج ]
             if(encryptedFile.exists()) encryptedFile.delete(); // [ ✅ تنظيف الملف الفاسد ]
             throw new IOException(e.getMessage()); // (إعادة رمي الخطأ)
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (response.body() != null) response.body().close();
        }
    }
    
    // (باقي دوال الإشعارات كما هي)
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
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }
}
