package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private LinearLayout emptyLayout;
    private FrameLayout loadingOverlay;

    // كلاس البيانات
    private static class DownloadItem {
        String title;
        String youtubeId;
        String duration;
        String status; // "Completed" or "Running" or "Failed"
        UUID workId;
        int progress = 0;

        DownloadItem(String title, String youtubeId, String duration, String status, UUID workId) {
            this.title = title;
            this.youtubeId = youtubeId;
            this.duration = duration;
            this.status = status;
            this.workId = workId;
        }
    }

    private ArrayList<DownloadItem> downloadItems = new ArrayList<>();
    private CustomAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_downloads);

        listView = findViewById(R.id.downloads_listview);
        emptyLayout = findViewById(R.id.empty_layout);
        loadingOverlay = findViewById(R.id.loading_overlay);

        adapter = new CustomAdapter(this, downloadItems);
        listView.setAdapter(adapter);

        observeDownloadChanges();
    }

    // --- Adapter مخصص للبطاقات الجميلة ---
    private class CustomAdapter extends ArrayAdapter<DownloadItem> {
        public CustomAdapter(@NonNull Context context, ArrayList<DownloadItem> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_download, parent, false);
            }

            DownloadItem item = getItem(position);
            if (item == null) return convertView;

            // ربط العناصر
            TextView titleView = convertView.findViewById(R.id.video_title);
            TextView durationView = convertView.findViewById(R.id.video_duration);
            TextView sizeView = convertView.findViewById(R.id.video_size);
            ImageView statusIcon = convertView.findViewById(R.id.status_icon);
            View iconContainer = convertView.findViewById(R.id.icon_container);
            ProgressBar progressBar = convertView.findViewById(R.id.download_progress);
            TextView statusText = convertView.findViewById(R.id.status_text);
            ImageView deleteBtn = convertView.findViewById(R.id.delete_btn);

            // تعيين البيانات
            titleView.setText(item.title);

            // 1. حالة التشغيل والتحميل
            if (item.status.equals("Completed")) {
                statusIcon.setImageResource(android.R.drawable.ic_media_play);
                progressBar.setVisibility(View.GONE);
                statusText.setVisibility(View.GONE);
                sizeView.setVisibility(View.VISIBLE);
                durationView.setVisibility(View.VISIBLE);
                
                // حساب الحجم والمدة
                sizeView.setText(getFileSizeString(item.youtubeId));
                durationView.setText(formatDuration(item.duration));

                // الضغط على الأيقونة يشغل الفيديو
                iconContainer.setOnClickListener(v -> decryptAndPlayVideo(item.youtubeId, item.title, item.duration));
                
            } else if (item.status.startsWith("فشل")) {
                statusIcon.setImageResource(android.R.drawable.stat_notify_error);
                statusText.setText("فشل التحميل");
                statusText.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                sizeView.setVisibility(View.GONE);
                
                iconContainer.setOnClickListener(v -> Toast.makeText(getContext(), "فشل التحميل", Toast.LENGTH_SHORT).show());

            } else {
                // جاري التحميل
                statusIcon.setImageResource(android.R.drawable.stat_sys_download);
                statusText.setText("جاري التحميل... " + item.progress + "%");
                statusText.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(item.progress);
                sizeView.setVisibility(View.GONE); // نخفي الحجم أثناء التحميل
                
                iconContainer.setOnClickListener(v -> Toast.makeText(getContext(), "جاري التحميل...", Toast.LENGTH_SHORT).show());
            }

            // زر الحذف
            deleteBtn.setOnClickListener(v -> confirmDelete(item));

            // الضغط على البطاقة كاملة
            convertView.setOnClickListener(v -> {
                if (item.status.equals("Completed")) {
                    decryptAndPlayVideo(item.youtubeId, item.title, item.duration);
                }
            });

            return convertView;
        }
    }
    
    // --- دوال مساعدة للحجم والوقت ---

    private String getFileSizeString(String youtubeId) {
        File file = new File(getFilesDir(), youtubeId + ".enc");
        if (file.exists()) {
            long length = file.length();
            // تحويل بايت إلى ميجابايت
            double mb = length / (1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return "-- MB";
    }

    private String formatDuration(String durationStr) {
        try {
            if (durationStr == null || durationStr.equals("unknown")) return "--:--";
            double secDouble = Double.parseDouble(durationStr);
            long totalSec = (long) secDouble;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            return String.format(Locale.US, "%d:%02d", min, sec);
        } catch (Exception e) {
            return "--:--";
        }
    }

    // --- منطق الحذف ---
    private void confirmDelete(DownloadItem item) {
        new AlertDialog.Builder(this)
            .setTitle("حذف الفيديو")
            .setMessage("هل أنت متأكد من حذف \"" + item.title + "\"؟")
            .setPositiveButton("حذف", (dialog, which) -> deleteDownload(item))
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void deleteDownload(DownloadItem item) {
        // 1. حذف الملف
        File file = new File(getFilesDir(), item.youtubeId + ".enc");
        if (file.exists()) file.delete();
        
        // 2. حذف من SharedPreferences
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> currentSet = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());
        Set<String> newSet = new HashSet<>();
        
        for (String s : currentSet) {
            if (!s.startsWith(item.youtubeId + "|")) {
                newSet.add(s);
            }
        }
        prefs.edit().putStringSet(DownloadWorker.KEY_DOWNLOADS_SET, newSet).apply();
        
        // 3. تحديث القائمة
        observeDownloadChanges(); // إعادة تحميل
        Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
    }


    // --- (باقي المنطق الأساسي: المراقبة والتشغيل) ---

    private void observeDownloadChanges() {
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completedDownloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());

        WorkManager.getInstance(this).getWorkInfosByTagLiveData("download_work_tag")
            .observe(this, workInfos -> {
                downloadItems.clear();
                Set<String> processedIds = new HashSet<>();

                // 1. إضافة التحميلات الجارية
                if (workInfos != null) {
                    for (WorkInfo workInfo : workInfos) {
                        String youtubeId = null, title = null;
                        int progress = 0;
                        WorkInfo.State state = workInfo.getState();

                        if (state == WorkInfo.State.RUNNING) {
                            youtubeId = workInfo.getProgress().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            title = workInfo.getProgress().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            String progStr = workInfo.getProgress().getString("progress"); // "50%"
                            if(progStr != null) {
                                try { progress = Integer.parseInt(progStr.replace("%","").trim()); } catch(Exception e){}
                            }
                            
                            if (youtubeId != null) {
                                DownloadItem item = new DownloadItem(title, youtubeId, null, "Running", workInfo.getId());
                                item.progress = progress;
                                downloadItems.add(item);
                                processedIds.add(youtubeId);
                            }
                        }
                    }
                }

                // 2. إضافة المكتملة
                for (String videoData : completedDownloads) {
                    String[] parts = videoData.split("\\|", 3);
                    if (parts.length >= 2) {
                        String id = parts[0];
                        if (processedIds.contains(id)) continue; // لا تضف المكتمل إذا كان يبدو جارياً بالخطأ
                        
                        String title = parts[1];
                        String dur = (parts.length == 3) ? parts[2] : "unknown";
                        downloadItems.add(new DownloadItem(title, id, dur, "Completed", null));
                    }
                }

                // تحديث الواجهة
                if (downloadItems.isEmpty()) {
                    emptyLayout.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.GONE);
                } else {
                    // ترتيب: الجاري أولاً، ثم المكتمل
                    Collections.sort(downloadItems, (o1, o2) -> {
                       if(o1.status.equals("Running") && o2.status.equals("Completed")) return -1;
                       if(o1.status.equals("Completed") && o2.status.equals("Running")) return 1;
                       return 0;
                    });
                    
                    emptyLayout.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                }
            });
    }

    private void decryptAndPlayVideo(String youtubeId, String videoTitle, String duration) {
        loadingOverlay.setVisibility(View.VISIBLE); // إظهار شاشة التحميل

        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                File encryptedFile = new File(getFilesDir(), youtubeId + ".enc");
                if (!encryptedFile.exists()) throw new Exception("الملف غير موجود");

                // فك التشفير لملف MP4
                decryptedFile = new File(getCacheDir(), "decrypted_video.mp4");
                if(decryptedFile.exists()) decryptedFile.delete();

                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                        encryptedFile, this, masterKeyAlias,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                InputStream encryptedInputStream = encryptedFileObj.openFileInput();
                OutputStream decryptedOutputStream = new FileOutputStream(decryptedFile);

                byte[] buffer = new byte[1024 * 8]; // 8KB buffer
                int bytesRead;
                while ((bytesRead = encryptedInputStream.read(buffer)) != -1) {
                    decryptedOutputStream.write(buffer, 0, bytesRead);
                }
                decryptedOutputStream.flush();
                decryptedOutputStream.close();
                encryptedInputStream.close();

                playDecryptedFile(decryptedFile, videoTitle, duration);

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "فشل التشغيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    loadingOverlay.setVisibility(View.GONE);
                });
                if(decryptedFile != null && decryptedFile.exists()) decryptedFile.delete();
            }
        });
    }

    private void playDecryptedFile(File decryptedFile, String videoTitle, String duration) {
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "User");

        Intent intent = new Intent(DownloadsActivity.this, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", decryptedFile.getAbsolutePath());
        intent.putExtra("WATERMARK_TEXT", userId);
        intent.putExtra("DURATION", duration);

        new Handler(Looper.getMainLooper()).post(() -> {
            loadingOverlay.setVisibility(View.GONE);
            startActivity(intent);
        });
    }
    
    @Override protected void onResume() { super.onResume(); loadingOverlay.setVisibility(View.GONE); observeDownloadChanges(); }
}
