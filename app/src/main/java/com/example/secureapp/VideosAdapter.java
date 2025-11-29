package com.example.secureapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log; // ✅ استيراد اللوج
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
import com.google.firebase.crashlytics.FirebaseCrashlytics; // ✅ استيراد فايربيس
import com.google.gson.Gson;

import java.io.File;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.ViewHolder> {
    private static final String TAG = "VideosAdapter"; // ✅ تاج للوج
    private List<VideoEntity> videos;
    private Context context;
    private String subjectName; // ✅ اسم المادة لتصنيف التحميل
    private String chapterName;

    // ✅ تم تحديث الكونستركتور لاستقبال subjectName
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

        // عند الضغط على زر التحميل
        holder.btnDownload.setOnClickListener(v -> fetchUrlAndShowQualities(video, true));

        // عند الضغط على الفيديو (للمشاهدة)
        holder.itemView.setOnClickListener(v -> {
            // التحقق من الملف محلياً أولاً
            File subjectDir = new File(context.getFilesDir(), subjectName != null ? subjectName : "Uncategorized");
            File chapterDir = new File(subjectDir, chapterName.replaceAll("[^a-zA-Z0-9_-]", "_"));
            File file = new File(chapterDir, video.title.replaceAll("[^a-zA-Z0-9_-]", "_") + ".enc");
            
            if (file.exists()) {
                // تشغيل ملف محلي (لا يحتاج جودات أو إنترنت)
                openPlayer(file.getAbsolutePath(), null); 
            } else {
                // جلب الروابط للمشاهدة أونلاين
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
                    
                    // ✅ استخراج المدة من السيرفر (مع قيمة افتراضية "0" إذا كانت فارغة)
                    String videoDuration = (data.duration != null) ? data.duration : "0";

                    if (data.availableQualities != null && !data.availableQualities.isEmpty()) {
                        if (isDownloadMode) {
                            // للتحميل: نعرض نافذة ليختار جودة واحدة (ونمرر المدة)
                            showQualitySelectionDialog(data.availableQualities, video, videoDuration);
                        } else {
                            // للمشاهدة: نرسل القائمة كاملة للمشغل
                            openPlayerWithQualities(data.availableQualities);
                        }
                    } 
                    else if (data.streamUrl != null && !data.streamUrl.isEmpty()) {
                        // رابط وحيد
                        if (isDownloadMode) launchDownloadWorker(video, data.streamUrl, "Default", videoDuration);
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

    // ✅ دالة عرض نافذة اختيار الجودة للتحميل (تستقبل المدة الآن)
    private void showQualitySelectionDialog(List<VideoApiResponse.QualityOption> qualities, VideoEntity video, String duration) {
        // ترتيب الجودات (الأعلى أولاً)
        Collections.sort(qualities, (a, b) -> Integer.compare(b.quality, a.quality));
        
        String[] qualityNames = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) qualityNames[i] = qualities.get(i).quality + "p";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("اختر جودة التحميل ⬇️");
        builder.setItems(qualityNames, (dialog, which) -> {
            VideoApiResponse.QualityOption selected = qualities.get(which);
            // ✅ تمرير المدة والرابط والجودة للتحميل
            launchDownloadWorker(video, selected.url, selected.quality + "p", duration);
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    private void openPlayerWithQualities(List<VideoApiResponse.QualityOption> qualities) {
        Collections.sort(qualities, (a, b) -> Integer.compare(b.quality, a.quality));
        String defaultUrl = qualities.get(0).url; // تشغيل أعلى جودة افتراضياً
        String qualitiesJson = new Gson().toJson(qualities);
        openPlayer(defaultUrl, qualitiesJson);
    }

    private void openPlayer(String pathOrUrl, String qualitiesJson) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", pathOrUrl);
        intent.putExtra("WATERMARK_TEXT", getUserId());
        if (qualitiesJson != null) {
            intent.putExtra("QUALITIES_JSON", qualitiesJson);
        }
        context.startActivity(intent);
    }

    // ✅ دالة التحميل النهائية (تستقبل المدة + اسم المادة من الكلاس)
    private void launchDownloadWorker(VideoEntity video, String streamUrl, String qualityLabel, String duration) {
        String titleWithQuality = video.title + " (" + qualityLabel + ")";

        // ✅ تسجيل الحدث
        Log.d(TAG, "Attempting to start download: " + titleWithQuality + " | URL: " + streamUrl + " | Duration: " + duration);
        FirebaseCrashlytics.getInstance().log("Adapter: Enqueue Download - " + titleWithQuality);

        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(context, "فشل: الرابط فارغ!", Toast.LENGTH_SHORT).show();
            FirebaseCrashlytics.getInstance().recordException(new Exception("Adapter: Empty URL for download: " + titleWithQuality));
            return;
        }

        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, video.youtubeVideoId)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, titleWithQuality)
                // ✅ استخدام اسم المادة والشابتر الصحيحين لإنشاء المجلدات
                .putString("subjectName", subjectName != null ? subjectName : "Uncategorized")
                .putString("chapterName", chapterName != null ? chapterName : "General")
                .putString("specificUrl", streamUrl)
                .putString("duration", duration) // ✅ تمرير المدة
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        try {
            WorkManager.getInstance(context).enqueue(request);
            Toast.makeText(context, "بدأ تحميل: " + titleWithQuality + "\n(تابع الإشعارات)", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Download enqueued successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue download work", e);
            FirebaseCrashlytics.getInstance().recordException(e);
            Toast.makeText(context, "حدث خطأ في بدء التحميل", Toast.LENGTH_SHORT).show();
        }
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
