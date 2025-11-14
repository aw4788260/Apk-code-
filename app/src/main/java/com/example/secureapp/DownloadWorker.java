package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// [ ✅✅✅ بداية: إضافة Imports جديدة ]
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
// [ ✅✅✅ نهاية: إضافة Imports جديدة ]


public class DownloadWorker extends Worker {

    private static final String TAG = "DownloadWorker";
    
    public static final String KEY_YOUTUBE_ID = "YOUTUBE_ID";
    public static final String KEY_VIDEO_TITLE = "VIDEO_TITLE";

    public static final String DOWNLOADS_PREFS = "OfflineDownloads";
    public static final String KEY_DOWNLOADS_SET = "downloads_set";

    private Context context;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    // [ 🛑🛑🛑 تم حذف دالة extractBinary() من هنا لأننا لن نستخدمها ]


    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String youtubeId = inputData.getString(KEY_YOUTUBE_ID);
        String videoTitle = inputData.getString(KEY_VIDEO_TITLE);

        if (youtubeId == null || videoTitle == null) {
            Log.e(TAG, "Worker failed: Missing input data");
            return Result.failure();
        }

        Data initialProgress = new Data.Builder()
                .putString(KEY_YOUTUBE_ID, youtubeId)
                .putString(KEY_VIDEO_TITLE, videoTitle)
                .putString("progress", "0% (جاري جلب الرابط)")
                .build();
        setProgressAsync(initialProgress);

        File tempFile = new File(context.getCacheDir(), UUID.randomUUID().toString() + ".mp4");
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");

        try {
            // --- [ ✅✅✅ بداية الكود الجديد (باستخدام المكتبات الصحيحة) ] ---
            Log.d(TAG, "Starting download for: " + videoTitle);

            // 1. جلب رابط الفيديو (يتطلب التشغيل على Main Thread)
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> downloadUrlRef = new AtomicReference<>();
            final AtomicReference<String> errorRef = new AtomicReference<>();

            // (نقوم بتشغيل Extractor على الـ Main Thread وننتظر النتيجة)
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    new YouTubeExtractor(context) {
                        @Override
                        public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                            if (ytFiles == null) {
                                errorRef.set("فشل جلب الفيديو (ytFiles is null)");
                                latch.countDown();
                                return;
                            }
                            
                            // (البحث عن أفضل جودة MP4 متاحة - 720p أو 360p)
                            int itag = -1;
                            if (ytFiles.get(22) != null) { // 720p (MP4, H.264)
                                itag = 22;
                            } else if (ytFiles.get(18) != null) { // 360p (MP4, H.264)
                                itag = 18;
                            } else {
                                // (البحث عن أي صيغة mp4 أخرى كخطة بديلة)
                                for(int i = 0; i < ytFiles.size(); i++) {
                                    int key = ytFiles.keyAt(i);
                                    YtFile file = ytFiles.get(key);
                                    if (file.getFormat().getExt().equals("mp4")) {
                                        itag = key;
                                        break;
                                    }
                                }
                            }

                            if (itag != -1) {
                                downloadUrlRef.set(ytFiles.get(itag).getUrl());
                            } else {
                                errorRef.set("لم يتم العثور على صيغة mp4 متاحة");
                            }
                            latch.countDown();
                        }
                    }.extract("https://www.youtube.com/watch?v=" + youtubeId, true, true);
                } catch (Exception e) {
                    errorRef.set("خطأ في YouTubeExtractor: " + e.getMessage());
                    latch.countDown();
                }
            });

            // (الـ Worker ينتظر انتهاء مهمة الـ Main Thread)
            latch.await();

            if (errorRef.get() != null) {
                throw new Exception(errorRef.get());
            }
            
            String downloadUrl = downloadUrlRef.get();
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new Exception("لم يتم العثور على رابط تحميل صالح.");
            }

            Log.d(TAG, "Got download URL. Starting OkHttp download...");

            // 2. تحميل الملف باستخدام OkHttp
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(downloadUrl).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new IOException("OkHttp failed: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            
            long totalBytes = body.contentLength();
            long downloadedBytes = 0;
            
            try (InputStream inputStream = body.byteStream();
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;
                    
                    if (totalBytes > 0) {
                        int progress = (int) ((downloadedBytes * 100) / totalBytes);
                        Data progressData = new Data.Builder()
                                .putString("progress", progress + "%")
                                .putString(KEY_YOUTUBE_ID, youtubeId)
                                .putString(KEY_VIDEO_TITLE, videoTitle)
                                .build();
                        setProgressAsync(progressData);
                    }
                }
                outputStream.flush();
            }

            Log.d(TAG, "Download finished. Temp file size: " + tempFile.length());
            // --- [ ✅✅✅ نهاية الكود الجديد ] ---


            // (الكود التالي (التشفير) سليم ويجب الإبقاء عليه)
            Log.d(TAG, "Starting encryption for: " + encryptedFile.getName());
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            try (InputStream encInputStream = new FileInputStream(tempFile);
                 OutputStream encOutputStream = encryptedFileObj.openFileOutput()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = encInputStream.read(buffer)) != -1) {
                    encOutputStream.write(buffer, 0, bytesRead);
                }
                encOutputStream.flush();
            }
            Log.d(TAG, "Encryption finished. Encrypted file size: " + encryptedFile.length());

            tempFile.delete();
            Log.d(TAG, "Temp file deleted.");

            String videoData = youtubeId + "|" + videoTitle;
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> downloads = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            downloads.add(videoData);
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, downloads).apply();
            Log.d(TAG, "Video added to SharedPreferences list.");

            Data successData = new Data.Builder()
                    .putString(KEY_YOUTUBE_ID, youtubeId)
                    .putString(KEY_VIDEO_TITLE, videoTitle)
                    .build();
            return Result.success(successData);

        } catch (Exception e) {
            Log.e(TAG, "Worker failed: " + e.getMessage(), e);
            
            if (tempFile.exists()) tempFile.delete();
            if (encryptedFile.exists()) encryptedFile.delete();
            
            Data errorData = new Data.Builder()
                    .putString("error", e.getMessage()) // (هذا الخطأ هو الذي سيظهر الآن)
                    .putString(KEY_YOUTUBE_ID, youtubeId)
                    .putString(KEY_VIDEO_TITLE, videoTitle)
                    .build();
            return Result.failure(errorData);
        }
    }
}
