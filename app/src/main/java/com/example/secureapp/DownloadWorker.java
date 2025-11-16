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

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.client.ClientType;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

// [ ✅✅✅ إضافة الـ import الصحيح ]
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload;
// (تم حذف import الخاص بـ OkHttp لأنه لم يعد مطلوباً)

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

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
        DownloadLogger.logError(context, "DownloadWorker", "doWork() started for: " + videoTitle);

        if (youtubeId == null || videoTitle == null) {
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: youtubeId or videoTitle is null.");
            return Result.failure();
        }

        Data outputData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .build();

        try {
            DownloadLogger.logError(context, "DownloadWorker", "Calling setForegroundAsync...");
            setForegroundAsync(createForegroundInfo("جاري سحب الرابط...", videoTitle, 0, true));
            DownloadLogger.logError(context, "DownloadWorker", "setForegroundAsync completed successfully.");
        
        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "CRITICAL: setForegroundAsync failed: " + e.getMessage());
             // (سنكمل المحاولة)
        }

        // (متغيرات التشفير والتحميل)
        OutputStream outputStream = null; // (لم نعد بحاجة لـ InputStream هنا)
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");
        int notificationId = getId().hashCode();

        try {
            // 1. إعداد المكتبة (كما كان)
            YoutubeDownloader downloader = new YoutubeDownloader();
            RequestVideoInfo request = new RequestVideoInfo(youtubeId).clientType(ClientType.MWEB);

            DownloadLogger.logError(context, "DownloadWorker", "Requesting video info (using MWEB client)...");
            Response<VideoInfo> response = downloader.getVideoInfo(request);
            VideoInfo video = response.data();

            if (video == null || video.videoWithAudioFormats().isEmpty()) {
                throw new IOException("Video info not found or no formats available.");
            }
            DownloadLogger.logError(context, "DownloadWorker", "Video info fetched.");

            VideoFormat format = video.bestVideoWithAudioFormat();
            if (format == null) {
                format = video.videoWithAudioFormats().get(0);
            }
            if (format == null) {
                throw new IOException("No video with audio format found.");
            }
            
            String officialTitle = video.details().title(); 
            
            // --- [ ✅✅✅ هذا هو الإصلاح لخطأ 403 و خطأ Compilation ] ---
            
            DownloadLogger.logError(context, "DownloadWorker", "Target file path: " + encryptedFile.getAbsolutePath());

            // 2. إعداد ملف التشفير (يجب إعداده "قبل" بدء التحميل)
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            // 3. فتح "Stream الكتابة" الخاص بالملف المشفر
            outputStream = encryptedFileObj.openFileOutput();
            DownloadLogger.logError(context, "DownloadWorker", "Encrypted output stream created. Preparing download request...");

            // 4. إعداد "طلب التحميل" من المكتبة، وتمرير Stream التشفير إليه
            RequestVideoStreamDownload streamRequest = new RequestVideoStreamDownload(format, outputStream)
                .callback(progress -> {
                    // (هذه الدالة سيتم استدعاؤها من المكتبة أثناء التحميل)
                    int progressPercent = (int) progress.getProgressPercentage();
                    try {
                        setForegroundAsync(createForegroundInfo("جاري تحميل الفيديو...", officialTitle, progressPercent, true));
                    } catch (Exception e) {
                        // (لا يمكن عمل شيء حيال ذلك الآن، أكمل التحميل)
                    }
                });

            // 5. بدء التحميل (المكتبة ستقوم بالتحميل والكتابة مباشرة في outputStream المشفر)
            DownloadLogger.logError(context, "DownloadWorker", "Starting file download via library stream...");
            Response<Void> streamResponse = downloader.downloadVideoStream(streamRequest);

            if (!streamResponse.ok()) {
                throw new IOException("Library download failed: " + streamResponse.error());
            }
            
            DownloadLogger.logError(context, "DownloadWorker", "File write/encrypt complete.");
            // --- [ ✅✅✅ نهاية الإصلاح ] ---

            // 6. حفظ البيانات في SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            
            String cleanTitle = officialTitle.replaceAll("[^a-zA-Z0-9.-_ ]", "").trim();
            completed.add(youtubeId + "|" + cleanTitle); 
            
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
            DownloadLogger.logError(context, "DownloadWorker", "Saved to SharedPreferences: " + youtubeId);

            sendNotification(notificationId, "اكتمل التحميل", cleanTitle, 100, false);
            
            return Result.success(outputData);

        } catch (Exception e) {
            Log.e("DownloadWorker", "DownloadWorker failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: " + e.getMessage());
            
            // (حذف الملف الفاشل إذا كان موجوداً)
            if(encryptedFile.exists()) encryptedFile.delete(); 
            
            sendNotification(getId().hashCode(), "فشل التحميل", videoTitle, 0, false);

            Data failureData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                .build();
            return Result.failure(failureData);
        } finally {
            // 7. إغلاق الـ Streams
            try {
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                DownloadLogger.logError(context, "DownloadWorker", "Failed to close streams: " + e.getMessage());
            }
        }
    }

    // (تم حذف دالة downloadAndEncryptFile القديمة)
    
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
