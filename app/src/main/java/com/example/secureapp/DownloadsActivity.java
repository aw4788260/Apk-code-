package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.Collections;
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
    private TextView logsTextView;
    private Button clearLogsButton;

    private static class DownloadItem {
        String title;
        String youtubeId;
        String duration;
        String status;
        UUID workId;

        DownloadItem(String title, String youtubeId, String duration, String status, UUID workId) {
            this.title = title;
            this.youtubeId = youtubeId;
            this.duration = duration;
            this.status = status;
            this.workId = workId;
        }

        @NonNull
        @Override
        public String toString() {
            String durText = "";
            // ✅✅ تصحيح قراءة الوقت (يدعم الأرقام العشرية)
            if (duration != null && !duration.equals("unknown")) {
                try {
                    double secDouble = Double.parseDouble(duration);
                    long sec = (long) secDouble;
                    long min = sec / 60;
                    long remSec = sec % 60;
                    durText = String.format(" (%d:%02d)", min, remSec);
                } catch (Exception e) {
                    // تجاهل الخطأ بصمت
                }
            }

            if (status.equals("Completed")) {
                return title + durText + "\n✅ جاهز";
            }
            return title + "\n" + status;
        }
    }

    private ArrayList<DownloadItem> downloadItems = new ArrayList<>();
    private ArrayAdapter<DownloadItem> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_downloads);

        listView = findViewById(R.id.downloads_listview);
        emptyText = findViewById(R.id.empty_text);
        decryptionProgress = findViewById(R.id.decryption_progress);
        logsTextView = findViewById(R.id.logs_textview);
        clearLogsButton = findViewById(R.id.clear_logs_button);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, downloadItems);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DownloadItem clickedItem = downloadItems.get(position);
            if (clickedItem.status.equals("Completed")) {
                // نرسل المدة المحفوظة للمشغل
                decryptAndPlayVideo(clickedItem.youtubeId, clickedItem.title, clickedItem.duration);
            } else if (clickedItem.status.startsWith("فشل")) {
                Toast.makeText(this, "هذا التحميل فشل.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "قيد التنفيذ...", Toast.LENGTH_SHORT).show();
            }
        });

        clearLogsButton.setOnClickListener(v -> {
            DownloadLogger.clearLogs(this);
            loadLogs();
        });

        observeDownloadChanges();
        loadLogs();
    }

    private void observeDownloadChanges() {
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completedDownloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());

        WorkManager.getInstance(this).getWorkInfosByTagLiveData("download_work_tag")
            .observe(this, workInfos -> {
                downloadItems.clear();
                Set<String> processedYoutubeIds = new HashSet<>();

                if (workInfos != null) {
                    for (WorkInfo workInfo : workInfos) {
                        WorkInfo.State state = workInfo.getState();
                        String youtubeId = null, title = null, statusStr = "";
                        if (state == WorkInfo.State.RUNNING) {
                            youtubeId = workInfo.getProgress().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            title = workInfo.getProgress().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            statusStr = "جاري التحميل " + workInfo.getProgress().getString("progress");
                        } else if (state == WorkInfo.State.FAILED) {
                            youtubeId = workInfo.getOutputData().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            title = workInfo.getOutputData().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            statusStr = "فشل";
                        }

                        if (youtubeId != null && title != null && !statusStr.isEmpty()) {
                            downloadItems.add(new DownloadItem(title, youtubeId, null, statusStr, workInfo.getId()));
                            processedYoutubeIds.add(youtubeId);
                        }
                    }
                }

                for (String videoData : completedDownloads) {
                    String[] parts = videoData.split("\\|", 3);
                    if (parts.length >= 2) {
                        String id = parts[0];
                        String title = parts[1];
                        String dur = (parts.length == 3) ? parts[2] : "unknown";
                        downloadItems.add(new DownloadItem(title, id, dur, "Completed", null));
                    }
                }

                if (downloadItems.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE); listView.setVisibility(View.GONE);
                } else {
                    downloadItems.sort((i1, i2) -> i1.status.equals("Completed") ? 1 : -1);
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(View.GONE); listView.setVisibility(View.VISIBLE);
                }
                loadLogs();
            });
    }

    private void loadLogs() {
        ArrayList<String> logs = DownloadLogger.getLogs(this);
        if (logs.isEmpty()) logsTextView.setText("لا توجد سجلات.");
        else {
            Collections.reverse(logs);
            StringBuilder sb = new StringBuilder();
            for (String log : logs) sb.append(log).append("\n\n");
            logsTextView.setText(sb.toString());
        }
    }

    private void decryptAndPlayVideo(String youtubeId, String videoTitle, String duration) {
        decryptionProgress.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                File encryptedFile = new File(getFilesDir(), youtubeId + ".enc");
                if (!encryptedFile.exists()) throw new Exception("الملف غير موجود");

                decryptedFile = new File(getCacheDir(), "decrypted_video.ts");
                if(decryptedFile.exists()) decryptedFile.delete();

                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                        encryptedFile, this, masterKeyAlias,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                InputStream encryptedInputStream = encryptedFileObj.openFileInput();
                OutputStream decryptedOutputStream = new FileOutputStream(decryptedFile);

                byte[] buffer = new byte[1024 * 8];
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
                    Toast.makeText(this, "فشل فك التشفير", Toast.LENGTH_LONG).show();
                    decryptionProgress.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
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
        // ✅ تمرير المدة
        intent.putExtra("DURATION", duration);

        new Handler(Looper.getMainLooper()).post(() -> {
            decryptionProgress.setVisibility(View.GONE);
            startActivity(intent);
        });
    }
    
    @Override protected void onResume() { super.onResume(); loadLogs(); decryptionProgress.setVisibility(View.GONE); if(downloadItems.isEmpty()) {emptyText.setVisibility(View.VISIBLE); listView.setVisibility(View.GONE);} else {emptyText.setVisibility(View.GONE); listView.setVisibility(View.VISIBLE);} }
}
