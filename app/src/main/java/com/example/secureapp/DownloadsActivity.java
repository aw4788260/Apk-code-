package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private LinearLayout emptyLayout;
    private FrameLayout loadingOverlay;
    private TextView breadcrumbTitle;

    // ✅ عناصر التبويب الجديدة
    private Button tabVideos, tabFiles;
    private boolean isVideoTab = true; // الافتراضي: فيديوهات

    private String currentSubject = null;
    private String currentChapter = null;

    private ArrayList<DownloadItem> allDownloadsMasterList = new ArrayList<>();
    private ArrayList<DownloadItem> displayList = new ArrayList<>();
    private CustomAdapter adapter;

    private static class DownloadItem {
        String title;
        String youtubeId;
        String duration;
        String status;
        UUID workId;
        int progress = 0;
        String subject;
        String chapter;
        String filename;
        boolean isFolder;
        String folderName;

        DownloadItem(String title, String youtubeId, String duration, String status, UUID workId, String subject, String chapter, String filename) {
            this.title = title;
            this.youtubeId = youtubeId;
            this.duration = duration;
            this.status = status;
            this.workId = workId;
            this.subject = subject;
            this.chapter = chapter;
            this.filename = filename;
            this.isFolder = false;
        }

        DownloadItem(String folderName) {
            this.isFolder = true;
            this.folderName = folderName;
            this.status = "Folder";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_downloads);

        listView = findViewById(R.id.downloads_listview);
        emptyLayout = findViewById(R.id.empty_layout);
        loadingOverlay = findViewById(R.id.loading_overlay);
        breadcrumbTitle = findViewById(R.id.header_layout).findViewById(R.id.video_title); 

        // ✅ تعريف أزرار التبويب
        tabVideos = findViewById(R.id.tab_videos);
        tabFiles = findViewById(R.id.tab_files);

        // ✅ تفعيل التبديل
        tabVideos.setOnClickListener(v -> switchTab(true));
        tabFiles.setOnClickListener(v -> switchTab(false));

        adapter = new CustomAdapter(this, displayList);
        listView.setAdapter(adapter);

        observeDownloadChanges();
    }

    // ✅ دالة التبديل بين الفيديوهات والملفات
    private void switchTab(boolean videos) {
        isVideoTab = videos;
        // تحديث الألوان (Active / Inactive)
        if (videos) {
            tabVideos.setBackgroundResource(R.drawable.tab_active);
            tabVideos.setTextColor(Color.BLACK);
            tabFiles.setBackgroundResource(R.drawable.tab_inactive);
            tabFiles.setTextColor(Color.WHITE);
        } else {
            tabFiles.setBackgroundResource(R.drawable.tab_active);
            tabFiles.setTextColor(Color.BLACK);
            tabVideos.setBackgroundResource(R.drawable.tab_inactive);
            tabVideos.setTextColor(Color.WHITE);
        }
        // إعادة فلترة القائمة
        refreshDisplayList();
    }

    @Override
    public void onBackPressed() {
        if (currentChapter != null) {
            currentChapter = null;
            refreshDisplayList();
        } else if (currentSubject != null) {
            currentSubject = null;
            refreshDisplayList();
        } else {
            super.onBackPressed();
        }
    }

    private void observeDownloadChanges() {
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completedDownloads = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());

        WorkManager.getInstance(this).getWorkInfosByTagLiveData("download_work_tag")
            .observe(this, workInfos -> {
                allDownloadsMasterList.clear();
                Set<String> processedIds = new HashSet<>();

                if (workInfos != null) {
                    for (WorkInfo workInfo : workInfos) {
                        WorkInfo.State state = workInfo.getState();
                        if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                            String youtubeId = workInfo.getProgress().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            String title = workInfo.getProgress().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            
                            int progress = 0;
                            try { progress = Integer.parseInt(workInfo.getProgress().getString("progress").replace("%","").trim()); } catch(Exception e){}

                            if (youtubeId != null) {
                                // ✅ إذا كان ID يبدأ بـ PDF_ أو العنوان يحتوي على PDF، نميزه
                                // لكن هنا سنعامله كعنصر عادي والفلترة تتم في refreshDisplayList
                                DownloadItem item = new DownloadItem(title, youtubeId, null, "Running", workInfo.getId(), "التحميلات الجارية", "قيد التنزيل", null);
                                item.progress = progress;
                                allDownloadsMasterList.add(item);
                                processedIds.add(youtubeId);
                            }
                        }
                    }
                }

                for (String videoData : completedDownloads) {
                    String[] parts = videoData.split("\\|");
                    if (parts.length >= 2) {
                        String id = parts[0];
                        if (processedIds.contains(id)) continue; 
                        
                        String title = parts[1];
                        String dur = (parts.length > 2) ? parts[2] : "unknown";
                        String subject = (parts.length > 3) ? parts[3] : "غير مصنف";
                        String chapter = (parts.length > 4) ? parts[4] : "عام";
                        String filename = (parts.length > 5) ? parts[5] : null;

                        allDownloadsMasterList.add(new DownloadItem(title, id, dur, "Completed", null, subject, chapter, filename));
                    }
                }

                refreshDisplayList();
            });
    }

    private void refreshDisplayList() {
        displayList.clear();
        TextView headerTitle = findViewById(R.id.header_layout).findViewById(R.id.downloads_listview) != null ? null : (TextView)((LinearLayout)findViewById(R.id.header_layout)).getChildAt(1);

        // ✅ إعداد قائمة مؤقتة للفلترة
        ArrayList<DownloadItem> filteredList = new ArrayList<>();

        // 1. فلترة النوع أولاً (فيديو أو PDF)
        for (DownloadItem item : allDownloadsMasterList) {
            boolean isPdf = "PDF".equals(item.duration) || item.youtubeId.startsWith("PDF_");
            
            if (isVideoTab && !isPdf) {
                filteredList.add(item); // نحن في تبويب الفيديو والعنصر فيديو
            } else if (!isVideoTab && isPdf) {
                filteredList.add(item); // نحن في تبويب الملفات والعنصر PDF
            }
        }

        // 2. تطبيق منطق المجلدات على القائمة المفلترة
        if (currentSubject == null) {
            if(headerTitle != null) headerTitle.setText(isVideoTab ? "المكتبة (فيديو)" : "المكتبة (ملفات)");
            
            Set<String> subjects = new HashSet<>();
            for (DownloadItem item : filteredList) {
                if (item.subject != null) subjects.add(item.subject);
            }
            for (String sub : subjects) displayList.add(new DownloadItem(sub));

        } else if (currentChapter == null) {
            if(headerTitle != null) headerTitle.setText(currentSubject);

            Set<String> chapters = new HashSet<>();
            for (DownloadItem item : filteredList) {
                if (item.subject != null && item.subject.equals(currentSubject)) {
                    chapters.add(item.chapter);
                }
            }
            for (String chap : chapters) displayList.add(new DownloadItem(chap));

        } else {
            if(headerTitle != null) headerTitle.setText(currentSubject + " > " + currentChapter);

            for (DownloadItem item : filteredList) {
                if (item.subject != null && item.subject.equals(currentSubject) && 
                    item.chapter != null && item.chapter.equals(currentChapter)) {
                    displayList.add(item);
                }
            }
            Collections.sort(displayList, (o1, o2) -> {
               if(o1.status.equals("Running") && o2.status.equals("Completed")) return -1;
               if(o1.status.equals("Completed") && o2.status.equals("Running")) return 1;
               return 0;
            });
        }

        if (displayList.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

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

            TextView titleView = convertView.findViewById(R.id.video_title);
            TextView qualityView = convertView.findViewById(R.id.video_quality);
            TextView folderPathView = convertView.findViewById(R.id.folder_path);
            View detailsLayout = convertView.findViewById(R.id.details_layout);
            View progressLayout = convertView.findViewById(R.id.progress_layout);
            ImageView statusIcon = convertView.findViewById(R.id.status_icon);
            ImageView deleteBtn = convertView.findViewById(R.id.delete_btn);
            View iconContainer = convertView.findViewById(R.id.icon_container);
            
            if (item.isFolder) {
                titleView.setText(item.folderName);
                qualityView.setVisibility(View.GONE);
                folderPathView.setVisibility(View.GONE);
                detailsLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);

                statusIcon.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(android.R.drawable.ic_menu_view);
                
                iconContainer.setOnClickListener(v -> openFolder(item.folderName));
                convertView.setOnClickListener(v -> openFolder(item.folderName));
                
                return convertView;
            }

            deleteBtn.setVisibility(View.VISIBLE);
            
            // التحقق من نوع العنصر (PDF أو فيديو) لتغيير الأيقونة
            boolean isPdf = "PDF".equals(item.duration) || item.youtubeId.startsWith("PDF_");

            String displayTitle = item.title;
            String displayQuality = isPdf ? "PDF" : "SD"; // جودة افتراضية

            if (!isPdf && item.title != null && item.title.contains("(") && item.title.endsWith(")")) {
                try {
                    int lastOpen = item.title.lastIndexOf("(");
                    displayTitle = item.title.substring(0, lastOpen).trim();
                    String extracted = item.title.substring(lastOpen + 1, item.title.length() - 1);
                    if(!extracted.isEmpty()) displayQuality = extracted;
                } catch (Exception e) { displayTitle = item.title; }
            }

            titleView.setText(displayTitle);
            qualityView.setText(displayQuality);
            qualityView.setVisibility(View.VISIBLE);
            folderPathView.setVisibility(View.GONE); 

            TextView durationView = convertView.findViewById(R.id.video_duration);
            TextView sizeView = convertView.findViewById(R.id.video_size);
            ProgressBar progressBar = convertView.findViewById(R.id.download_progress);
            TextView statusText = convertView.findViewById(R.id.status_text);
            ProgressBar loadingSpinner = convertView.findViewById(R.id.loading_spinner);

            if (item.status.equals("Completed")) {
                statusIcon.setVisibility(View.VISIBLE);
                // ✅ تغيير الأيقونة حسب النوع
                if (isPdf) {
                    statusIcon.setImageResource(android.R.drawable.ic_menu_slideshow);
                } else {
                    statusIcon.setImageResource(android.R.drawable.ic_media_play);
                }
                
                loadingSpinner.setVisibility(View.GONE);
                detailsLayout.setVisibility(View.VISIBLE);
                progressLayout.setVisibility(View.GONE);
                sizeView.setText(getFileSizeString(item));
                
                if (isPdf) {
                    durationView.setText("مستند");
                } else {
                    durationView.setText(formatDuration(item.duration));
                }

                // ✅ التعامل مع النقر للتشغيل (فيديو أو PDF)
                View.OnClickListener playAction = v -> {
                    if (isPdf) {
                        openPdfViewer(item);
                    } else {
                        decryptAndPlayVideo(item);
                    }
                };

                iconContainer.setOnClickListener(playAction);
                convertView.setOnClickListener(playAction);

            } else if (item.status.startsWith("فشل")) {
                statusIcon.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(android.R.drawable.stat_notify_error);
                detailsLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                statusText.setText("فشل");
                iconContainer.setOnClickListener(null);
                convertView.setOnClickListener(null);
            } else {
                statusIcon.setVisibility(View.GONE);
                loadingSpinner.setVisibility(View.VISIBLE);
                detailsLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(item.progress);
                statusText.setText(item.progress + "%");
                iconContainer.setOnClickListener(v -> Toast.makeText(getContext(), "جاري التحميل...", Toast.LENGTH_SHORT).show());
                convertView.setOnClickListener(null);
            }

            deleteBtn.setOnClickListener(v -> confirmDelete(item));

            return convertView;
        }
    }

    private void openFolder(String folderName) {
        if (currentSubject == null) {
            currentSubject = folderName;
        } else if (currentChapter == null) {
            currentChapter = folderName;
        }
        refreshDisplayList();
    }

    private File getTargetFile(DownloadItem item) {
        if (item.subject != null && item.chapter != null && item.filename != null) {
            File subjectDir = new File(getFilesDir(), item.subject);
            File chapterDir = new File(subjectDir, item.chapter);
            return new File(chapterDir, item.filename + ".enc");
        }
        return new File(getFilesDir(), item.youtubeId + ".enc");
    }

    private String getFileSizeString(DownloadItem item) {
        File file = getTargetFile(item);
        if (file.exists()) {
            long length = file.length();
            double mb = length / (1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return "-- MB";
    }

    private String formatDuration(String durationStr) {
        try {
            if (durationStr == null || durationStr.equals("unknown") || durationStr.equals("PDF")) return "--:--";
            double secDouble = Double.parseDouble(durationStr);
            long totalSec = (long) secDouble;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            return String.format(Locale.US, "%d:%02d", min, sec);
        } catch (Exception e) {
            return "--:--";
        }
    }

    private void confirmDelete(DownloadItem item) {
        new AlertDialog.Builder(this)
            .setTitle("حذف")
            .setMessage("هل أنت متأكد من حذف " + item.title + "؟")
            .setPositiveButton("حذف", (dialog, which) -> deleteDownload(item))
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void deleteDownload(DownloadItem item) {
        File file = getTargetFile(item);
        if (file.exists()) file.delete();
        
        SharedPreferences prefs = getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> currentSet = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());
        Set<String> newSet = new HashSet<>();
        
        for (String s : currentSet) {
            if (!s.startsWith(item.youtubeId + "|")) {
                newSet.add(s);
            }
        }
        prefs.edit().putStringSet(DownloadWorker.KEY_DOWNLOADS_SET, newSet).apply();
        
        if (item.workId != null) {
            WorkManager.getInstance(this).cancelWorkById(item.workId);
        }

        observeDownloadChanges(); 
        Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
    }

    private void decryptAndPlayVideo(DownloadItem item) {
        File encryptedFile = getTargetFile(item);
        if (encryptedFile.exists()) {
            playDecryptedFile(encryptedFile, item.title, item.duration);
        } else {
            Toast.makeText(this, "الملف غير موجود!", Toast.LENGTH_SHORT).show();
        }
    }

    private void playDecryptedFile(File encryptedFile, String videoTitle, String duration) {
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "User");

        Intent intent = new Intent(DownloadsActivity.this, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", encryptedFile.getAbsolutePath());
        intent.putExtra("WATERMARK_TEXT", userId);
        intent.putExtra("DURATION", duration);

        startActivity(intent);
    }

    // ✅ دالة فتح الـ PDF الأوفلاين
    private void openPdfViewer(DownloadItem item) {
        File encryptedFile = getTargetFile(item);
        if (encryptedFile.exists()) {
            // استخراج الـ ID الأصلي من "PDF_123"
            String realPdfId = item.youtubeId.replace("PDF_", "");
            
            Intent intent = new Intent(DownloadsActivity.this, PdfViewerActivity.class);
            // نمرر الـ ID فقط، والنشاط سيعرف كيف يجد الملف في التخزين الآمن بناءً عليه
            intent.putExtra("PDF_ID", realPdfId);
            intent.putExtra("PDF_TITLE", item.title);
            startActivity(intent);
        } else {
            Toast.makeText(this, "ملف الـ PDF غير موجود!", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override protected void onResume() { super.onResume(); loadingOverlay.setVisibility(View.GONE); observeDownloadChanges(); }
}
