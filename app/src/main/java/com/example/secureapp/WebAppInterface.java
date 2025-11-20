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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WebAppInterface {

    private Context mContext;
    private static final String TAG = "WebAppInterface";

    WebAppInterface(Context c) { mContext = c; }

    /**
     * ✅ الدالة الرئيسية (4 متغيرات) - تستقبل المدة لدعم المشغل
     */
    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl, String durationStr) {
        fetchQualitiesAndShowDialog(youtubeId, videoTitle, proxyUrl, durationStr);
    }

    /**
     * ✅ الدالة الاحتياطية (3 متغيرات) - لضمان عمل التطبيق لو الموقع مرسلش المدة
     */
    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl) {
        fetchQualitiesAndShowDialog(youtubeId, videoTitle, proxyUrl, "0");
    }

    // دالة جلب الجودات (باستخدام الكود البسيط الذي طلبته)
    private void fetchQualitiesAndShowDialog(String youtubeId, String videoTitle, String proxyUrl, String durationStr) {
        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري جلب الجودات...", Toast.LENGTH_SHORT).show()
        );

        new Thread(() -> {
            try {
                // 1. تجهيز الرابط
                String finalProxyUrl = (proxyUrl != null && !proxyUrl.isEmpty()) ? proxyUrl : "https://web-production-3a04a.up.railway.app";
                if (finalProxyUrl.endsWith("/")) finalProxyUrl = finalProxyUrl.substring(0, finalProxyUrl.length() - 1);
                
                String apiUrl = finalProxyUrl + "/api/get-hls-playlist?youtubeId=" + youtubeId;

                // 2. الاتصال (أبسط إعداد ممكن - كما طلبت)
                OkHttpClient client = new OkHttpClient.Builder()
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
                
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .build(); // بدون User-Agent مخصص
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new Exception("Server Error: " + response.code());

                    String jsonBody = response.body().string();
                    JSONObject json = new JSONObject(jsonBody);
                    
                    if (!json.has("availableQualities")) throw new Exception("No qualities found");
                    
                    JSONArray qualitiesArray = json.getJSONArray("availableQualities");
                    List<String> qualityNames = new ArrayList<>();
                    List<String> qualityUrls = new ArrayList<>();
                    
                    for (int i = 0; i < qualitiesArray.length(); i++) {
                        JSONObject q = qualitiesArray.getJSONObject(i);
                        // حفظ الجودة كرقم (مثلاً 720p)
                        qualityNames.add(q.optInt("quality", 0) + "p");
                        qualityUrls.add(q.getString("url"));
                    }

                    // عرض الديالوج (مع تمرير المدة)
                    activity.runOnUiThread(() -> showSelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls, durationStr));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching qualities", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(mContext, "فشل الاتصال: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    // عرض القائمة
    private void showSelectionDialog(String title, String youtubeId, List<String> names, List<String> urls, String duration) {
        if (names.isEmpty()) {
             Toast.makeText(mContext, "لا توجد جودات متاحة.", Toast.LENGTH_SHORT).show();
             return;
        }

        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("اختر الجودة: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    // ندمج الجودة مع الاسم ليظهر في قائمة التحميلات (مثل: درس 1 (720p))
                    String titleWithQuality = title + " (" + names.get(which) + ")";
                    String selectedUrl = urls.get(which);
                    
                    startDownloadWorker(youtubeId, titleWithQuality, selectedUrl, duration);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // بدء التحميل
    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl)
                    .putString("duration", duration) // ✅ تمرير المدة ضروري جداً
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
