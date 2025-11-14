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

// [ ✅✅✅ Imports جديدة ]
import java.io.BufferedReader; // <-- لقراءة مخرجات العملية
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException; // <-- لإدارة الأخطاء
import java.io.InputStream;
import java.io.InputStreamReader; // <-- لقراءة مخرجات العملية
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// [ 🛑🛑🛑 تم حذف كل imports المكتبات القديمة (at.huber, okhttp, latch) ]


public class DownloadWorker extends Worker {

    private static final String TAG = "DownloadWorker";
    
    // مفاتيح لتمرير البيانات
    public static final String KEY_YOUTUBE_ID = "YOUTUBE_ID";
    public static final String KEY_VIDEO_TITLE = "VIDEO_TITLE";

    // أسماء ملفات التخزين
    public static final String DOWNLOADS_PREFS = "OfflineDownloads";
    public static final String KEY_DOWNLOADS_SET = "downloads_set";

    private Context context;
    private File ytDlpBinary; // (سنحتفظ بمسار الـ binary هنا)

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    /**
     * [ ✅✅✅ هذا هو الكود الذي أرسلته ]
     * دالة لنسخ الـ binary من (assets) إلى التخزين الداخلي وجعله قابلاً للتنفيذ
     */
    private File extractBinary(Context context) throws IOException {
        File outFile = new File(context.getFilesDir(), "yt-dlp");

        // (نقوم بالنسخ فقط إذا كان الملف غير موجود)
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
            // [ ✅ مهم جداً ] جعله قابلاً للتنفيذ
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

        try {
            // [ 1. خطوة استخراج الـ Binary ]
            this.ytDlpBinary = extractBinary(context);

            // [ 2. خطوة التحميل (باستخدام ProcessBuilder) ]
            Log.d(TAG, "Starting download: " + videoTitle);

            ProcessBuilder pb = new ProcessBuilder(
                    ytDlpBinary.getAbsolutePath(),
                    // رابط الفيديو
                    "https://www.youtube.com/watch?v=" + youtubeId,
                    // طلب أفضل جودة mp4 (فيديو وصوت مدمج)
                    "-f", "best[ext=mp4][vcodec^=avc]/best[ext=mp4]/best",
                    // [ ✅ مهم ] تحديد مكان حفظ الملف المؤقت
                    "-o", tempFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true); // دمج مخرجات الخطأ مع المخرجات العادية
            Process process = pb.start();

            // قراءة مخرجات yt-dlp (مفيد جداً لمعرفة نسبة التحميل)
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                // (هنا يمكنك قراءة نسبة التحميل، مثلاً "[download] 10.5% of ...")
                Log.d("YT-DLP", line);
            }

            int exitCode = process.waitFor(); // انتظار انتهاء العملية
            Log.d("YT-DLP", "Done, exit code = " + exitCode);

            if (exitCode != 0) {
                throw new Exception("yt-dlp failed with exit code " + exitCode);
            }

            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new Exception("yt-dlp ran but file was not created.");
            }
            Log.d(TAG, "Download finished. Temp file size: " + tempFile.length());


            // [ 3. خطوة التشفير (باستخدام androidx.security.crypto) ]
            // (هذا الكود من إجاباتي السابقة وهو صحيح)
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

            // [ 4. خطوة التنظيف وتحديث القائمة ]
            tempFile.delete();
            Log.d(TAG, "Temp file deleted.");

            String videoData = youtubeId + "|" + videoTitle;
            SharedPreferences prefs = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE);
            Set<String> downloads = new HashSet<>(prefs.getStringSet(KEY_DOWNLOADS_SET, new HashSet<>()));
            downloads.add(videoData);
            prefs.edit().putStringSet(KEY_DOWNLOADS_SET, downloads).apply();
            Log.d(TAG, "Video added to SharedPreferences list.");

            // [ 5. الانتهاء بنجاح ]
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
