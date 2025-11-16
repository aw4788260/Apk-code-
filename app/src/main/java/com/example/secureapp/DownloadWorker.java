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
             // لا تتوقف، حاول إكمال التحميل في الخلفية (قد يقتله النظام)
        }

        try {
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

            String videoUrl = format.url();
            String officialTitle = video.details().title(); 
            
            downloadAndEncryptFile(videoUrl, officialTitle);

            return Result.success(outputData);

        } catch (Exception e) {
            Log.e("DownloadWorker", "Youtube-Downloader-Java failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: " + e.getMessage());
            sendNotification(getId().hashCode(), "فشل سحب الرابط", e.getMessage() != null ? e.getMessage() : "Error", 0, false);

            Data failureData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("error", e.getMessage() != null ? e.getMessage() : "Unknown error")
                .build();
            return Result.failure(failureData);
        }
    }

    private void downloadAndEncryptFile(String url, String videoTitle) throws IOException {
        // ...
        // [ ✅✅✅ هذا هو الإصلاح لخطأ 403 ]
        // (محاكاة متصفح لتجنب الحظر)
        String userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent) // <-- الإضافة الحاسمة
                .build();
        
        DownloadLogger.logError(context, "DownloadWorker", "Starting file download (with User-Agent)...");
        okhttp3.Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            DownloadLogger.logError(context, "DownloadWorker", "Download failed. Response code: " + response.code());
            throw new IOException("Failed to download file: " + response);
        }
        DownloadLogger.logError(context, "DownloadWorker", "File download response OK. Starting encryption...");

        String youtubeId = getInputData().getString(KEY_YOUTUBE_ID);
        if (youtubeId == null || youtubeId.isEmpty()) {
            throw new IOException("YouTube ID is missing, cannot create encrypted file.");
        }

        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");
        DownloadLogger.logError(context, "DownloadWorker", "Target file path: " + encryptedFile.getAbsolutePath());

        InputStream inputStream = null;
        OutputStream outputStream = null;
        long fileLength = response.body().contentLength();
        long total = 0;
        int notificationId = getId().hashCode();

        try {
            inputStream = response.body().byteStream();

            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            outputStream = encryptedFileObj.openFileOutput();
            DownloadLogger.logError(context, "DownloadWorker", "Encrypted output stream created. Writing file...");

            byte[] data = new byte[4096];
            int count;
            int lastProgress = -1;

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
            DownloadLogger.logError(context, "DownloadWorker", "File write/encrypt complete.");

            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            
            String cleanTitle = videoTitle.replaceAll("[^a-zA-Z0-9.-_ ]", "").trim();
            completed.add(youtubeId + "|" + cleanTitle); 
            
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
            DownloadLogger.logError(context, "DownloadWorker", "Saved to SharedPreferences: " + youtubeId);

            sendNotification(notificationId, "اكتمل التحميل", cleanTitle, 100, false);

        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "downloadFile() failed during write/encrypt: " + e.getMessage());
             if(encryptedFile.exists()) encryptedFile.delete(); 
             throw new IOException(e.getMessage()); 
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (response.body() != null) response.body().close();
        }
    }
    
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
                    // [ ✅✅✅ هذا هو الإصلاح ]
                    // استخدام أقل أولوية ممكنة (مطلوبة لـ dataSync)
                    NotificationManager.IMPORTANCE_MIN
            );
            notificationManager.createNotificationChannel(channel);
        }
    }
}
