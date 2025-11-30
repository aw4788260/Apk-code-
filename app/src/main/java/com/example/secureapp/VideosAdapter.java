package com.example.secureapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.VideoApiResponse;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

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

        // 1. محاولة العثور على الملف المحمل الحقيقي
        File downloadedFile = getDownloadedVideoFile(video.youtubeVideoId);
        boolean isDownloaded = (downloadedFile != null && downloadedFile.exists());

        // -------------------------------------------------------------
        // ✅ حالة: الفيديو محمل وموجود
        // -------------------------------------------------------------
        if (isDownloaded) {
            holder.btnDownload.setText("تم التحميل (تشغيل) ▶");
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // أخضر
            
            // ✅ [التعديل هنا] هذا الزر فقط هو الذي يشغل الملف المحلي (من المكتبة)
            holder.btnDownload.setOnClickListener(v -> decryptAndPlayVideo(downloadedFile));
        } 
        // -------------------------------------------------------------
        // ✅ حالة: الفيديو غير محمل
        // -------------------------------------------------------------
        else {
            holder.btnDownload.setText("⬇ تحميل أوفلاين");
            holder.btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4B5563"))); // رمادي
            holder.btnDownload.setOnClickListener(v -> fetchUrlAndShowQualities(video, true));
        }

        // -------------------------------------------------------------
        // ✅ زر التشغيل الرئيسي (تم تعديله ليكون للأونلاين فقط)
        // -------------------------------------------------------------
        holder.btnPlay.setOnClickListener(v -> {
            // ⚠️ تم إزالة شرط (if isDownloaded)
            // الآن سيعمل دائماً كأونلاين (يظهر خيارات الجودة) حتى لو الملف محمل
            fetchUrlAndShowQualities(video, false); 
        });
    }

    /**
     * ✅ دالة ذكية للبحث عن مسار الملف الحقيقي من سجلات التحميل
     */
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

    private void decryptAndPlayVideo(File encryptedFile) {
        ProgressDialog pd = new ProgressDialog(context);
        pd.setMessage("جاري فتح الفيديو...");
        pd.setCancelable(false);
        pd.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            File decryptedFile = null;
            try {
                decryptedFile = new File(context.getCacheDir(), "decrypted_video.mp4");
                if(decryptedFile.exists()) decryptedFile.delete();

                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                        encryptedFile, context, masterKeyAlias,
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

                File finalDecryptedFile = decryptedFile;
                new Handler(Looper.getMainLooper()).post(() -> {
                    pd.dismiss();
                    openPlayer(finalDecryptedFile.getAbsolutePath(), null);
                });

            } catch (Exception e) {
                FirebaseCrashlytics.getInstance().recordException(new Exception("Decryption Failed in Adapter", e));
                File finalDecryptedFile = decryptedFile;
                new Handler(Looper.getMainLooper()).post(() -> {
                    pd.dismiss();
                    Toast.makeText(context, "فشل فتح الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if(finalDecryptedFile != null && finalDecryptedFile.exists()) finalDecryptedFile.delete();
                });
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
