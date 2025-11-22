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
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DownloadsActivity extends AppCompatActivity {

    private ListView listView;
    private LinearLayout emptyLayout;
    private FrameLayout loadingOverlay;
    private TextView breadcrumbTitle; // لعرض المسار الحالي (مثلاً: الفيزياء > الفصل الأول)

    // متغيرات التصفح (المجلدات)
    private String currentSubject = null; // المادة الحالية (null = القائمة الرئيسية)
    private String currentChapter = null; // الشابتر الحالي (null = داخل المادة)

    // القائمة الرئيسية التي تحتوي على كل البيانات الخام
    private ArrayList<DownloadItem> allDownloadsMasterList = new ArrayList<>();
    
    // القائمة التي يتم عرضها حالياً (سواء مجلدات أو فيديوهات)
    private ArrayList<DownloadItem> displayList = new ArrayList<>();
    private CustomAdapter adapter;

    private static class DownloadItem {
        String title;
        String youtubeId;
        String duration;
        String status;
        UUID workId;
        int progress = 0;
        
        // بيانات المسار
        String subject;
        String chapter;
        String filename;
        
        // [✅ جديد] نوع العنصر: هل هو مجلد أم ملف؟
        boolean isFolder;
        String folderName; // اسم المجلد للعرض

        // كونتستركتور للفيديو
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

        // كونتستركتور للمجلد
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
        
        // نقوم بتغيير عنوان الـ Header ليعرض المسار
        breadcrumbTitle = findViewById(R.id.header_layout).findViewById(R.id.video_title); // سنستخدم الـ ID الموجود في الـ header لو كان متاحاً، أو نضيف TextView جديد. 
        // لتسهيل الأمر، سنعتمد على TextView موجود في الـ Header في ملف activity_downloads.xml
        // سأقوم بتحديث العنوان برمجياً بناءً على التصفح.

        adapter = new CustomAdapter(this, displayList);
        listView.setAdapter(adapter);

        observeDownloadChanges();
    }

    // [✅ جديد] التحكم في زر الرجوع للتنقل للأعلى في المجلدات
    @Override
    public void onBackPressed() {
        if (currentChapter != null) {
            // إذا كنا داخل شابتر، ارجع للمادة
            currentChapter = null;
            refreshDisplayList();
        } else if (currentSubject != null) {
            // إذا كنا داخل مادة، ارجع للقائمة الرئيسية
            currentSubject = null;
            refreshDisplayList();
        } else {
            // إذا كنا في الرئيسية، اخرج من التطبيق
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

                // 1. إضافة التحميلات الجارية (ستظهر في الجذر أو مجلد "جار التحميل")
                if (workInfos != null) {
                    for (WorkInfo workInfo : workInfos) {
                        WorkInfo.State state = workInfo.getState();
                        if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                            String youtubeId = workInfo.getProgress().getString(DownloadWorker.KEY_YOUTUBE_ID);
                            String title = workInfo.getProgress().getString(DownloadWorker.KEY_VIDEO_TITLE);
                            // للأسف الـ WorkInfo progress لا يحتوي على المجلدات حالياً إلا إذا عدلناه، 
                            // سنفترض أنها "جار التحميل" مؤقتاً
                            
                            int progress = 0;
                            try { progress = Integer.parseInt(workInfo.getProgress().getString("progress").replace("%","").trim()); } catch(Exception e){}

                            if (youtubeId != null) {
                                DownloadItem item = new DownloadItem(title, youtubeId, null, "Running", workInfo.getId(), "التحميلات الجارية", "قيد التنزيل", null);
                                item.progress = progress;
                                allDownloadsMasterList.add(item);
                                processedIds.add(youtubeId);
                            }
                        }
                    }
                }

                // 2. إضافة التحميلات المكتملة
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

                // 3. تحديث العرض بناءً على الموقع الحالي
                refreshDisplayList();
            });
    }

    // [✅ جديد] دالة لتحديث القائمة المعروضة بناءً على المجلد الحالي
    private void refreshDisplayList() {
        displayList.clear();
        TextView headerTitle = findViewById(R.id.header_layout).findViewById(R.id.downloads_listview) != null ? null : (TextView)((LinearLayout)findViewById(R.id.header_layout)).getChildAt(1); // محاولة الوصول للعنوان الفرعي

        if (currentSubject == null) {
            // --- المستوى 1: عرض المواد (Subjects) ---
            if(headerTitle != null) headerTitle.setText("المكتبة (المواد)");
            
            Set<String> subjects = new HashSet<>();
            for (DownloadItem item : allDownloadsMasterList) {
                if (item.subject != null) subjects.add(item.subject);
            }
            
            for (String sub : subjects) {
                displayList.add(new DownloadItem(sub)); // إضافة كمجلد
            }

        } else if (currentChapter == null) {
            // --- المستوى 2: عرض الشباتر (Chapters) داخل المادة ---
            if(headerTitle != null) headerTitle.setText(currentSubject);

            Set<String> chapters = new HashSet<>();
            for (DownloadItem item : allDownloadsMasterList) {
                if (item.subject != null && item.subject.equals(currentSubject)) {
                    chapters.add(item.chapter);
                }
            }
            
            for (String chap : chapters) {
                displayList.add(new DownloadItem(chap)); // إضافة كمجلد
            }

        } else {
            // --- المستوى 3: عرض الفيديوهات (Files) داخل الشابتر ---
            if(headerTitle != null) headerTitle.setText(currentSubject + " > " + currentChapter);

            for (DownloadItem item : allDownloadsMasterList) {
                if (item.subject != null && item.subject.equals(currentSubject) && 
                    item.chapter != null && item.chapter.equals(currentChapter)) {
                    displayList.add(item); // إضافة كملف فيديو
                }
            }
            
            // ترتيب الفيديوهات (الجاري تحميله أولاً)
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
            TextView folderPathView = convertView.findViewById(R.id.folder_path); // TextView الجديد
            View detailsLayout = convertView.findViewById(R.id.details_layout);
            View progressLayout = convertView.findViewById(R.id.progress_layout);
            ImageView statusIcon = convertView.findViewById(R.id.status_icon);
            ImageView deleteBtn = convertView.findViewById(R.id.delete_btn);
            View iconContainer = convertView.findViewById(R.id.icon_container);
            
            // ---------------------------------------------------------
            // [✅ جديد] معالجة عرض المجلدات مقابل الفيديوهات
            // ---------------------------------------------------------
            
            if (item.isFolder) {
                // --- تصميم المجلد ---
                titleView.setText(item.folderName);
                qualityView.setVisibility(View.GONE);
                folderPathView.setVisibility(View.GONE);
                detailsLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE); // إخفاء زر الحذف للمجلدات مؤقتاً

                // أيقونة المجلد
                statusIcon.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(android.R.drawable.ic_menu_view); // أيقونة تشبه المجلد
                
                // عند الضغط على المجلد: ندخل للداخل
                iconContainer.setOnClickListener(v -> openFolder(item.folderName));
                convertView.setOnClickListener(v -> openFolder(item.folderName));
                
                return convertView;
            }

            // --- تصميم الفيديو (كما كان سابقاً) ---
            
            deleteBtn.setVisibility(View.VISIBLE);
            
            // استخراج الجودة من العنوان
            String displayTitle = item.title;
            String displayQuality = "SD";
            if (item.title != null && item.title.contains("(") && item.title.endsWith(")")) {
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
            
            // إخفاء مسار المجلد لأنه معروف من العنوان العلوي
            folderPathView.setVisibility(View.GONE); 

            // باقي منطق الفيديو (تشغيل، تحميل، حذف)
            TextView durationView = convertView.findViewById(R.id.video_duration);
            TextView sizeView = convertView.findViewById(R.id.video_size);
            ProgressBar progressBar = convertView.findViewById(R.id.download_progress);
            TextView statusText = convertView.findViewById(R.id.status_text);
            ProgressBar loadingSpinner = convertView.findViewById(R.id.loading_spinner);

            if (item.status.equals("Completed")) {
                statusIcon.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(android.R.drawable.ic_media_play);
                loadingSpinner.setVisibility(View.GONE);
                detailsLayout.setVisibility(View.VISIBLE);
                progressLayout.setVisibility(View.GONE);
                sizeView.setText(getFileSizeString(item));
                durationView.setText(formatDuration(item.duration));

                iconContainer.setOnClickListener(v -> decryptAndPlayVideo(item));
                convertView.setOnClickListener(v -> decryptAndPlayVideo(item));

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

    // دالة فتح المجلد
    private void openFolder(String folderName) {
        if (currentSubject == null) {
            currentSubject = folderName; // دخلنا المادة
        } else if (currentChapter == null) {
            currentChapter = folderName; // دخلنا الشابتر
        }
        refreshDisplayList();
    }

    // باقي الدوال المساعدة (نفس القديمة)
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
        loadingOverlay.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                File encryptedFile = getTargetFile(item);
                if (!encryptedFile.exists()) throw new Exception("الملف غير موجود");

                decryptedFile = new File(getCacheDir(), "decrypted_video.mp4");
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

                playDecryptedFile(decryptedFile, item.title, item.duration);

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
