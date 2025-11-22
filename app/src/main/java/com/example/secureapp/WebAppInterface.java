package com.example.secureapp;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class WebAppInterface {

    private Context mContext;
    private static final String TAG = "WebAppInterface";

    WebAppInterface(Context c) { mContext = c; }

    /**
     * ✅ دالة جديدة: تستقبل الجودات كـ JSON String مباشرة من السيرفر
     * @param youtubeId معرف الفيديو
     * @param videoTitle عنوان الفيديو
     * @param durationStr مدة الفيديو
     * @param qualitiesJson نص JSON يحتوي على الجودات والروابط ([{quality: 720, url: "..."}])
     */
    @JavascriptInterface
    public void downloadVideoWithQualities(String youtubeId, String videoTitle, String durationStr, String qualitiesJson) {
        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> {
            try {
                // 1. تحليل البيانات القادمة من السيرفر مباشرة
                JSONArray jsonArray = new JSONArray(qualitiesJson);
                List<String> qualityNames = new ArrayList<>();
                List<String> qualityUrls = new ArrayList<>();

                if (jsonArray.length() == 0) {
                    Toast.makeText(mContext, "لا توجد جودات متاحة للتحميل.", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject q = jsonArray.getJSONObject(i);
                    // قراءة الجودة والرابط
                    String qLabel = q.optString("quality") + "p"; // مثلاً: 720p
                    String qUrl = q.getString("url");
                    
                    qualityNames.add(qLabel);
                    qualityUrls.add(qUrl);
                }

                // 2. عرض القائمة فوراً (بدون انتظار أو تحميل)
                showSelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls, durationStr);

            } catch (Exception e) {
                Log.e(TAG, "Error parsing qualities JSON", e);
                Toast.makeText(mContext, "خطأ في بيانات التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // عرض القائمة (نفس الدالة السابقة لكن بدون تغيير)
    private void showSelectionDialog(String title, String youtubeId, List<String> names, List<String> urls, String duration) {
        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("اختر الجودة: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    String titleWithQuality = title + " (" + names.get(which) + ")";
                    String selectedUrl = urls.get(which);
                    
                    // تمرير الرابط المباشر للـ Worker
                    startDownloadWorker(youtubeId, titleWithQuality, selectedUrl, duration);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // بدء التحميل (كما هي)
    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl) // هذا الرابط مباشر الآن
                    .putString("duration", duration)
                    .build();

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("download_work_tag")
                    .build();

            WorkManager.getInstance(mContext).enqueue(request);
            
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "تمت الإضافة للقائمة", Toast.LENGTH_SHORT).show()
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Worker Error", e);
        }
    }
}
