package com.example.secureapp;

import android.content.Context;
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

    WebAppInterface(Context c) { mContext = c; }

    /**
     * ✅ دالة الجافاسكريبت الجديدة: تستقبل 6 متغيرات
     * @param youtubeId معرف الفيديو
     * @param videoTitle عنوان الفيديو
     * @param durationStr مدة الفيديو
     * @param qualitiesJson قائمة الجودات (JSON)
     * @param subjectName اسم المادة (للمجلد الأول)
     * @param chapterName اسم الشابتر (للمجلد الثاني)
     */
    @JavascriptInterface
    public void downloadVideoWithQualities(String youtubeId, String videoTitle, String durationStr, String qualitiesJson, String subjectName, String chapterName) {
        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> {
            try {
                JSONArray jsonArray = new JSONArray(qualitiesJson);
                List<String> qualityNames = new ArrayList<>();
                List<String> qualityUrls = new ArrayList<>();

                if (jsonArray.length() == 0) {
                    Toast.makeText(mContext, "لا توجد جودات متاحة.", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject q = jsonArray.getJSONObject(i);
                    qualityNames.add(q.optString("quality") + "p");
                    qualityUrls.add(q.getString("url"));
                }

                // تمرير كل البيانات (بما فيها المجلدات) لدالة العرض
                showSelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls, durationStr, subjectName, chapterName);

            } catch (Exception e) {
                Toast.makeText(mContext, "Error parsing data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // دالة عرض القائمة (تستلم وتمرر المجلدات)
    private void showSelectionDialog(String title, String youtubeId, List<String> names, List<String> urls, String duration, String subject, String chapter) {
        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("تحميل: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    // دمج الجودة مع العنوان للعرض في الإشعارات
                    String titleWithQuality = title + " (" + names.get(which) + ")";
                    String selectedUrl = urls.get(which);
                    
                    // بدء التحميل مع تمرير المجلدات
                    startDownloadWorker(youtubeId, titleWithQuality, selectedUrl, duration, subject, chapter);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // دالة بدء الـ Worker (تضع البيانات في inputData)
    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration, String subject, String chapter) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl)
                    .putString("duration", duration)
                    
                    // [✅ هام جداً] تمرير أسماء المجلدات للـ Worker
                    .putString("subjectName", subject)
                    .putString("chapterName", chapter)
                    
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
                    Toast.makeText(mContext, "تمت الإضافة لقائمة التحميلات", Toast.LENGTH_SHORT).show()
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
