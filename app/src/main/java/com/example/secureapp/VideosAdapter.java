package com.example.secureapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // ✅ استيراد
import android.graphics.Color; // ✅ استيراد
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.Gson;

import java.io.File;
import java.util.Collections;
import java.util.HashSet; // ✅ استيراد
import java.util.List;
import java.util.Set; // ✅ استيراد
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.ViewHolder> {
    private static final String TAG = "VideosAdapter";
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

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoEntity video = videos.get(position);
        holder.title.setText(video.title);

        // -------------------------------------------------------------
        // ✅ [جديد] التحقق هل الفيديو محمل مسبقاً أم لا؟
        // -------------------------------------------------------------
        if (isVideoDownloaded(video.youtubeVideoId)) {
            // الحالة 1: الفيديو محمل
            holder.btnDownload.setText("تم التحميل ✅");
            // تغيير لون الزر للأخضر لتمييزه
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))); 
            
            // عند الضغط: فتح مكتبة التحميلات
            holder.btnDownload.setOnClickListener(v -> {
                Intent intent = new Intent(context, DownloadsActivity.class);
                context.startActivity(intent);
            });

        } else {
            // الحالة 2: الفيديو غير محمل (الوضع الطبيعي)
            holder.btnDownload.setText("⬇ تحميل أوفلاين");
            // اللون الرمادي الأصلي (أو الأزرق الغامق حسب تصميمك)
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4B5563"))); 
            
            // عند الضغط: بدء التحميل
            holder.btnDownload.setOnClickListener(v -> fetchUrlAndShowQualities(video, true));
        }
        // -------------------------------------------------------------

        // زر التشغيل (يعمل كالمعتاد: يشغل الملف المحلي إذا وجد، أو أونلاين)
        holder.btnPlay.setOnClickListener(v -> {
            File subjectDir = new File(context.getFilesDir(), subjectName != null ? subjectName : "Uncategorized");
            File chapterDir = new File(subjectDir, chapterName.replaceAll("[^a-zA-Z0-9_-]", "_"));
            File file = new File(chapterDir, video.title.replaceAll("[^a-zA-Z0-9_-]", "_") + ".enc");
            
            if (file.exists()) {
                openPlayer(file.getAbsolutePath(), null); 
            } else {
                fetchUrlAndShowQualities(video, false);
            }
        });
    }

    // ✅ دالة مساعدة للتحقق من وجود الفيديو في سجل التحميلات المكتملة
    private boolean isVideoDownloaded(String youtubeId) {
        if (youtubeId == null) return false;
        
        SharedPreferences prefs = context.getSharedPreferences(DownloadWorker.DOWNLOADS_PREFS, Context.MODE_PRIVATE);
        Set<String> completed = prefs.getStringSet(DownloadWorker.KEY_DOWNLOADS_SET, new HashSet<>());
        
        // البحث عن الـ ID في السجلات (كل سجل يبدأ بـ ID|)
        for (String entry : completed) {
            if (entry.startsWith(youtubeId + "|")) {
                return true;
            }
        }
        return false;
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
                    String videoDuration = (data.duration != null) ? data.duration : "0";

                    if (data.availableQualities != null && !data.availableQualities.isEmpty()) {
                        Collections.sort(data.availableQualities, (a, b) -> {
                            int qA = parseQuality(a.quality);
                            int qB = parseQuality(b.quality);
                            return Integer.compare(qB, qA);
                        });

                        if (isDownloadMode) {
                            showQualitySelectionDialog(data.availableQualities, video, videoDuration);
                        } else {
                            openPlayerWithQualities(data.availableQualities);
                        }
                    } 
                    else if (data.streamUrl != null && !data.streamUrl.isEmpty()) {
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
                Log.e(TAG, "Network Error: " + t.getMessage());
                Toast.makeText(context, "خطأ في الشبكة: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private int parseQuality(String qualityStr) {
        if (qualityStr == null) return 0;
        try {
            return Integer.parseInt(qualityStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void showQualitySelectionDialog(List<VideoApiResponse.QualityOption> qualities, VideoEntity video, String duration) {
        String[] qualityNames = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) {
            String q = qualities.get(i).quality;
            qualityNames[i] = q.contains("p") ? q : q + "p";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("اختر جودة التحميل ⬇️");
        builder.setItems(qualityNames, (dialog, which) -> {
            VideoApiResponse.QualityOption selected = qualities.get(which);
            launchDownloadWorker(video, selected.url, qualityNames[which], duration);
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    private void openPlayerWithQualities(List<VideoApiResponse.QualityOption> qualities) {
        String defaultUrl = qualities.get(0).url;
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

    private void launchDownloadWorker(VideoEntity video, String streamUrl, String qualityLabel, String duration) {
        String titleWithQuality = video.title + " (" + qualityLabel + ")";
        Log.d(TAG, "Starting download: " + titleWithQuality);

        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(context, "فشل: الرابط فارغ!", Toast.LENGTH_SHORT).show();
            return;
        }

        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, video.youtubeVideoId)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, titleWithQuality)
                .putString("subjectName", subjectName != null ? subjectName : "Uncategorized")
                .putString("chapterName", chapterName != null ? chapterName : "General")
                .putString("specificUrl", streamUrl)
                .putString("duration", duration)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        try {
            WorkManager.getInstance(context).enqueue(request);
            Toast.makeText(context, "بدأ تحميل: " + titleWithQuality + "\n(تابع الإشعارات)", Toast.LENGTH_SHORT).show();
            
            // ✅ تحديث الزر فوراً بعد بدء التحميل (اختياري: ليظهر للمستخدم أن العملية بدأت)
            // ملاحظة: التحويل الكامل لـ "تم التحميل" سيحدث عند اكتمال العملية وإعادة فتح القائمة
            
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
        Button btnDownload;
        Button btnPlay;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.video_title);
            btnDownload = itemView.findViewById(R.id.btn_download_action);
            btnPlay = itemView.findViewById(R.id.btn_play_action);
        }
    }
}
