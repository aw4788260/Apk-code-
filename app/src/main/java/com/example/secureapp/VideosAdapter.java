package com.example.secureapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
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

// استيراد الجداول والشبكة
import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.VideoApiResponse;

import java.io.File;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.ViewHolder> {
    private List<VideoEntity> videos;
    private Context context;
    private String subjectName; // يستخدم لتنظيم مجلدات التحميل
    private String chapterName;

    public VideosAdapter(Context context, List<VideoEntity> videos, String subjectName, String chapterName) {
        this.context = context;
        this.videos = videos;
        this.subjectName = subjectName;
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

        // --- زر التحميل (Download) ---
        holder.btnDownload.setOnClickListener(v -> {
            startDownload(video);
        });

        // --- الضغط للمشاهدة (Watch) ---
        holder.itemView.setOnClickListener(v -> {
            // 1. تحديد مسار الملف الأوفلاين المحتمل
            File subjectDir = new File(context.getFilesDir(), subjectName != null ? subjectName : "Uncategorized"); 
            File chapterDir = new File(subjectDir, chapterName.replaceAll("[^a-zA-Z0-9_-]", "_"));
            File file = new File(chapterDir, video.title.replaceAll("[^a-zA-Z0-9_-]", "_") + ".enc");
            
            // بحث احتياطي في الجذر (للملفات القديمة)
            File rootFile = new File(context.getFilesDir(), video.youtubeVideoId + ".enc");

            if (file.exists() || rootFile.exists()) {
                // ✅ الفيديو محمل: تشغيل أوفلاين فوراً
                openPlayer(file.exists() ? file.getAbsolutePath() : rootFile.getAbsolutePath());
            } else {
                // 🌐 الفيديو غير محمل: طلب الرابط المباشر من السيرفر
                fetchUrlAndPlay(video.id); // نرسل ID الفيديو الخاص بقاعدة البيانات (وليس يوتيوب)
            }
        });
    }

    // دالة الاتصال بالسيرفر لجلب الرابط
    private void fetchUrlAndPlay(int lessonId) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage("جاري جلب الرابط...");
        dialog.setCancelable(false);
        dialog.show();

        // تجهيز البيانات
        String userId = getUserId();
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        // الاتصال بالـ API
        RetrofitClient.getApi().getVideoUrl(lessonId, userId, deviceId).enqueue(new Callback<VideoApiResponse>() {
            @Override
            public void onResponse(Call<VideoApiResponse> call, Response<VideoApiResponse> response) {
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    String streamUrl = response.body().streamUrl;
                    
                    if (streamUrl != null && !streamUrl.isEmpty()) {
                        // 🚀 نجاح: تشغيل الرابط في المشغل الأصلي
                        openPlayer(streamUrl);
                    } else {
                        Toast.makeText(context, "لم يتم العثور على رابط مباشر", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "فشل الاتصال بالسيرفر", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VideoApiResponse> call, Throwable t) {
                dialog.dismiss();
                Toast.makeText(context, "خطأ في الاتصال بالإنترنت", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openPlayer(String pathOrUrl) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", pathOrUrl);
        intent.putExtra("WATERMARK_TEXT", getUserId());
        context.startActivity(intent);
    }

    private void startDownload(VideoEntity video) {
        // نرسل البيانات للـ Worker وهو يتولى الباقي
        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, video.youtubeVideoId)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, video.title)
                .putString("subjectName", subjectName != null ? subjectName : "Uncategorized")
                .putString("chapterName", chapterName)
                .putString("specificUrl", "https://youtu.be/" + video.youtubeVideoId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        WorkManager.getInstance(context).enqueue(request);
        Toast.makeText(context, "تمت إضافة الفيديو للتحميل ⬇️", Toast.LENGTH_SHORT).show();
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
