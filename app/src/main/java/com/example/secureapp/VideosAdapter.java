package com.example.secureapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VideoEntity video = videos.get(position);
        holder.title.setText(video.title);

        File downloadedFile = getDownloadedVideoFile(video.youtubeVideoId);
        boolean isDownloaded = (downloadedFile != null && downloadedFile.exists());

        if (isDownloaded) {
            holder.btnDownload.setText("تم التحميل (تشغيل) ▶");
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            // ✅ عند الضغط، نمرر الملف المشفر مباشرة
            holder.btnDownload.setOnClickListener(v -> decryptAndPlayVideo(downloadedFile));
        } else {
            holder.btnDownload.setText("⬇ تحميل أوفلاين");
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4B5563")));
            holder.btnDownload.setOnClickListener(v -> fetchUrlAndShowQualities(video, true));
        }

        holder.btnPlay.setOnClickListener(v -> {
            fetchUrlAndShowQualities(video, false); 
        });
    }

    private File getDownloadedVideoFile(String youtubeId) {
        if (youtubeId == null) return null;

        SharedPreferences prefs = context.getSharedPreferences("DownloadPrefs", Context.MODE_PRIVATE);
        Set<String> completed = prefs.getStringSet("CompletedDownloads", new HashSet<>());

        for (String entry : completed) {
            String[] parts = entry.split("\\|");
            if (parts.length >= 6 && parts[0].equals(youtubeId)) {
                String savedSubject = parts[3];
                String savedChapter = parts[4];
                String savedFilename = parts[5];

                File subjectDir = new File(context.getFilesDir(), savedSubject);
                File chapterDir = new File(subjectDir, savedChapter);
                File file = new File(chapterDir, savedFilename + ".enc");

                if (file.exists()) {
                    return file;
                }
            }
        }
        return null;
    }

    // ✅ الدالة الجديدة: آمنة تماماً وخفيفة
    private void decryptAndPlayVideo(File encryptedFile) {
        try {
            // نرسل المسار المشفر فقط، والمشغل يتولى الباقي
            openPlayer(encryptedFile.getAbsolutePath(), null);
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(new Exception("Adapter Navigation Error", e));
            Toast.makeText(context, "حدث خطأ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchUrlAndShowQualities(VideoEntity video, boolean isDownloadMode) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage("جاري جلب البيانات...");
        dialog.setCancelable(false);
        dialog.show();

        String userId = getUserId();
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        // ✅ إضافة المتغير الناقص (App Secret)
        String appSecret = MainActivity.APP_SECRET;

        // ✅ التعديل: تمرير 4 متغيرات كما يطلب ApiService
        RetrofitClient.getApi().getVideoUrl(video.id, userId, deviceId, appSecret).enqueue(new Callback<VideoApiResponse>() {
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
                    Toast.makeText(context, "فشل الاتصال بالخادم (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VideoApiResponse> call, Throwable t) {
                dialog.dismiss();
                Log.e(TAG, "Network Error: " + t.getMessage());
                Toast.makeText(context, "تأكد من اتصالك بالإنترنت وحاول مرة أخرى.", Toast.LENGTH_LONG).show();
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
        // ✅ التعديل الجديد: البحث عن جودة متوسطة (480p أو 360p) بدلاً من تشغيل الأعلى فوراً
        
        VideoApiResponse.QualityOption selectedOption = null;

        // 1. المحاولة الأولى: البحث عن 480p (توازن ممتاز)
        for (VideoApiResponse.QualityOption option : qualities) {
            if (parseQuality(option.quality) == 480) {
                selectedOption = option;
                break;
            }
        }

        // 2. المحاولة الثانية: إذا لم نجد 480، نبحث عن 360p (توفير أكثر)
        if (selectedOption == null) {
            for (VideoApiResponse.QualityOption option : qualities) {
                if (parseQuality(option.quality) == 360) {
                    selectedOption = option;
                    break;
                }
            }
        }

        // 3. الخيار الأخير: إذا لم نجد أياً منهما، نختار أقل جودة متاحة لتوفير النت
        // (القائمة مرتبة تنازلياً، لذا آخر عنصر هو الأقل جودة)
        if (selectedOption == null && !qualities.isEmpty()) {
            selectedOption = qualities.get(qualities.size() - 1);
        }

        if (selectedOption != null) {
            String defaultUrl = selectedOption.url;
            String qualitiesJson = new Gson().toJson(qualities);
            
            // يمكنك إلغاء تعليق السطر التالي للتأكد أثناء التجربة
            // Toast.makeText(context, "بدء التشغيل بجودة: " + selectedOption.quality, Toast.LENGTH_SHORT).show();
            
            openPlayer(defaultUrl, qualitiesJson);
        }
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
                .putString("type", "video")
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        try {
            WorkManager.getInstance(context).enqueue(request);
            Toast.makeText(context, "بدأ تحميل: " + titleWithQuality + "\n(تابع الإشعارات)", Toast.LENGTH_SHORT).show();
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
