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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        
        // ✅ هذا هو الرابط المباشر للجودة المختارة (يأتي من WebAppInterface)
        String specificUrl = getInputData().getString("specificUrl");

        if (youtubeId == null || videoTitle == null) return Result.failure();

        setForegroundAsync(createForegroundInfo("جاري التحميل...", videoTitle, 0, true));

        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");
        int notificationId = getId().hashCode();
        OutputStream outputStream = null;

        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            // تجهيز التشفير
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile, context, masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();
            outputStream = encryptedFileObj.openFileOutput();

            // التحميل المباشر باستخدام الرابط المختار
            if (specificUrl != null && specificUrl.contains(".m3u8")) {
                DownloadLogger.logError(context, "DownloadWorker", "Downloading HLS from specific URL");
                downloadHlsSegments(client, specificUrl, outputStream, youtubeId, videoTitle);
            } else if (specificUrl != null) {
                DownloadLogger.logError(context, "DownloadWorker", "Downloading Direct File from specific URL");
                downloadDirectFile(client, specificUrl, outputStream, youtubeId, videoTitle);
            } else {
                throw new IOException("No download URL provided");
            }
            
            outputStream.flush();
            outputStream.close();
            outputStream = null;

            saveCompletion(youtubeId, videoTitle);
            sendNotification(notificationId, "اكتمل التحميل", videoTitle, 100, false);
            
            Data finalOutput = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .build();
            return Result.success(finalOutput);

        } catch (Exception e) {
            Log.e("DownloadWorker", "Failed", e);
            DownloadLogger.logError(context, "DownloadWorker", "Error: " + e.getMessage());
            if(encryptedFile.exists()) encryptedFile.delete();
            sendNotification(notificationId, "فشل التحميل", videoTitle, 0, false);
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        } finally {
            if (outputStream != null) {
                try { outputStream.close(); } catch (IOException e) {}
            }
        }
    }

    private void downloadHlsSegments(OkHttpClient client, String m3u8Url, OutputStream encryptedOutput, String id, String title) throws IOException {
        Request playlistRequest = new Request.Builder().url(m3u8Url).build();
        List<String> segmentUrls = new ArrayList<>();
        String baseUrl = "";
        if (m3u8Url.contains("/")) {
            baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
        }

        try (Response response = client.newCall(playlistRequest).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to fetch m3u8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.startsWith("http")) segmentUrls.add(line);
                    else segmentUrls.add(baseUrl + line);
                }
            }
        }

        if (segmentUrls.isEmpty()) throw new IOException("Empty m3u8 playlist");

        int totalSegments = segmentUrls.size();
        byte[] buffer = new byte[8192];
        
        for (int i = 0; i < totalSegments; i++) {
            String segUrl = segmentUrls.get(i);
            Request segRequest = new Request.Builder().url(segUrl).build();
            try (Response segResponse = client.newCall(segRequest).execute()) {
                if (!segResponse.isSuccessful()) throw new IOException("Failed segment: " + i);
                InputStream segInput = segResponse.body().byteStream();
                int count;
                while ((count = segInput.read(buffer)) != -1) {
                    encryptedOutput.write(buffer, 0, count);
                }
            }
            int progress = (int) (((float) (i + 1) / totalSegments) * 100);
            updateProgress(id, title, progress);
        }
    }
    
    private void downloadDirectFile(OkHttpClient client, String url, OutputStream encryptedOutput, String id, String title) throws IOException {
        Request request = new Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed direct download");
            InputStream inputStream = response.body().byteStream();
            long fileLength = response.body().contentLength();
            byte[] data = new byte[8192];
            int count;
            long total = 0;
            int lastProgress = 0;
            while ((count = inputStream.read(data)) != -1) {
                encryptedOutput.write(data, 0, count);
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        updateProgress(id, title, progress);
                        lastProgress = progress;
                    }
                }
            }
        }
    }

    private void updateProgress(String id, String title, int progress) {
        if (progress % 5 == 0) {
            setForegroundAsync(createForegroundInfo("جاري التحميل...", title, progress, true));
        }
        Data progressData = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, id)
                .putString(KEY_VIDEO_TITLE, title)
                .putString("progress", progress + "%")
                .build();
        setProgressAsync(progressData);
    }

    private void saveCompletion(String id, String title) {
        SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9.-_ \u0600-\u06FF]", "").trim();
        completed.add(id + "|" + cleanTitle);
        prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
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
                    CHANNEL_ID, "Download Notifications", NotificationManager.IMPORTANCE_MIN
            );
            notificationManager.createNotificationChannel(channel);
        }
    }
}
