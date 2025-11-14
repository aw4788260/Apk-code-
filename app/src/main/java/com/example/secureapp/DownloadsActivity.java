package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer; // [ ✅ جديد ]
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.WorkInfo; // [ ✅ جديد ]
import androidx.work.WorkManager; // [ ✅ جديد ]

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap; // [ ✅ جديد ]
import java.util.HashSet;
import java.util.List; // [ ✅ جديد ]
import java.util.Map; // [ ✅ جديد ]
import java.util.Set;
import java.util.UUID; // [ ✅ جديد ]
import java.util.concurrent.Executors;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private TextView emptyText;
    private ProgressBar decryptionProgress;

    // [ ✅✅ جديد: نموذج بيانات معدل ]
    // (سنستخدم كائن مخصص بدلاً من قوائم منفصلة)
    private static class DownloadItem {
        String title;
        String youtubeId;
        String status; // "Completed", "Running 10%", "Failed: Error", "Queued"
        UUID workId; // لتتبع مهام WorkManager

        DownloadItem(String title, String youtubeId, String status, UUID workId) {
            this.title = title;
            this.youtubeId = youtubeId;
            this.status = status;
            this.workId = workId;
        }

        // (هذا ما سيظهر في الـ ListView)
        @NonNull
        @Override
        public String toString() {
            if (status.equals("Completed")) {
                return title + " (✅ جاهز للتشغيل)";
            }
            return title + " (" + status + ")";
        }
    }

    private ArrayList<DownloadItem> downloadItems = new ArrayList<>();
    private ArrayAdapter<DownloadItem> adapter;
    // [ نهاية التعديل ]

    private static final String TAG = "DownloadsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        // ربط الواجهة
        listView = findViewById(R.id.downloads_listview);
        emptyText = findViewById(R.id.empty_text);
        decryptionProgress = findViewById(R.id.decryption_progress);
        
        // [ ✅✅ تعديل: استخدام الأداة والموديل الجديد ]
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, downloadItems);
        listView.setAdapter(adapter);

        // [ ✅✅ تعديل: الضغط على العنصر (أصبح أكثر ذكاءً) ]
        listView.setOnItemClickListener((parent, view, position, id) -> {
            DownloadItem clickedItem = downloadItems.get(position);
            
            if (clickedItem.status.equals("Completed")) {
                // [ ✅✅ هنا يبدأ فك التشفير والتشغيل ]
                decryptAndPlayVideo(clickedItem.youtubeId, clickedItem.title);
            } else if (clickedItem.status.startsWith("Failed")) {
                Toast.makeText(this, "هذا التحميل فشل. الرجاء المحاولة مجدداً.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "هذا التحميل قيد التنفيذ...", Toast.LENGTH_SHORT).show();
            }
        });
        
        // [ ✅✅ جديد: استدعاء المراقب الجديد ]
        observeDownloadChanges();
    }

    /**
     * [ ✅✅✅ جديد: هذا هو الكود الرئيسي الجديد ]
     * يقوم بمراقبة WorkManager وجلب البيانات من SharedPreferences
     */
    private void observeDownloadChanges() {
        // 1. جلب التحميلات المكتملة (القديمة) من SharedPreferences
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completedDownloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());
        
        // (خريطة لتخزين المكتمل لسهولة البحث)
        Map<String, String> completedMap = new HashMap<>();
        for (String videoData : completedDownloads) {
            String[] parts = videoData.split("\\|", 2);
            if (parts.length == 2) {
                completedMap.put(parts[0], parts[1]); // youtubeId -> title
            }
        }

        // 2. مراقبة WorkManager (مباشرة)
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("download_work_tag")
            .observe(this, new Observer<List<WorkInfo>>() {
                @Override
                public void onChanged(List<WorkInfo> workInfos) {
                    
                    downloadItems.clear(); // (نبدأ القائمة من جديد مع كل تحديث)
                    
                    // (مجموعة لتتبع الـ IDs التي تمت معالجتها لتجنب التكرار)
                    Set<String> processedYoutubeIds = new HashSet<>();

                    // 3. معالجة التحميلات (الجاري، الفاشل، المنتظر) من WorkManager
                    if (workInfos != null) {
                        for (WorkInfo workInfo : workInfos) {
                            // (جلب البيانات التي أرسلناها للـ Worker)
                            String youtubeId = workInfo.getInputData().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            String title = workInfo.getInputData().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            
                            if (youtubeId == null || title == null) continue;

                            String statusStr = "";
                            WorkInfo.State state = workInfo.getState();

                            if (state == WorkInfo.State.RUNNING) {
                                String progress = workInfo.getProgress().getString("progress");
                                statusStr = (progress != null) ? "جاري التحميل " + progress : "جاري التحميل...";
                            } else if (state == WorkInfo.State.ENQUEUED) {
                                statusStr = "في الانتظار...";
                            } else if (state == WorkInfo.State.FAILED) {
                                String error = workInfo.getOutputData().getString("error");
                                if (error != null && (error.contains("exit code 1") || error.contains("not created"))) {
                                    statusStr = "فشل: الفيديو غير متاح";
                                } else {
                                    statusStr = "فشل: خطأ غير معروف";
                                }
                            } else if (state == WorkInfo.State.SUCCEEDED) {
                                // (نجح، سيتم إضافته من SharedPreferences لضمان عدم تكراره)
                                processedYoutubeIds.add(youtubeId);
                                // (يمكننا إضافته من هنا مباشرة أيضاً)
                                // downloadItems.add(new DownloadItem(title, youtubeId, "Completed", workInfo.getId()));
                            } else if (state == WorkInfo.State.CANCELLED || state == WorkInfo.State.BLOCKED) {
                                statusStr = "تم الإلغاء";
                            }

                            if (!statusStr.isEmpty()) {
                                downloadItems.add(new DownloadItem(title, youtubeId, statusStr, workInfo.getId()));
                                processedYoutubeIds.add(youtubeId); // (تمت معالجته)
                            }
                        }
                    }

                    // 4. إضافة التحميلات المكتملة (التي لم تتم معالجتها بواسطة WorkManager)
                    for (Map.Entry<String, String> entry : completedMap.entrySet()) {
                        String youtubeId = entry.getKey();
                        String title = entry.getValue();
                        if (!processedYoutubeIds.contains(youtubeId)) {
                            // (هذا تحميل مكتمل قديماً ولم يعد WorkManager يعرف عنه شيئاً)
                            downloadItems.add(new DownloadItem(title, youtubeId, "Completed", null));
                        }
                    }

                    // 5. تحديث الواجهة
                    if (downloadItems.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.GONE);
                    } else {
                        adapter.notifyDataSetChanged();
                        emptyText.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                    }
                    
                    // (تنظيف المهام المنتهية من WorkManager)
                    WorkManager.getInstance(getApplicationContext()).pruneWork();
                }
            });
    }

    // [ 🛑🛑 تم حذف دالة loadDownloads() القديمة ]

    private void decryptAndPlayVideo(String youtubeId, String videoTitle) {
        Log.d(TAG, "Starting decryption for " + youtubeId);
        
        // إظهار شاشة الانتظار
        decryptionProgress.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        
        // (سنقوم بفك التشفير في خيط منفصل لتجنب تجميد الواجهة)
        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                // 1. تحديد الملف المشفر (الموجود في filesDir)
                File encryptedFile = new File(getFilesDir(), youtubeId + ".enc");
                if (!encryptedFile.exists()) {
                    throw new Exception("الملف المشفر غير موجود!");
                }

                // 2. تحديد الملف المؤقت (الذي سيتم فك تشفيره إليه في الكاش)
                decryptedFile = new File(getCacheDir(), "decrypted_video.mp4");
                // (حذف أي ملف قديم)
                if(decryptedFile.exists()) decryptedFile.delete();

                // 3. إعداد مفتاح التشفير
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES2S_GCM_SPEC);

                EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                        encryptedFile,
                        this,
                        masterKeyAlias,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                // 4. عملية النسخ (من مشفر إلى غير مشفر)
                InputStream encryptedInputStream = encryptedFileObj.openFileInput();
                OutputStream decryptedOutputStream = new FileOutputStream(decryptedFile);

                byte[] buffer = new byte[1024 * 4];
                int bytesRead;
                while ((bytesRead = encryptedInputStream.read(buffer)) != -1) {
                    decryptedOutputStream.write(buffer, 0, bytesRead);
                }
                decryptedOutputStream.flush();
                decryptedOutputStream.close();
                encryptedInputStream.close();

                Log.d(TAG, "Decryption complete. File size: " + decryptedFile.length());

                // 5. [ الأهم ] تشغيل الملف بعد فك التشفير
                playDecryptedFile(decryptedFile, videoTitle);

            } catch (Exception e) {
                Log.e(TAG, "Decryption failed", e);
                // إظهار خطأ
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "فشل فك تشفير الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // إخفاء شاشة الانتظار
                    decryptionProgress.setVisibility(View.GONE);
                    // [ 🛑 تم حذف loadDownloads() ]
                });
                // تنظيف الملف المؤقت إذا فشلت العملية
                if(decryptedFile != null && decryptedFile.exists()) {
                    decryptedFile.delete();
                }
            }
        });
    }

    private void playDecryptedFile(File decryptedFile, String videoTitle) {
        // [ ✅✅ الأسلوب الصحيح باستخدام FileProvider ]
        
        // 1. جلب الـ Authority (الذي سنعرفه في Manifest)
        String authority = getApplicationContext().getPackageName() + ".provider";
        
        // 2. إنشاء URI آمن للملف
        Uri videoUri = FileProvider.getUriForFile(this, authority, decryptedFile);

        Log.d(TAG, "Playing video from URI: " + videoUri.toString());

        // 3. إنشاء Intent لتشغيل الفيديو
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(videoUri, "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // (مهم جداً: إعطاء إذن مؤقت للمشغل)

        // (الرجوع للـ UI Thread لتشغيل الـ Intent)
        new Handler(Looper.getMainLooper()).post(() -> {
            // إخفاء شاشة الانتظار
            decryptionProgress.setVisibility(View.GONE);
            
            try {
                startActivity(intent);
                // (لا تقم بإعادة تحميل القائمة هنا، بل في onResume)
            } catch (Exception e) {
                Log.e(TAG, "Failed to start video player", e);
                Toast.makeText(this, "لا يوجد مشغل فيديو متاح لتشغيل هذا الملف", Toast.LENGTH_LONG).show();
                // (إذا فشل، أعد تحميل القائمة)
                 // [ 🛑 تم حذف loadDownloads() ]
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // [ ✅ تحديث القائمة ]
        // (سيخفي أيضاً شاشة الانتظار إذا كانت ظاهرة)
        decryptionProgress.setVisibility(View.GONE);
        // (المراقب الذي في "onCreate" سيتولى تحديث القائمة تلقائياً عند رجوع الـ Activity)
        // [ 🛑 تم حذف loadDownloads() ]
    }
}
