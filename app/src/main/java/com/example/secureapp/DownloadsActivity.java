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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private TextView emptyText;
    private ProgressBar decryptionProgress;
    
    private ArrayList<String> videoTitles = new ArrayList<>();
    private ArrayList<String> videoIds = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private static final String TAG = "DownloadsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        // ربط الواجهة
        listView = findViewById(R.id.downloads_listview);
        emptyText = findViewById(R.id.empty_text);
        decryptionProgress = findViewById(R.id.decryption_progress);
        
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoTitles);
        listView.setAdapter(adapter);

        // الضغط على العنصر
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String clickedTitle = videoTitles.get(position);
            String clickedYoutubeId = videoIds.get(position);
            
            // [ ✅✅ هنا يبدأ فك التشفير والتشغيل ]
            decryptAndPlayVideo(clickedYoutubeId, clickedTitle);
        });
    }

    private void loadDownloads() {
        // (جلب البيانات من نفس المكان الذي حفظ فيه الـ Worker)
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> downloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());

        videoTitles.clear();
        videoIds.clear();

        if (downloads.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            for (String videoData : downloads) {
                // "ID|Title"
                String[] parts = videoData.split("\\|", 2);
                if (parts.length == 2) {
                    videoIds.add(parts[0]);   // "abc12345"
                    videoTitles.add(parts[1]); // "شرح الدرس الأول"
                }
            }
            adapter.notifyDataSetChanged();
            emptyText.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

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
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

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
                    loadDownloads(); // (إعادة تحميل القائمة)
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
                loadDownloads();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // [ ✅ تحديث القائمة ]
        // لتحديث القائمة تلقائياً عند الرجوع لهذه الشاشة
        // (سيخفي أيضاً شاشة الانتظار إذا كانت ظاهرة)
        decryptionProgress.setVisibility(View.GONE);
        loadDownloads();
    }
}
