package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// [ ✅✅✅ بداية: تعديل Imports ]
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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONObject; // (مكتبة JSON المدمجة في أندرويد)
// [ 🛑🛑🛑 تم حذف كل imports مكتبة at.huber.youtubeExtractor ]
// [ ✅✅✅ نهاية: تعديل Imports ]


public class DownloadWorker extends Worker {

    private static final String TAG = "DownloadWorker";
    
    public static final String KEY_YOUTUBE_ID = "YOUTUBE_ID";
    public static final String KEY_VIDEO_TITLE = "VIDEO_TITLE";

    public static final String DOWNLOADS_PREFS = "OfflineDownloads";
    public static final String KEY_DOWNLOADS_SET = "downloads_set";
    
    // [ ✅✅✅ إضافة: رابط السيرفر ]
    private static final String API_BASE_URL = "https://secured-bot.vercel.app";

    private Context context;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }


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

        // [ ✅✅✅ استخدام OkHttpClient مرتين: مرة لجلب الرابط، ومرة للتحميل ]
        OkHttpClient client = new OkHttpClient();

        try {
            // --- [ ✅✅✅ بداية: الخطوة 1 - جلب رابط التحميل من السيرفر ] ---
            Log.d(TAG, "Starting download for: " + videoTitle);
            
            String apiUrl = API_BASE_URL + "/api/secure/get-download-link?youtubeId=" + youtubeId;
            Request apiRequest = new Request.Builder().url(apiUrl).build();
            String downloadUrl;

            try (Response apiResponse = client.newCall(apiRequest).execute()) {
                if (!apiResponse.isSuccessful()) {
                    throw new IOException("API request failed: " + apiResponse.code() + " " + apiResponse.message());
                }
                
                ResponseBody apiBody = apiResponse.body();
                if (apiBody == null) {
                    throw new IOException("API response body is null");
                }
                
                // (قراءة رد السيرفر)
                String jsonString = apiBody.string();
                JSONObject json = new JSONObject(jsonString);
                
                if (json.has("error")) {
                    throw new Exception("API returned error: " + json.getString("error"));
                }
                
                downloadUrl = json.getString("downloadUrl");
            }
            
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                throw new Exception("API did not return a valid download URL.");
            }

            Log.d(TAG, "Got download URL. Starting file download...");
            // --- [ ✅✅✅ نهاية: الخطوة 1 ] ---


            // --- [ ✅✅✅ بداية: الخطوة 2 - تحميل الملف (نفس الكود القديم) ] ---
            Request downloadRequest = new Request.Builder().url(downloadUrl).build();
            Response downloadResponse = client.newCall(downloadRequest).execute();

            if (!downloadResponse.isSuccessful()) {
                throw new IOException("File download failed: " + downloadResponse.code());
            }

            ResponseBody body = downloadResponse.body();
            if (body == null) {
                throw new IOException("File response body is null");
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
            // --- [ ✅✅✅ نهاية: الخطوة 2 ] ---


            Log.d(TAG, "Download finished. Temp file size: " + tempFile.length());

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
