package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.example.secureapp.database.VideoEntity;
import java.io.File;
import java.util.List;

public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.ViewHolder> {
    private List<VideoEntity> videos;
    private Context context;
    private String chapterName;

    public VideosAdapter(Context context, List<VideoEntity> videos, String chapterName) {
        this.context = context;
        this.videos = videos;
        this.chapterName = chapterName;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoEntity video = videos.get(position);
        holder.title.setText(video.title);

        // --- زر التحميل ---
        holder.btnDownload.setOnClickListener(v -> {
            startDownload(video);
        });

        // --- الضغط للمشاهدة ---
        holder.itemView.setOnClickListener(v -> {
            // التحقق: هل الملف موجود أوفلاين؟
            // (بناء المسار كما تم في DownloadWorker لديك)
            // سنبحث في المسار العام أو داخل المجلدات حسب هيكل التحميل لديك
            // للتبسيط سنبحث في المسار الافتراضي
            
            // محاولة إيجاد الملف
            File subjectDir = new File(context.getFilesDir(), "Uncategorized"); // يمكن تحسينه ليكون اسم المادة
            File chapterDir = new File(subjectDir, chapterName.replaceAll("[^a-zA-Z0-9_-]", "_"));
            File file = new File(chapterDir, video.title.replaceAll("[^a-zA-Z0-9_-]", "_") + ".enc");
            
            // إذا لم نجده، نجرب البحث في الـ Root (لأن DownloadWorker قد يختلف)
            if (!file.exists()) {
                 // حاول مساراً آخر أو ابحث بالـ ID
                 // للتسهيل: سنفترض أننا سنشغل الأوفلاين فقط إذا وجدناه، وإلا تنبيه
            }

            if (file.exists()) {
                // تشغيل أوفلاين
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("VIDEO_PATH", file.getAbsolutePath());
                intent.putExtra("WATERMARK_TEXT", getUserId());
                context.startActivity(intent);
            } else {
                // تشغيل أونلاين (يحتاج لرابط مباشر، لكن الـ API يعطي ID فقط)
                // لذلك، الأفضل هو تحميله أولاً أو استخدام مشغل يوتيوب
                Toast.makeText(context, "يجب تحميل الفيديو أولاً للمشاهدة", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void startDownload(VideoEntity video) {
        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, video.youtubeVideoId)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, video.title)
                .putString("chapterName", chapterName) // لإرسال اسم الفصل للوركر
                .putString("specificUrl", "https://youtu.be/" + video.youtubeVideoId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        WorkManager.getInstance(context).enqueue(request);
        Toast.makeText(context, "جارِ إضافة الفيديو للتحميل...", Toast.LENGTH_SHORT).show();
    }

    private String getUserId() {
        return context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE)
                .getString("TelegramUserId", "User");
    }

    @Override
    public int getItemCount() { return videos != null ? videos.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageButton btnDownload;
        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.video_title);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}
