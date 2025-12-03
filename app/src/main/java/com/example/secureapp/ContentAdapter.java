package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.io.File;
import java.util.List;

public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {
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
        // نستخدم نفس تصميم الفيديو ونغير الأيقونات
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
        holder.btnDownload.setText("⬇ تحميل");
        // ... (يمكنك نقل منطق الفيديوهات من VideosAdapter هنا)
    }

    private void setupPdfItem(ViewHolder holder, ContentItem item) {
        holder.btnPlay.setText("📄 فتح الملف");
        holder.btnPlay.setBackgroundTintList(context.getColorStateList(R.color.teal_200));
        
        File pdfFile = getDownloadedPdfFile(String.valueOf(item.id));
        boolean isDownloaded = (pdfFile != null && pdfFile.exists());

        if (isDownloaded) {
            holder.btnDownload.setText("محمل ✅");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4CAF50"));
            holder.btnDownload.setOnClickListener(v -> openLocalPdf(pdfFile, item.title, String.valueOf(item.id)));
        } else {
            holder.btnDownload.setText("⬇ PDF");
            holder.btnDownload.setBackgroundColor(Color.parseColor("#4B5563"));
            holder.btnDownload.setOnClickListener(v -> startPdfDownload(item));
        }

        holder.btnPlay.setOnClickListener(v -> openOnlinePdf(item));
    }

    private void startPdfDownload(ContentItem item) {
        Data inputData = new Data.Builder()
                .putString("type", "pdf")
                .putString("pdfId", String.valueOf(item.id))
                .putString("videoTitle", item.title)
                .putString("subjectName", subjectName)
                .putString("chapterName", chapterName)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag("download_work_tag")
                .build();

        WorkManager.getInstance(context).enqueue(request);
        Toast.makeText(context, "جاري التحميل...", Toast.LENGTH_SHORT).show();
    }

    private void openOnlinePdf(ContentItem item) {
        SharedPreferences prefs = context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "");
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        String url = "https://courses.aw478260.dpdns.org/api/secure/get-pdf?pdfId=" + item.id + "&userId=" + userId + "&deviceId=" + deviceId;
        
        Intent intent = new Intent(context, PdfViewerActivity.class);
        intent.putExtra("PDF_URL", url);
        intent.putExtra("PDF_TITLE", item.title);
        intent.putExtra("PDF_ID", String.valueOf(item.id));
        context.startActivity(intent);
    }
    
    private void openLocalPdf(File file, String title, String id) {
        Intent intent = new Intent(context, PdfViewerActivity.class);
        intent.putExtra("PDF_TITLE", title);
        intent.putExtra("PDF_ID", id);
        context.startActivity(intent);
    }

    private File getDownloadedPdfFile(String pdfId) {
        File dir = new File(context.getFilesDir(), "secure_pdfs");
        File file = new File(dir, "doc_" + pdfId + ".enc");
        return file.exists() ? file : null;
    }

    @Override public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title; Button btnDownload, btnPlay;
        ViewHolder(View v) { super(v); title = v.findViewById(R.id.video_title); btnDownload = v.findViewById(R.id.btn_download_action); btnPlay = v.findViewById(R.id.btn_play_action); }
    }
}
