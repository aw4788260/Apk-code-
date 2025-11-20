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

// مكتبات FFmpeg
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DownloadWorker extends Worker {

    private static final String CHANNEL_ID = "download_channel";
    private NotificationManager notificationManager;
    private final Context context;
    public static final String KEY_YOUTUBE_ID = "youtubeId";
    public static final String KEY_VIDEO_TITLE = "videoTitle";
    public static final String DOWNLOADS_PREFS = "DownloadPrefs";
    public static final String KEY_DOWNLOADS_SET = "CompletedDownloads";
    private static final String SERVER_HOSTNAME = "web-production-3a04a.up.railway.app";

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
        String specificUrl = getInputData().getString("specificUrl");

        if (youtubeId == null || videoTitle == null) return Result.failure();
        
        // إشعار البدء
        setForegroundAsync(createForegroundInfo("جاري التحميل...", videoTitle, 0, true));

        // تعريف الملفات (مؤقت ونهائي)
        File tempTsFile = new File(context.getCacheDir(), youtubeId + "_temp.ts");
        File tempMp4File = new File(context.getCacheDir(), youtubeId + "_temp.mp4");
        File finalEncryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");

        int notificationId = getId().hashCode();
        
        // تنظيف مسبق
        if (tempTsFile.exists()) tempTsFile.delete();
        if (tempMp4File.exists()) tempMp4File.delete();

        try {
            // إعدادات الأمان والاتصال
            CertificatePinner certificatePinner = new CertificatePinner.Builder()
                    .add(SERVER_HOSTNAME, "sha256/BFqG/YJoU71ewDXviOWivvt1MWjHJBT9VXfp3D2TDDE=")
                    .add(SERVER_HOSTNAME, "sha256/AlSQhgtJirc8ahLyekmtX+Iw+v46yPYRLJt9Cq1GlB0=")
                    .build();

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .certificatePinner(certificatePinner)
                    .build();

            // 1. تحميل الفيديو بصيغة TS (مؤقتاً)
            OutputStream tsOutputStream = new FileOutputStream(tempTsFile);

            if (specificUrl != null && specificUrl.contains(".m3u8")) {
                downloadHlsSegments(client, specificUrl, tsOutputStream, youtubeId, videoTitle);
            } else if (specificUrl != null) {
                downloadDirectFile(client, specificUrl, tsOutputStream, youtubeId, videoTitle);
            } else {
                throw new IOException("No URL found");
            }
            
            tsOutputStream.flush();
            tsOutputStream.close();

            // 2. التحويل إلى MP4 باستخدام FFmpeg
            // نستخدم -c copy لسرعة خيالية (بدون إعادة ضغط)
            setForegroundAsync(createForegroundInfo("جاري المعالجة (تحويل الصيغة)...", videoTitle, 90, true));
            
            // الأمر: تحويل Container من ts لـ mp4 وإصلاح الصوت (aac_adtstoasc)
            String cmd = "-y -i " + tempTsFile.getAbsolutePath() + " -c copy -bsf:a aac_adtstoasc " + tempMp4File.getAbsolutePath();
            FFmpegSession session = FFmpegKit.execute(cmd);

            if (ReturnCode.isSuccess(session.getReturnCode())) {
                // 3. تشفير ملف الـ MP4 الناتج وحفظه
                setForegroundAsync(createForegroundInfo("جاري التشفير والحفظ...", videoTitle, 95, true));
                encryptAndSaveFile(tempMp4File, finalEncryptedFile);
                
                // تنظيف الملفات المؤقتة
                tempTsFile.delete();
                tempMp4File.delete();
                
                // حفظ البيانات وإرسال إشعار النجاح
                saveCompletion(youtubeId, videoTitle);
                sendNotification(notificationId, "اكتمل التحميل", videoTitle, 100, false);
                return Result.success();
            } else {
                throw new IOException("FFmpeg conversion failed: " + session.getFailStackTrace());
            }

        } catch (Exception e) {
            Log.e("DownloadWorker", "Error", e);
            // تنظيف في حالة الفشل
            if(finalEncryptedFile.exists()) finalEncryptedFile.delete();
            if (tempTsFile.exists()) tempTsFile.delete();
            if (tempMp4File.exists()) tempMp4File.delete();
            
            sendNotification(notificationId, "فشل التحميل", videoTitle, 0, false);
            return Result.failure();
        }
    }

    private void encryptAndSaveFile(File inputFile, File outputFile) throws Exception {
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                outputFile, context, masterKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (InputStream inputStream = new FileInputStream(inputFile);
             OutputStream outputStream = encryptedFile.openFileOutput()) {
            
            byte[] buffer = new byte[1024 * 8];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    private void saveCompletion(String id, String title) {
        String duration = getInputData().getString("duration");
        if (duration == null || duration.equals("0") || duration.isEmpty()) duration = "unknown";

        SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
        
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9.-_ \u0600-\u06FF]", "").trim();
        completed.add(id + "|" + cleanTitle + "|" + duration);
        
        prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
    }

    // دوال التحميل المساعدة (كما هي)
    private void downloadHlsSegments(OkHttpClient client, String m3u8Url, OutputStream outputStream, String id, String title) throws IOException {
        Request playlistRequest = new Request.Builder().url(m3u8Url).build();
        List<String> segmentUrls = new ArrayList<>();
        String baseUrl = "";
        if (m3u8Url.contains("/")) baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);

        try (Response response = client.newCall(playlistRequest).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to fetch m3u8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.startsWith("http")) segmentUrls.add(line); else segmentUrls.add(baseUrl + line);
                }
            }
        }
        if (segmentUrls.isEmpty()) throw new IOException("Empty m3u8");

        int totalSegments = segmentUrls.size();
        int parallelism = 4; 
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        Map<Integer, Future<byte[]>> activeTasks = new HashMap<>();
        int nextSubmitIndex = 0;

        try {
            for (int i = 0; i < totalSegments; i++) {
                while (activeTasks.size() < parallelism && nextSubmitIndex < totalSegments) {
                    final String segUrl = segmentUrls.get(nextSubmitIndex);
                    Callable<byte[]> task = () -> {
                        Request segRequest = new Request.Builder().url(segUrl).build();
                        try (Response segResponse = client.newCall(segRequest).execute()) {
                            if (!segResponse.isSuccessful()) throw new IOException("Failed segment");
                            return segResponse.body().bytes(); 
                        }
                    };
                    activeTasks.put(nextSubmitIndex, executor.submit(task));
                    nextSubmitIndex++;
                }
                Future<byte[]> future = activeTasks.get(i);
                if (future == null) throw new IOException("Lost task " + i);
                byte[] segmentData = future.get(); 
                outputStream.write(segmentData);
                activeTasks.remove(i);
                
                // نوقف العداد عند 90% لأن الـ 10% الباقية للتحويل
                int progress = (int) (((float) (i + 1) / totalSegments) * 90);
                updateProgress(id, title, progress);
            }
        } catch (Exception e) {
            executor.shutdownNow();
            throw new IOException(e);
        } finally {
            executor.shutdown();
        }
    }

    private void downloadDirectFile(OkHttpClient client, String url, OutputStream outputStream, String id, String title) throws IOException {
        Request request = new Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed");
            InputStream inputStream = response.body().byteStream();
            long fileLength = response.body().contentLength();
            byte[] data = new byte[8192];
            int count;
            long total = 0;
            int lastProgress = 0;
            while ((count = inputStream.read(data)) != -1) {
                outputStream.write(data, 0, count);
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 90 / fileLength);
                    if (progress > lastProgress) {
                        updateProgress(id, title, progress);
                        lastProgress = progress;
                    }
                }
            }
        }
    }

    private void updateProgress(String id, String title, int progress) {
        if (progress % 2 == 0) setForegroundAsync(createForegroundInfo("جاري التحميل...", title, progress, true));
        setProgressAsync(new Data.Builder().putString(KEY_YOUTUBE_ID, id).putString(KEY_VIDEO_TITLE, title).putString("progress", progress + "%").build());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(String title, String message, int progress, boolean ongoing) {
        return new ForegroundInfo(getId().hashCode(), buildNotification(getId().hashCode(), title, message, progress, ongoing));
    }

    private void sendNotification(int id, String title, String message, int progress, boolean ongoing) {
         notificationManager.notify(id, buildNotification(id, title, message, progress, ongoing));
    }

    private Notification buildNotification(int id, String title, String message, int progress, boolean ongoing) {
        Intent intent = new Intent(context, DownloadsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title).setContentText(message).setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent).setOngoing(ongoing).setOnlyAlertOnce(true);
        if (ongoing) builder.setProgress(100, progress, false); else builder.setProgress(0, 0, false).setAutoCancel(true);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Download Notifications", NotificationManager.IMPORTANCE_MIN));
    }
}
