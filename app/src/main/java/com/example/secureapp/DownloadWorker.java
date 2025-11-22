package com.example.secureapp;

// [✅✅ تم إضافة الاستيراد الناقص هنا]
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

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
        String displayTitle = getInputData().getString(KEY_VIDEO_TITLE); 
        String specificUrl = getInputData().getString("specificUrl");
        String duration = getInputData().getString("duration");
        
        String subjectName = getInputData().getString("subjectName");
        String chapterName = getInputData().getString("chapterName");
        
        if (subjectName == null) subjectName = "Uncategorized";
        if (chapterName == null) chapterName = "General";

        if (youtubeId == null || displayTitle == null) return Result.failure();

        // استخراج اسم الملف النظيف
        String cleanFileName = displayTitle;
        if (displayTitle.contains("(") && displayTitle.endsWith(")")) {
            try {
                cleanFileName = displayTitle.substring(0, displayTitle.lastIndexOf("(")).trim();
            } catch (Exception e) {
                cleanFileName = displayTitle;
            }
        }
        
        String safeFileName = sanitizeFilename(cleanFileName);
        String safeSubject = sanitizeFilename(subjectName);
        String safeChapter = sanitizeFilename(chapterName);

        setForegroundAsync(createForegroundInfo("جاري التحميل...", displayTitle, 0, true));

        // إنشاء المجلدات
        File subjectDir = new File(context.getFilesDir(), safeSubject);
        if (!subjectDir.exists()) subjectDir.mkdirs();

        File chapterDir = new File(subjectDir, safeChapter);
        if (!chapterDir.exists()) chapterDir.mkdirs();

        // الملفات
        File tempTsFile = new File(context.getCacheDir(), youtubeId + "_temp.ts");
        File tempMp4File = new File(context.getCacheDir(), youtubeId + "_temp.mp4");
        File finalEncryptedFile = new File(chapterDir, safeFileName + ".enc");

        int notificationId = getId().hashCode();

        if (tempTsFile.exists()) tempTsFile.delete();
        if (tempMp4File.exists()) tempMp4File.delete();

        try {
            OkHttpClient client = new OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build();

            // تحميل
            OutputStream tsOutputStream = new FileOutputStream(tempTsFile);
            downloadDirectFile(client, specificUrl, tsOutputStream, youtubeId, displayTitle);
            tsOutputStream.flush();
            tsOutputStream.close();

            // تحويل
            setForegroundAsync(createForegroundInfo("جاري المعالجة...", displayTitle, 90, true));
            String cmd = "-y -i \"" + tempTsFile.getAbsolutePath() + "\" -c copy -bsf:a aac_adtstoasc \"" + tempMp4File.getAbsolutePath() + "\"";
            FFmpegSession session = FFmpegKit.execute(cmd);

            if (ReturnCode.isSuccess(session.getReturnCode())) {
                // تشفير
                setForegroundAsync(createForegroundInfo("جاري الحفظ...", displayTitle, 95, true));
                encryptAndSaveFile(tempMp4File, finalEncryptedFile);
                
                tempTsFile.delete();
                tempMp4File.delete();
                
                // حفظ البيانات
                saveCompletion(youtubeId, displayTitle, duration, safeSubject, safeChapter, safeFileName);
                
                sendNotification(notificationId, "تم التحميل", displayTitle, 100, false);
                return Result.success();
            } else {
                throw new IOException("FFmpeg failed");
            }

        } catch (Exception e) {
            Log.e("DownloadWorker", "Error", e);
            if(finalEncryptedFile.exists()) finalEncryptedFile.delete();
            sendNotification(notificationId, "فشل التحميل", displayTitle, 0, false);
            return Result.failure();
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
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
            while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
            outputStream.flush();
        }
    }

    private void saveCompletion(String id, String fullTitle, String duration, String subject, String chapter, String filename) {
        if (duration == null || duration.isEmpty()) duration = "unknown";
        SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completed = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
        
        String entry = id + "|" + fullTitle + "|" + duration + "|" + subject + "|" + chapter + "|" + filename;
        completed.add(entry);
        
        prefs.edit().putStringSet(KEY_DOWNLOADS_SET, completed).apply();
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
        if (progress % 5 == 0) setForegroundAsync(createForegroundInfo("جاري التحميل...", title, progress, true));
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(String title, String message, int progress, boolean ongoing) {
        return new ForegroundInfo(getId().hashCode(), buildNotification(getId().hashCode(), title, message, progress, ongoing));
    }

    private void sendNotification(int id, String title, String message, int progress, boolean ongoing) {
         notificationManager.notify(id, buildNotification(id, title, message, progress, ongoing));
    }

    // [✅ هذه الدالة كانت تسبب الخطأ بسبب غياب الـ import]
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
