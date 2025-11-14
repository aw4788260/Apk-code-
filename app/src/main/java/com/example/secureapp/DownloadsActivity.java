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
import androidx.lifecycle.Observer; 
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.WorkInfo; 
import androidx.work.WorkManager; 

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap; 
import java.util.HashSet;
import java.util.List; 
import java.util.Map; 
import java.util.Set;
import java.util.UUID; 
import java.util.concurrent.Executors;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private TextView emptyText;
    private ProgressBar decryptionProgress;

    private static class DownloadItem {
        String title;
        String youtubeId;
        String status; 
        UUID workId; 

        DownloadItem(String title, String youtubeId, String status, UUID workId) {
            this.title = title;
            this.youtubeId = youtubeId;
            this.status = status;
            this.workId = workId;
        }

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

    private static final String TAG = "DownloadsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        listView = findViewById(R.id.downloads_listview);
        emptyText = findViewById(R.id.empty_text);
        decryptionProgress = findViewById(R.id.decryption_progress);
        
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, downloadItems);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DownloadItem clickedItem = downloadItems.get(position);
            
            if (clickedItem.status.equals("Completed")) {
                decryptAndPlayVideo(clickedItem.youtubeId, clickedItem.title);
            } else if (clickedItem.status.startsWith("فشل")) {
                Toast.makeText(this, "هذا التحميل فشل. الرجاء المحاولة مجدداً.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "هذا التحميل قيد التنفيذ...", Toast.LENGTH_SHORT).show();
            }
        });
        
        observeDownloadChanges();
    }

    /**
     * [ ✅✅✅ إصلاح جذري: هذا هو الكود الصحيح ]
     * (تم حذف .pruneWork() من هذه الدالة لمنع الحذف الفوري)
     */
    private void observeDownloadChanges() {
        // 1. جلب التحميلات المكتملة (القديمة) من SharedPreferences
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completedDownloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());
        
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
                    
                    downloadItems.clear(); 
                    Set<String> processedYoutubeIds = new HashSet<>();

                    if (workInfos != null) {
                        for (WorkInfo workInfo : workInfos) {
                            
                            WorkInfo.State state = workInfo.getState();
                            String youtubeId = null;
                            String title = null;
                            String statusStr = "";
                            
                            if (state == WorkInfo.State.RUNNING) {
                                // (نعتمد على الإصلاح في Worker لإرسال البيانات فوراً)
                                youtubeId = workInfo.getProgress().getString(DownloadWorker.KEY_YOUTUBE_ID);
                                title = workInfo.getProgress().getString(DownloadWorker.KEY_VIDEO_TITLE);
                                String progress = workInfo.getProgress().getString("progress");
                                statusStr = (progress != null) ? "جاري التحميل " + progress : "جاري التحميل...";
                                
                            } else if (state == WorkInfo.State.SUCCEEDED) {
                                youtubeId = workInfo.getOutputData().getString(DownloadWorker.KEY_YOUTUBE_ID);
                                title = workInfo.getOutputData().getString(DownloadWorker.KEY_VIDEO_TITLE);
                                statusStr = "Completed"; 
                                
                            } else if (state == WorkInfo.State.FAILED) {
                                youtubeId = workInfo.getOutputData().getString(DownloadWorker.KEY_YOUTUBE_ID);
                                title = workInfo.getOutputData().getString(DownloadWorker.KEY_VIDEO_TITLE);
                                
                                String error = workInfo.getOutputData().getString("error");
                                // [ ✅ هذا الكود سيعمل عندما يتم إصلاح المشكلة 1 ]
                                if (error != null && (error.contains("exit code 1") || error.contains("not created"))) {
                                    statusStr = "فشل: الفيديو غير متاح";
                                } else {
                                    statusStr = "فشل: خطأ غير معروف"; // [ 🐞 هذا ما تراه الآن ]
                                }
                            } else if (state == WorkInfo.State.ENQUEUED) {
                                statusStr = ""; // تجاهل
                            } else if (state == WorkInfo.State.CANCELLED || state == WorkInfo.State.BLOCKED) {
                                statusStr = "تم الإلغاء";
                            }

                            
                            if (youtubeId != null && title != null && !statusStr.isEmpty()) {
                                if (statusStr.equals("Completed")) {
                                    processedYoutubeIds.add(youtubeId);
                                } else {
                                    downloadItems.add(new DownloadItem(title, youtubeId, statusStr, workInfo.getId()));
                                    processedYoutubeIds.add(youtubeId); 
                                }
                            }
                        }
                    }

                    // 4. إضافة التحميلات المكتملة (التي لم تتم معالجتها بواسطة WorkManager)
                    for (Map.Entry<String, String> entry : completedMap.entrySet()) {
                        String youtubeId = entry.getKey();
                        String title = entry.getValue();
                        if (!processedYoutubeIds.contains(youtubeId)) {
                            downloadItems.add(new DownloadItem(title, youtubeId, "Completed", null));
                        }
                    }

                    // 5. تحديث الواجهة
                    if (downloadItems.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.GONE);
                    } else {
                        downloadItems.sort((item1, item2) -> {
                            if (item1.status.equals("Completed") && !item2.status.equals("Completed")) return 1;
                            if (!item1.status.equals("Completed") && item2.status.equals("Completed")) return -1;
                            return 0;
                        });
                        adapter.notifyDataSetChanged();
                        emptyText.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                    }
                    
                    // [ ✅✅✅ تم حذف .pruneWork() من هنا ]
                }
            });
    }


    private void decryptAndPlayVideo(String youtubeId, String videoTitle) {
        Log.d(TAG, "Starting decryption for " + youtubeId);
        
        decryptionProgress.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        
        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                File encryptedFile = new File(getFilesDir(), youtubeId + ".enc");
                if (!encryptedFile.exists()) {
                    throw new Exception("الملف المشفر غير موجود!");
                }

                decryptedFile = new File(getCacheDir(), "decrypted_video.mp4");
                if(decryptedFile.exists()) decryptedFile.delete();

                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

                EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                        encryptedFile,
                        this,
                        masterKeyAlias,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

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

                playDecryptedFile(decryptedFile, videoTitle);

            } catch (Exception e) {
                Log.e(TAG, "Decryption failed", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "فشل فك تشفير الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    decryptionProgress.setVisibility(View.GONE);
                    if (downloadItems.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                        listView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                    }
                });
                if(decryptedFile != null && decryptedFile.exists()) {
                    decryptedFile.delete();
                }
            }
        });
    }

    private void playDecryptedFile(File decryptedFile, String videoTitle) {
        String authority = getApplicationContext().getPackageName() + ".provider";
        Uri videoUri = FileProvider.getUriForFile(this, authority, decryptedFile);

        Log.d(TAG, "Playing video from URI: " + videoUri.toString());

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(videoUri, "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); 

        new Handler(Looper.getMainLooper()).post(() -> {
            decryptionProgress.setVisibility(View.GONE);
            
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start video player", e);
                Toast.makeText(this, "لا يوجد مشغل فيديو متاح لتشغيل هذا الملف", Toast.LENGTH_LONG).show();
                if (downloadItems.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        decryptionProgress.setVisibility(View.GONE);
        if (downloadItems.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    // [ ✅✅✅ بداية الإضافة: نقل .pruneWork() هنا ]
    @Override
    protected void onStop() {
        super.onStop();
        try {
            // (تنظيف المهام المكتملة "فقط" عند الخروج من الشاشة)
            WorkManager.getInstance(getApplicationContext()).pruneWork();
        } catch (Exception e) {
            Log.e(TAG, "Error pruning work onStop", e);
        }
    }
    // [ ✅✅✅ نهاية الإضافة ]
}
