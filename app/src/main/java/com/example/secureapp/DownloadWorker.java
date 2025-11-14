package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.SparseArray; // <-- ✅ تأكد من وجود هذا السطر

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// [ ✅✅✅ imports المكتبات الجديدة ]
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch; // <-- ✅ أداة الانتظار

import at.huber.youtubeExtractor.VideoMeta; // <-- ✅ المكتبة الجديدة
import at.huber.youtubeExtractor.YouTubeExtractor; // <-- ✅ المكتبة الجديدة
import at.huber.youtubeExtractor.YtFile; // <-- ✅ المكتبة الجديدة
import okhttp3.OkHttpClient; // <-- ✅ مكتبة التحميل
import okhttp3.Request; // <-- ✅ مكتبة التحميل
import okhttp3.Response; // <-- ✅ مكتبة التحميل
import okhttp3.ResponseBody; // <-- ✅ مكتبة التحميل


public class DownloadWorker extends Worker {

    private static final String TAG = "DownloadWorker";
    
    // مفاتيح لتمرير البيانات
    public static final String KEY_YOUTUBE_ID = "YOUTUBE_ID";
    public static final String KEY_VIDEO_TITLE = "VIDEO_TITLE";

    // أسماء ملفات التخزين
    public static final String DOWNLOADS_PREFS = "OfflineDownloads";
    public static final String KEY_DOWNLOADS_SET = "downloads_set";

    private Context context;

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        // 1. استلام البيانات (ID وعنوان الفيديو)
        Data inputData = getInputData();
        String youtubeId = inputData.getString(KEY_YOUTUBE_ID);
        String videoTitle = inputData.getString(KEY_VIDEO_TITLE);

        if (youtubeId == null || videoTitle == null) {
            Log.e(TAG, "Worker failed: Missing input data");
            return Result.failure();
        }

        // اسم ملف مؤقت (غير مشفر) - في مجلد الكاش
        File tempFile = new File(context.getCacheDir(), UUID.randomUUID().toString() + ".mp4");
        // اسم الملف النهائي (المشفر) - في مجلد الملفات الداخلي (الآمن)
        File encryptedFile = new File(context.getFilesDir(), youtubeId + ".enc");

        // [ ✅✅✅ بداية الكود الجديد للتحميل ]
        try {
            // 2. جلب رابط التحميل (سنستخدم CountDownLatch للانتظار)
            final CountDownLatch latch = new CountDownLatch(1);
            final String[] downloadUrl = {null};
            final String[] errorMsg = {null};

            // (هذا الكود "غير متزامن"، لذلك نستخدم Latch لجعله "متزامن" داخل الـ Worker)
            new YouTubeExtractor(context) {
                @Override
                public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                    if (ytFiles == null) {
                        Log.e(TAG, "Extraction failed: ytFiles is null");
                        errorMsg[0] = "فشل جلب روابط الفيديو";
                        latch.countDown(); // (أخبر الـ Worker أن ينهي الانتظار)
                        return;
                    }
                    
                    // iTag 22 = 720p (mp4)
                    // iTag 18 = 360p (mp4)
                    // (سنبحث عن أفضل جودة متاحة)
                    YtFile fileToDownload = ytFiles.get(22); // (محاولة 720p)
                    if (fileToDownload == null) {
                        fileToDownload = ytFiles.get(18); // (محاولة 360p)
                    }
                    // (يمكنك إضافة المزيد من iTags إذا أردت)

                    if (fileToDownload == null) {
                        Log.e(TAG, "Extraction failed: No suitable mp4 stream found.");
                        errorMsg[0] = "لم يتم العثور على جودة mp4 مناسبة";
                        latch.countDown();
                        return;
                    }

                    downloadUrl[0] = fileToDownload.getUrl();
                    latch.countDown(); // (أخبر الـ Worker أن يكمل)
                }
            }.extract("https://www.youtube.com/watch?v=" + youtubeId);

            // [ ✅ الأهم ] الـ Worker سينتظر هنا حتى يتم استدعاء latch.countDown()
            latch.await();

            // التحقق إذا حدث خطأ أثناء جلب الرابط
            if (downloadUrl[0] == null) {
                throw new Exception(errorMsg[0] != null ? errorMsg[0] : "فشل جلب الرابط");
            }

            // 3. خطوة التحميل (باستخدام OkHttp)
            Log.d(TAG, "Starting download from URL: " + downloadUrl[0]);
            
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(downloadUrl[0]).build();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("OkHttp download failed: " + response.message());
            }

            ResponseBody body = response.body();
            InputStream inputStream = body.byteStream();
            OutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024 * 4];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
            body.close(); // (مهم جداً إغلاق الـ body)

            Log.d(TAG, "Download finished. Temp file size: " + tempFile.length());
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new Exception("OkHttp failed to download file.");
            }

            // [ 4. خطوة التشفير (باستخدام androidx.security.crypto) ]
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

            while ((bytesRead = encInputStream.read(buffer)) != -1) {
                encOutputStream.write(buffer, 0, bytesRead);
            }
            encOutputStream.flush();
            encOutputStream.close();
            encInputStream.close();
            Log.d(TAG, "Encryption finished. Encrypted file size: " + encryptedFile.length());

            // [ 5. خطوة التنظيف وتحديث القائمة ]
            tempFile.delete();
            Log.d(TAG, "Temp file deleted.");

            // (نستخدم صيغة "ID|Title" لسهولة القراءة)
            String videoData = youtubeId + "|" + videoTitle;
            
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> downloads = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            downloads.add(videoData);
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, downloads).apply();
            Log.d(TAG, "Video added to SharedPreferences list.");

            // [ 6. الانتهاء بنجاح ]
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Worker failed: " + e.getMessage(), e);
            
            // تنظيف في حالة الفشل
            if (tempFile.exists()) tempFile.delete();
            if (encryptedFile.exists()) encryptedFile.delete();
            
            return Result.failure();
        }
    }
}
