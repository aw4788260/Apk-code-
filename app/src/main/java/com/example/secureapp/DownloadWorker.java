package com.example.secureapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;
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
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
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
    private static final String TAG = "DownloadWorker";
    private NotificationManager notificationManager;
    private final Context context;
    
    public static final String KEY_YOUTUBE_ID = "youtubeId";
    public static final String KEY_VIDEO_TITLE = "videoTitle";
    public static final String DOWNLOADS_PREFS = "DownloadPrefs";
    public static final String KEY_DOWNLOADS_SET = "CompletedDownloads";

    private static final int BUFFER_SIZE = 1024 * 1024 * 4; 
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0";

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseCrashlytics.getInstance().log("DownloadWorker: Started execution");
        Log.d(TAG, "🚀 Worker Started!");

        // ✅ 1. فحص نوع التحميل (فيديو أم PDF؟)
        String type = getInputData().getString("type");
        if ("pdf".equals(type)) {
            return downloadPdf(); // الانتقال لمنطق تحميل الـ PDF
        }

        // ⬇️ منطق تحميل الفيديو (الكود القديم)
        String youtubeId = getInputData().getString(KEY_YOUTUBE_ID);
        String displayTitle = getInputData().getString(KEY_VIDEO_TITLE);
        String specificUrl = getInputData().getString("specificUrl"); 
        
        Log.d(TAG, "Params: ID=" + youtubeId + ", URL=" + specificUrl);

        if (youtubeId == null || displayTitle == null || specificUrl == null || specificUrl.isEmpty()) {
            String errorMsg = "Missing input data. URL is " + (specificUrl == null ? "NULL" : "EMPTY");
            Log.e(TAG, errorMsg);
            FirebaseCrashlytics.getInstance().recordException(new Exception(errorMsg));
            return Result.failure();
        }

        String duration = getInputData().getString("duration");
        String subjectName = getInputData().getString("subjectName");
        String chapterName = getInputData().getString("chapterName");
        
        if (subjectName == null) subjectName = "Uncategorized";
        if (chapterName == null) chapterName = "General";

        String safeYoutubeId = youtubeId.replaceAll("[^a-zA-Z0-9_-]", "");
        String safeFileName = sanitizeFilename(displayTitle);
        String safeSubject = sanitizeFilename(subjectName);
        String safeChapter = sanitizeFilename(chapterName);

        setForegroundAsync(createForegroundInfo("جاري التحميل...", displayTitle, 0, true));

        File subjectDir = new File(context.getFilesDir(), safeSubject);
        if (!subjectDir.exists()) subjectDir.mkdirs();

        File chapterDir = new File(subjectDir, safeChapter);
        if (!chapterDir.exists()) chapterDir.mkdirs();

        File tempTsFile = new File(context.getCacheDir(), safeYoutubeId + "_temp.ts");
        File tempMp4File = new File(context.getCacheDir(), safeYoutubeId + "_temp.mp4");
        File finalEncryptedFile = new File(chapterDir, safeFileName + ".enc");

        int notificationId = getId().hashCode();

        if (tempTsFile.exists()) tempTsFile.delete();
        if (tempMp4File.exists()) tempMp4File.delete();

        try {
            ConnectionPool pool = new ConnectionPool(5, 5, TimeUnit.MINUTES);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectionPool(pool)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            OutputStream tsOutputStream = new BufferedOutputStream(new FileOutputStream(tempTsFile), BUFFER_SIZE);
            
            Log.d(TAG, "Starting Download Phase...");
            FirebaseCrashlytics.getInstance().log("DownloadWorker: Downloading from " + specificUrl);
            
            if (specificUrl.contains(".m3u8")) {
                FirebaseCrashlytics.getInstance().log("DownloadWorker: Mode HLS");
                downloadHlsSegments(client, specificUrl, tsOutputStream, youtubeId, displayTitle);
            } else {
                FirebaseCrashlytics.getInstance().log("DownloadWorker: Mode Direct");
                downloadDirectFile(client, specificUrl, tsOutputStream, youtubeId, displayTitle);
            }
            
            tsOutputStream.flush();
            tsOutputStream.close();

            if (isStopped()) throw new IOException("Work cancelled by user");

            Log.d(TAG, "Starting FFmpeg Phase...");
            FirebaseCrashlytics.getInstance().log("DownloadWorker: FFmpeg processing");
            setForegroundAsync(createForegroundInfo("جاري المعالجة...", displayTitle, 90, true));
            
            String cmd = "-y -i \"" + tempTsFile.getAbsolutePath() + "\" -c copy -bsf:a aac_adtstoasc \"" + tempMp4File.getAbsolutePath() + "\"";
            FFmpegSession session = FFmpegKit.execute(cmd);

            if (ReturnCode.isSuccess(session.getReturnCode())) {
                if (isStopped()) throw new IOException("Work cancelled by user");

                Log.d(TAG, "Starting Encryption Phase...");
                FirebaseCrashlytics.getInstance().log("DownloadWorker: Encrypting");
                setForegroundAsync(createForegroundInfo("جاري الحفظ...", displayTitle, 95, true));
                encryptAndSaveFile(tempMp4File, finalEncryptedFile);
                
                tempTsFile.delete();
                tempMp4File.delete();
                
                if (isStopped()) throw new IOException("Work cancelled by user");

                saveCompletion(youtubeId, displayTitle, duration, safeSubject, safeChapter, safeFileName);
                
                sendNotification(notificationId, "تم التحميل", displayTitle, 100, false);
                
                Log.d(TAG, "✅ Download Complete Success!");
                FirebaseCrashlytics.getInstance().log("DownloadWorker: Success");
                return Result.success();
            } else {
                String ffmpegError = session.getFailStackTrace();
                Log.e(TAG, "FFmpeg Failed: " + ffmpegError);
                FirebaseCrashlytics.getInstance().log("DownloadWorker: FFmpeg Failed - " + ffmpegError);
                throw new IOException("FFmpeg failed: " + ffmpegError);
            }

        } catch (Exception e) {
            Log.e(TAG, "Download Error", e);
            
            if(finalEncryptedFile.exists()) finalEncryptedFile.delete();
            if(tempTsFile.exists()) tempTsFile.delete();
            if(tempMp4File.exists()) tempMp4File.delete();
            
            if (isStopped() || (e.getMessage() != null && e.getMessage().contains("cancelled"))) {
                notificationManager.cancel(notificationId);
                FirebaseCrashlytics.getInstance().log("DownloadWorker: Cancelled by user");
                return Result.failure();
            }
            
            FirebaseCrashlytics.getInstance().recordException(e);
            sendNotification(notificationId, "فشل التحميل", displayTitle, 0, false);
            return Result.failure();
        }
    }

    // ✅ دالة تحميل الـ PDF الجديدة
    private Result downloadPdf() {
        String pdfId = getInputData().getString("pdfId");
        String title = getInputData().getString("videoTitle"); // نستخدم نفس المفتاح للتوافق
        String subject = getInputData().getString("subjectName");
        String chapter = getInputData().getString("chapterName");
        
        if (subject == null) subject = "Uncategorized";
        if (chapter == null) chapter = "General";

        // بناء الرابط الآمن
        SharedPreferences prefs = context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "");
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // تأكد من أن الرابط يطابق السيرفر الخاص بك
        String url = "https://courses.aw478260.dpdns.org/api/secure/get-pdf?pdfId=" + pdfId + "&userId=" + userId + "&deviceId=" + deviceId;

        String safeSubject = sanitizeFilename(subject);
        String safeChapter = sanitizeFilename(chapter);
        
        // مسار الحفظ الآمن (نفس المجلد المستخدم للفيديوهات لسهولة الإدارة)
        File subjectDir = new File(context.getFilesDir(), safeSubject);
        // ✅ تم إصلاح الخطأ هنا: إنشاء المجلدات إذا لم تكن موجودة
        if (!subjectDir.exists()) subjectDir.mkdirs();

        File chapterDir = new File(subjectDir, safeChapter);
        if (!chapterDir.exists()) chapterDir.mkdirs();
        
        // اسم الملف المشفر (doc_ID.enc) لتمييزه عن الفيديوهات
        String saveName = "doc_" + pdfId; 
        File targetFile = new File(chapterDir, saveName + ".enc");

        try {
            setForegroundAsync(createForegroundInfo("جاري تحميل الملف...", title, 0, true));
            
            OkHttpClient client = new OkHttpClient();
            Request req = new Request.Builder()
                    .url(url)
                    // إضافة الهيدر السري للحماية الإضافية
                    .addHeader("x-app-secret", "My_Sup3r_S3cr3t_K3y_For_Android_App_Only")
                    .build();
            
            try (Response response = client.newCall(req).execute()) {
                if (!response.isSuccessful()) throw new IOException("Server Error: " + response.code());
                
                // التشفير والحفظ المباشر
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                EncryptedFile encryptedFile = new EncryptedFile.Builder(
                        targetFile, context, masterKeyAlias,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                try (OutputStream os = encryptedFile.openFileOutput();
                     InputStream is = response.body().byteStream()) {
                    
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    os.flush();
                }
            }
            
            // حفظ في سجل التحميلات (مع تمييز النوع كـ PDF)
            // نستخدم "PDF_" كبادئة للـ ID لتمييزه في قائمة التحميلات
            // ونستخدم "PDF" مكان الـ Duration
            saveCompletion("PDF_" + pdfId, title, "PDF", safeSubject, safeChapter, saveName);
            
            sendNotification(pdfId.hashCode(), "اكتمل التحميل", title, 100, false);
            Log.d(TAG, "✅ PDF Downloaded & Encrypted: " + targetFile.getAbsolutePath());
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "PDF Download Failed", e);
            FirebaseCrashlytics.getInstance().recordException(e);
            sendNotification(pdfId.hashCode(), "فشل تحميل الملف", title, 0, false);
            return Result.failure();
        }
    }

    // --- الدوال المساعدة (بدون تغيير) ---

    private void downloadHlsSegments(OkHttpClient client, String m3u8Url, OutputStream outputStream, String id, String title) throws IOException {
        // ... (نفس الكود السابق)
        Request playlistRequest = new Request.Builder().url(m3u8Url).header("User-Agent", USER_AGENT).build();
        List<String> segmentUrls = new ArrayList<>();
        String baseUrl = "";
        if (m3u8Url.contains("/")) baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);

        try (Response response = client.newCall(playlistRequest).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to fetch m3u8: " + response.code());
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
        FirebaseCrashlytics.getInstance().log("HLS: Found " + segmentUrls.size() + " segments");

        int totalSegments = segmentUrls.size();
        int parallelism = 4;
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        Map<Integer, Future<byte[]>> activeTasks = new HashMap<>();
        int nextSubmitIndex = 0;

        try {
            for (int i = 0; i < totalSegments; i++) {
                if (isStopped()) throw new IOException("Work cancelled");
                while (activeTasks.size() < parallelism && nextSubmitIndex < totalSegments) {
                    if (isStopped()) break;
                    final String segUrl = segmentUrls.get(nextSubmitIndex);
                    Callable<byte[]> task = () -> {
                        Request segRequest = new Request.Builder().url(segUrl).header("User-Agent", USER_AGENT).build();
                        try (Response segResponse = client.newCall(segRequest).execute()) {
                            if (!segResponse.isSuccessful()) throw new IOException("Failed segment");
                            return segResponse.body().bytes(); 
                        }
                    };
                    activeTasks.put(nextSubmitIndex, executor.submit(task));
                    nextSubmitIndex++;
                }
                if (isStopped()) throw new IOException("Work cancelled");
                Future<byte[]> future = activeTasks.get(i);
                if (future == null) throw new IOException("Lost task " + i);
                try {
                    byte[] segmentData = future.get(); 
                    outputStream.write(segmentData);
                } catch (Exception e) { throw new IOException("Segment write error", e); }
                activeTasks.remove(i);
                int progress = (int) (((float) (i + 1) / totalSegments) * 90);
                updateProgress(id, title, progress);
            }
        } catch (Exception e) { executor.shutdownNow(); throw e; } finally { executor.shutdown(); }
    }

    private void downloadDirectFile(OkHttpClient client, String url, OutputStream outputStream, String id, String title) throws IOException {
        // ... (نفس الكود السابق)
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed direct download: " + response.code());
            InputStream inputStream = response.body().byteStream();
            long fileLength = response.body().contentLength();
            FirebaseCrashlytics.getInstance().log("Direct Download: Size = " + fileLength);
            
            byte[] data = new byte[BUFFER_SIZE];
            int count;
            long total = 0;
            int lastProgress = 0;
            while ((count = inputStream.read(data)) != -1) {
                if (isStopped()) throw new IOException("Work cancelled");
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

    private String sanitizeFilename(String name) { return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim(); }

    private void encryptAndSaveFile(File inputFile, File outputFile) throws Exception {
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        EncryptedFile encryptedFile = new EncryptedFile.Builder(outputFile, context, masterKeyAlias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
        try (InputStream inputStream = new FileInputStream(inputFile); OutputStream outputStream = new BufferedOutputStream(encryptedFile.openFileOutput(), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (isStopped()) throw new IOException("Work cancelled");
                outputStream.write(buffer, 0, bytesRead);
            }
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

    private void updateProgress(String id, String title, int progress) {
        if (isStopped()) return;
        if (progress % 5 == 0) setForegroundAsync(createForegroundInfo("جاري التحميل...", title, progress, true));
        setProgressAsync(new Data.Builder().putString(KEY_YOUTUBE_ID, id).putString(KEY_VIDEO_TITLE, title).putString("progress", progress + "%").build());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(String title, String message, int progress, boolean ongoing) {
        Notification notification = buildNotification(getId().hashCode(), title, message, progress, ongoing);
        int notificationId = getId().hashCode();

        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(notificationId, notification);
        }
    }

    private void sendNotification(int id, String title, String message, int progress, boolean ongoing) {
         notificationManager.notify(id, buildNotification(id, title, message, progress, ongoing));
    }

    private Notification buildNotification(int id, String title, String message, int progress, boolean ongoing) {
        Intent intent = new Intent(context, DownloadsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID).setContentTitle(title).setContentText(message).setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent).setOngoing(ongoing).setOnlyAlertOnce(true);
        if (ongoing) builder.setProgress(100, progress, false); else builder.setProgress(0, 0, false).setAutoCancel(true);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Download Notifications", NotificationManager.IMPORTANCE_MIN));
        }
    }
}
