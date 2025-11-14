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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DownloadWorker extends Worker {

    private static final String TAG = "DownloadWorker";
    
    // مفاتيح لتمرير البيانات
    public static final String KEY_YOUTUBE_ID = "YOUTUBE_ID";
    public static final String KEY_VIDEO_TITLE = "VIDEO_TITLE";

    // أسماء ملفات التخزين
    public static final String DOWNLOADS_PREFS = "OfflineDownloads";
    public static final String KEY_DOWNLOADS_SET = "downloads_set";

    private Context context;
    private File ytDlpBinary; 

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    private File extractBinary(Context context) throws IOException {
        File outFile = new File(context.getFilesDir(), "yt-dlp");

        if (!outFile.exists()) {
            Log.d(TAG, "Binary not found, extracting...");
            try (InputStream is = context.getAssets().open("yt-dlp");
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            outFile.setExecutable(true);
            Log.d(TAG, "Binary extracted successfully.");
        } else {
            Log.d(TAG, "Binary already exists.");
        }
        
        return outFile;
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

        File tempFile = new File(context.getCacheDir(), UUID.randomUUID().toString() + ".mp4");
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");

        try {
            this.ytDlpBinary = extractBinary(context);

            Log.d(TAG, "Starting download: " + videoTitle);

            ProcessBuilder pb = new ProcessBuilder(
                    ytDlpBinary.getAbsolutePath(),
                    "https://www.youtube.com/watch?v=" + youtubeId,
                    "-f", "best[ext=mp4][vcodec^=avc]/best[ext=mp4]/best",
                    "-o", tempFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true); 
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("YT-DLP", line);

                if (line.contains("[download]") && line.contains("%")) {
                    try {
                        String percentage = line.substring(line.indexOf("]") + 1, line.indexOf("%") + 1).trim();
                        
                        // [ ✅✅✅ تعديل: إرسال كل البيانات مع التقدم ]
                        Data progressData = new Data.Builder()
                                .putString("progress", percentage)
                                .putString(KEY_YOUTUBE_ID, youtubeId)
                                .putString(KEY_VIDEO_TITLE, videoTitle)
                                .build();
                        setProgressAsync(progressData);
                        
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse progress string: " + line);
                    }
                }
            }

            int exitCode = process.waitFor(); 
            Log.d("YT-DLP", "Done, exit code = " + exitCode);

            if (exitCode != 0) {
                throw new Exception("yt-dlp failed with exit code " + exitCode);
            }

            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new Exception("yt-dlp ran but file was not created.");
            }
            Log.d(TAG, "Download finished. Temp file size: " + tempFile.length());


            Log.d(TAG, "Starting encryption for: " + encryptedFile.getName());
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            InputStream encInputStream = new FileInputStream(tempFile);
            OutputStream encOutputStream = encryptedFileObj.openFileOutput();
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = encInputStream.read(buffer)) != -1) {
                encOutputStream.write(buffer, 0, bytesRead);
            }
            encOutputStream.flush();
            encOutputStream.close();
            encInputStream.close();
            Log.d(TAG, "Encryption finished. Encrypted file size: " + encryptedFile.length());

            tempFile.delete();
            Log.d(TAG, "Temp file deleted.");

            String videoData = youtubeId + "|" + videoTitle;
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> downloads = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            downloads.add(videoData);
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, downloads).apply();
            Log.d(TAG, "Video added to SharedPreferences list.");

            // [ ✅✅✅ تعديل: إرسال البيانات عند النجاح ]
            Data successData = new Data.Builder()
                    .putString(KEY_YOUTUBE_ID, youtubeId)
                    .putString(KEY_VIDEO_TITLE, videoTitle)
                    .build();
            return Result.success(successData);

        } catch (Exception e) {
            Log.e(TAG, "Worker failed: " + e.getMessage(), e);
            
            if (tempFile.exists()) tempFile.delete();
            if (encryptedFile.exists()) encryptedFile.delete();
            
            // [ ✅✅✅ تعديل: إرسال كل البيانات عند الفشل ]
            Data errorData = new Data.Builder()
                    .putString("error", e.getMessage())
                    .putString(KEY_YOUTUBE_ID, youtubeId)
                    .putString(KEY_VIDEO_TITLE, videoTitle)
                    .build();
            return Result.failure(errorData);
        }
    }
}
