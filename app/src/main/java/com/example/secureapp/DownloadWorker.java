package com.example.secureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // [ ✅ إضافة ]
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

import java.io.File;
import java.io.FileOutputStream;
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

        try {
            setForegroundAsync(createForegroundInfo("جاري سحب الرابط...", videoTitle, 0, true));
        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "setForegroundAsync failed: " + e.getMessage()); // [ ✅ لوج ]
             // (قد يكون بسبب عدم وجود إذن الإشعارات، لكن سنكمل المحاولة)
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
            String officialTitle = video.details().title();

            downloadFile(videoUrl, officialTitle);

            return Result.success();

        } catch (Exception e) {
            Log.e("DownloadWorker", "Youtube-Downloader-Java failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "doWork() failed: " + e.getMessage()); // [ ✅ لوج ]
            sendNotification(getId().hashCode(), "فشل سحب الرابط", e.getMessage() != null ? e.getMessage() : "Error", 0, false);
            return Result.failure();
        }
    }

    private void downloadFile(String url, String fileName) throws IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        DownloadLogger.logError(context, "DownloadWorker", "Starting file download..."); // [ ✅ لوج ]
        okhttp3.Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            DownloadLogger.logError(context, "DownloadWorker", "Download failed. Response code: " + response.code()); // [ ✅ لوج ]
            throw new IOException("Failed to download file: " + response);
        }
        DownloadLogger.logError(context, "DownloadWorker", "File download response OK. Starting save..."); // [ ✅ لوج ]

        String cleanFileName = fileName.replaceAll("[^a-zA-Z0-9.-_ ]", "").trim() + ".mp4";

        InputStream inputStream = null;
        OutputStream outputStream = null;
        long fileLength = response.body().contentLength();
        long total = 0;
        int notificationId = getId().hashCode();

        try {
            inputStream = response.body().byteStream();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SecureApp");
                Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                outputStream = context.getContentResolver().openOutputStream(uri);
            } else {
                File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SecureApp");
                if (!storageDir.exists()) {
                    storageDir.mkdirs();
                }
                File file = new File(storageDir, cleanFileName);
                outputStream = new FileOutputStream(file);
            }

            if (outputStream == null) {
                throw new IOException("Failed to create output stream.");
            }
            DownloadLogger.logError(context, "DownloadWorker", "Output stream created. Writing file..."); // [ ✅ لوج ]

            byte[] data = new byte[4096];
            int count;
            int lastProgress = -1;

            while ((count = inputStream.read(data)) != -1) {
                total += count;
                outputStream.write(data, 0, count);

                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        setForegroundAsync(createForegroundInfo("جاري تحميل الفيديو...", cleanFileName, progress, true));
                        lastProgress = progress;
                    }
                }
            }
            outputStream.flush();
            DownloadLogger.logError(context, "DownloadWorker", "File write complete."); // [ ✅ لوج ]

            // [ ✅✅✅ بداية: الإصلاح الحقيقي ]
            // (حفظ حالة الاكتمال في SharedPreferences لتقرأها شاشة التحميلات)
            String youtubeId = getInputData().getString(KEY_YOUTUBE_ID);
            if (youtubeId != null && !youtubeId.isEmpty()) {
                SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
                Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
                completed.add(youtubeId + "|" + cleanFileName); // حفظ كـ "ID|Title"
                prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
                DownloadLogger.logError(context, "DownloadWorker", "Saved to SharedPreferences: " + youtubeId); // [ ✅ لوج ]
            } else {
                DownloadLogger.logError(context, "DownloadWorker", "ERROR: youtubeId was null, cannot save to SharedPreferences."); // [ ✅ لوج ]
            }
            // [ ✅✅✅ نهاية: الإصلاح الحقيقي ]

            sendNotification(notificationId, "اكتمل التحميل", cleanFileName, 100, false);

        } catch (Exception e) {
             DownloadLogger.logError(context, "DownloadWorker", "downloadFile() failed during write: " + e.getMessage()); // [ ✅ لوج ]
             throw e; // (إعادة رمي الخطأ ليتم الإمساك به في doWork)
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
