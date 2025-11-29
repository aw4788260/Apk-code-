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
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.VideoApiResponse;
import com.google.gson.Gson; // ✅ نحتاج Gson لتحويل القائمة لنص

import java.io.File;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.ViewHolder> {
    // ... (نفس المتغيرات والكونستركتور السابق)
    private List<VideoEntity> videos;
    private Context context;
    private String subjectName;
    private String chapterName;

    public VideosAdapter(Context context, List<VideoEntity> videos, String subjectName, String chapterName) {
        this.context = context;
        this.videos = videos;
        this.subjectName = subjectName;
        this.chapterName = chapterName;
    }

    // ... (onCreateViewHolder و onBindViewHolder كما هي)
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoEntity video = videos.get(position);
        holder.title.setText(video.title);

        holder.btnDownload.setOnClickListener(v -> fetchUrlAndShowQualities(video, true));

        holder.itemView.setOnClickListener(v -> {
            File subjectDir = new File(context.getFilesDir(), subjectName != null ? subjectName : "Uncategorized");
            File chapterDir = new File(subjectDir, chapterName.replaceAll("[^a-zA-Z0-9_-]", "_"));
            File file = new File(chapterDir, video.title.replaceAll("[^a-zA-Z0-9_-]", "_") + ".enc");
            
            if (file.exists()) {
                openPlayer(file.getAbsolutePath(), null); // لا توجد قائمة جودات للملف المحلي
            } else {
                fetchUrlAndShowQualities(video, false);
            }
        });
    }

    private void fetchUrlAndShowQualities(VideoEntity video, boolean isDownloadMode) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage("جاري جلب البيانات...");
        dialog.setCancelable(false);
        dialog.show();

        String userId = getUserId();
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        RetrofitClient.getApi().getVideoUrl(video.id, userId, deviceId).enqueue(new Callback<VideoApiResponse>() {
            @Override
            public void onResponse(Call<VideoApiResponse> call, Response<VideoApiResponse> response) {
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    VideoApiResponse data = response.body();
                    
                    if (data.availableQualities != null && !data.availableQualities.isEmpty()) {
                        if (isDownloadMode) {
                            // في حالة التحميل: نظهر القائمة ليختار منها
                            showQualitySelectionDialog(data.availableQualities, video);
                        } else {
                            // ✅ في حالة المشاهدة: نرسل القائمة كاملة للمشغل
                            openPlayerWithQualities(data.availableQualities);
                        }
                    } 
                    else if (data.streamUrl != null && !data.streamUrl.isEmpty()) {
                        if (isDownloadMode) launchDownloadWorker(video, data.streamUrl, "Default");
                        else openPlayer(data.streamUrl, null);
                    } else {
                        Toast.makeText(context, "لا توجد روابط متاحة.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(context, "فشل الاتصال: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VideoApiResponse> call, Throwable t) {
                dialog.dismiss();
                Toast.makeText(context, "خطأ في الشبكة", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // دالة عرض النافذة (للتحميل فقط الآن)
    private void showQualitySelectionDialog(List<VideoApiResponse.QualityOption> qualities, VideoEntity video) {
        Collections.sort(qualities, (a, b) -> Integer.compare(b.quality, a.quality));
        String[] qualityNames = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) qualityNames[i] = qualities.get(i).quality + "p";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("اختر جودة التحميل ⬇️");
        builder.setItems(qualityNames, (dialog, which) -> {
            VideoApiResponse.QualityOption selected = qualities.get(which);
            launchDownloadWorker(video, selected.url, selected.quality + "p");
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    // ✅ دالة جديدة لفتح المشغل مع قائمة الجودات
    private void openPlayerWithQualities(List<VideoApiResponse.QualityOption> qualities) {
        // ترتيب الجودات لتكون الأفضل أولاً (أو حسب رغبتك)
        Collections.sort(qualities, (a, b) -> Integer.compare(b.quality, a.quality));
        
        // اختيار أفضل جودة كافتراضي
        String defaultUrl = qualities.get(0).url;
        
        // تحويل القائمة لنص JSON لإرسالها
        String qualitiesJson = new Gson().toJson(qualities);

        openPlayer(defaultUrl, qualitiesJson);
    }

    // تم تعديل الدالة لتستقبل JSON الجودات
    private void openPlayer(String pathOrUrl, String qualitiesJson) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", pathOrUrl);
        intent.putExtra("WATERMARK_TEXT", getUserId());
        if (qualitiesJson != null) {
            intent.putExtra("QUALITIES_JSON", qualitiesJson);
        }
        context.startActivity(intent);
    }

    // ... (launchDownloadWorker, getUserId, ViewHolder كما هي)
    private void launchDownloadWorker(VideoEntity video, String streamUrl, String qualityLabel) {
        String titleWithQuality = video.title + " (" + qualityLabel + ")";
        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, video.youtubeVideoId)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, titleWithQuality)
                .putString("subjectName", subjectName)
                .putString("chapterName", chapterName)
                .putString("specificUrl", streamUrl)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData).addTag("download_work_tag").build();
        WorkManager.getInstance(context).enqueue(request);
        Toast.makeText(context, "بدأ تحميل: " + titleWithQuality, Toast.LENGTH_LONG).show();
    }

    private String getUserId() {
        return context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE).getString("TelegramUserId", "User");
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
