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

public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {
    private static final String TAG = "ContentAdapter";
    private List<ContentItem> items;
    private Context context;
    private String subjectName, chapterName;

    public ContentAdapter(Context context, List<ContentItem> items, String subjectName, String chapterName) {
        this.context = context;
        this.items = items;
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
        ContentItem item = items.get(position);
        holder.title.setText(item.title);

        if (item.type == ContentItem.TYPE_VIDEO) {
            setupVideoItem(holder, item);
        } else {
            setupPdfItem(holder, item);
        }
    }

    private void setupVideoItem(ViewHolder holder, ContentItem item) {
        holder.btnPlay.setText("▶ تشغيل");
        holder.btnPlay.setBackgroundTintList(context.getColorStateList(R.color.teal_200));

        File downloadedFile = getDownloadedVideoFile(item.extraData);
        boolean isDownloaded = (downloadedFile != null && downloadedFile.exists());

        if (isDownloaded) {
            holder.btnDownload.setText("محمل (تشغيل) ✅");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4CAF50"));
            holder.btnDownload.setOnClickListener(v -> decryptAndPlayVideo(downloadedFile));
        } else {
            holder.btnDownload.setText("⬇ تحميل أوفلاين");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4B5563"));
            holder.btnDownload.setOnClickListener(v -> fetchVideoUrlAndShowQualities(item, true));
        }

        holder.btnPlay.setOnClickListener(v -> fetchVideoUrlAndShowQualities(item, false));
    }

    // ---------------------------------------------------------
    // ✅ إعداد عناصر الـ PDF
    // ---------------------------------------------------------
    private void setupPdfItem(ViewHolder holder, ContentItem item) {
        holder.btnPlay.setText("📄 فتح الملف");
        holder.btnPlay.setBackgroundTintList(context.getColorStateList(R.color.teal_200));

        // التحقق من التحميل
        File pdfFile = getDownloadedPdfFile(String.valueOf(item.id));
        boolean isDownloaded = (pdfFile != null && pdfFile.exists());

        if (isDownloaded) {
            holder.btnDownload.setText("محمل (فتح) ✅");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4CAF50"));
            // فتح الملف المحلي
            holder.btnDownload.setOnClickListener(v -> openLocalPdf(pdfFile, item.title, String.valueOf(item.id)));
        } else {
            holder.btnDownload.setText("⬇ PDF");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4B5563"));
            holder.btnDownload.setOnClickListener(v -> startPdfDownload(item));
        }

        holder.btnPlay.setOnClickListener(v -> openOnlinePdf(item));
    }

    // فتح الملف المحلي (الأوفلاين)
    private void openLocalPdf(File file, String title, String id) {
        Intent intent = new Intent(context, PdfViewerActivity.class);
        intent.putExtra("PDF_TITLE", title);
        intent.putExtra("PDF_ID", id);
        intent.putExtra("LOCAL_PATH", file.getAbsolutePath());
        context.startActivity(intent);
    }

    private void startPdfDownload(ContentItem item) {
        Data inputData = new Data.Builder()
                .putString("type", "pdf")
                .putString("pdfId", String.valueOf(item.id))
                .putString("videoTitle", item.title)
                .putString("subjectName", subjectName)
                .putString("chapterName", chapterName)
                .build();
        startWorker(inputData);
    }

    private void openOnlinePdf(ContentItem item) {
        // ✅ رابط نظيف (بدون بيانات حساسة)
        // سيقوم PdfViewerActivity بإضافة الهيدرز اللازمة عند الطلب
        String url = "https://courses.aw478260.dpdns.org/api/secure/get-pdf?pdfId=" + item.id;
        
        Intent intent = new Intent(context, PdfViewerActivity.class);
        intent.putExtra("PDF_URL", url);
        intent.putExtra("PDF_TITLE", item.title);
        intent.putExtra("PDF_ID", String.valueOf(item.id));
        context.startActivity(intent);
    }
    
    private void fetchVideoUrlAndShowQualities(ContentItem item, boolean isDownloadMode) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage("جاري جلب البيانات...");
        dialog.setCancelable(false);
        dialog.show();

        String userId = getUserId();
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String appSecret = MainActivity.APP_SECRET; // ✅ الكود السري

        // ✅ إرسال الطلب مع الهيدرز (بما في ذلك Secret)
        RetrofitClient.getApi().getVideoUrl(item.id, userId, deviceId, appSecret).enqueue(new Callback<VideoApiResponse>() {
            @Override
            public void onResponse(Call<VideoApiResponse> call, Response<VideoApiResponse> response) {
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    VideoApiResponse data = response.body();
                    String duration = (data.duration != null) ? data.duration : "0";

                    if (data.availableQualities != null && !data.availableQualities.isEmpty()) {
                        Collections.sort(data.availableQualities, (a, b) -> {
                            int qA = parseQuality(a.quality);
                            int qB = parseQuality(b.quality);
                            return Integer.compare(qB, qA);
                        });
                        if (isDownloadMode) {
                            showQualitySelectionDialog(data.availableQualities, item, duration);
                        } else {
                            openPlayerWithQualities(data.availableQualities);
                        }
                    } 
                    else if (data.streamUrl != null && !data.streamUrl.isEmpty()) {
                        if (isDownloadMode) launchVideoDownload(item, data.streamUrl, "Default", duration);
                        else openPlayer(data.streamUrl, null);
                    } else {
                        Toast.makeText(context, "لا توجد روابط متاحة.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    // معالجة الأخطاء (مثل الرفض الأمني)
                    if (response.code() == 403) {
                        Toast.makeText(context, "⛔ تم رفض الوصول (جهاز غير مصرح)", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "فشل الاتصال بالخادم (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<VideoApiResponse> call, Throwable t) {
                dialog.dismiss();
                Toast.makeText(context, "خطأ في الشبكة: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchVideoDownload(ContentItem item, String url, String quality, String duration) {
        String titleWithQuality = item.title + " (" + quality + ")";
        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_YOUTUBE_ID, item.extraData)
                .putString(DownloadWorker.KEY_VIDEO_TITLE, titleWithQuality)
                .putString("subjectName", subjectName)
                .putString("chapterName", chapterName)
                .putString("specificUrl", url)
                .putString("duration", duration)
                .putString("type", "video")
                .build();
        startWorker(inputData);
    }

    private void startWorker(Data inputData) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();
        WorkManager.getInstance(context).enqueue(request);
        Toast.makeText(context, "تمت الإضافة لقائمة التحميلات", Toast.LENGTH_SHORT).show();
    }

    private void openPlayer(String url, String qualitiesJson) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("VIDEO_PATH", url);
        intent.putExtra("WATERMARK_TEXT", getUserId());
        if (qualitiesJson != null) intent.putExtra("QUALITIES_JSON", qualitiesJson);
        context.startActivity(intent);
    }
    
    private void openPlayerWithQualities(List<VideoApiResponse.QualityOption> qualities) {
        String defaultUrl = qualities.get(0).url;
        String qualitiesJson = new Gson().toJson(qualities);
        openPlayer(defaultUrl, qualitiesJson);
    }
    
    private void decryptAndPlayVideo(File file) {
        openPlayer(file.getAbsolutePath(), null);
    }

    private void showQualitySelectionDialog(List<VideoApiResponse.QualityOption> qualities, ContentItem item, String duration) {
        String[] names = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) names[i] = qualities.get(i).quality + "p";

        new AlertDialog.Builder(context)
            .setTitle("اختر الجودة")
            .setItems(names, (dialog, which) -> {
                launchVideoDownload(item, qualities.get(which).url, names[which], duration);
            })
            .show();
    }

    private int parseQuality(String q) {
        try { return Integer.parseInt(q.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private File getDownloadedVideoFile(String youtubeId) {
        if (youtubeId == null) return null;
        SharedPreferences prefs = context.getSharedPreferences("DownloadPrefs", Context.MODE_PRIVATE);
        Set<String> completed = prefs.getStringSet("CompletedDownloads", new HashSet<>());

        for (String entry : completed) {
            if (entry.startsWith(youtubeId + "|")) {
                String[] parts = entry.split("\\|");
                if (parts.length >= 6) {
                    File dir = new File(context.getFilesDir(), parts[3] + "/" + parts[4]);
                    File file = new File(dir, parts[5] + ".enc");
                    if (file.exists()) return file;
                }
            }
        }
        return null;
    }

    private File getDownloadedPdfFile(String pdfId) {
        File dir = new File(context.getFilesDir(), "secure_pdfs");
        File file = new File(dir, "doc_" + pdfId + ".enc");
        return file.exists() ? file : null;
    }

    private String getUserId() {
        return context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE).getString("TelegramUserId", "User");
    }

    @Override public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title; Button btnDownload, btnPlay;
        ViewHolder(View v) { super(v); title = v.findViewById(R.id.video_title); btnDownload = v.findViewById(R.id.btn_download_action); btnPlay = v.findViewById(R.id.btn_play_action); }
    }
}
