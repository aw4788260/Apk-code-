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
     * دالة التحميل (مبسطة وتدعم النسختين القديمة والجديدة)
     */
    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl, String durationStr) {
        startVideoDownloadProcess(youtubeId, videoTitle, proxyUrl, durationStr);
    }

    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl) {
        startVideoDownloadProcess(youtubeId, videoTitle, proxyUrl, "0");
    }

    private void startVideoDownloadProcess(String youtubeId, String videoTitle, String proxyUrl, String durationStr) {
        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري الاتصال...", Toast.LENGTH_SHORT).show()
        );

        new Thread(() -> {
            try {
                // تجهيز الرابط
                String finalProxyUrl = (proxyUrl != null && !proxyUrl.isEmpty()) ? proxyUrl : "https://web-production-3a04a.up.railway.app";
                if (finalProxyUrl.endsWith("/")) finalProxyUrl = finalProxyUrl.substring(0, finalProxyUrl.length() - 1);
                
                String apiUrl = finalProxyUrl + "/api/get-hls-playlist?youtubeId=" + youtubeId;

                // ✅ أبسط إعداد ممكن للاتصال (بدون User-Agent وبدون Headers إضافية)
                OkHttpClient client = new OkHttpClient.Builder()
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
                
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .build(); // طلب افتراضي تماماً
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("Server Error (" + response.code() + ")");
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    
                    if (!json.has("availableQualities")) throw new Exception("No qualities found");
                    JSONArray qualitiesArray = json.getJSONArray("availableQualities");
                    
                    List<String> qualityNames = new ArrayList<>();
                    List<String> qualityUrls = new ArrayList<>();
                    
                    for (int i = 0; i < qualitiesArray.length(); i++) {
                        JSONObject q = qualitiesArray.getJSONObject(i);
                        qualityNames.add(q.optInt("quality", 0) + "p");
                        qualityUrls.add(q.getString("url"));
                    }

                    activity.runOnUiThread(() -> showQualitySelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls, durationStr));
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection Error", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(mContext, "خطأ: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void showQualitySelectionDialog(String title, String youtubeId, List<String> names, List<String> urls, String duration) {
        if (names.isEmpty()) return;
        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("اختر الجودة: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    // نمرر الاسم مدمجاً مع الجودة ليتم حفظه، وسنقوم بفصله لاحقاً في العرض
                    startDownloadWorker(youtubeId, title + " (" + names.get(which) + ")", urls.get(which), duration);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startDownloadWorker(String youtubeId, String title, String directUrl, String duration) {
        try {
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl)
                    .putString("duration", duration)
                    .build();

            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("download_work_tag")
                    .build();

            WorkManager.getInstance(mContext).enqueue(request);
            Toast.makeText(mContext, "تمت الإضافة لقائمة التحميل", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(mContext, "فشل البدء", Toast.LENGTH_SHORT).show();
        }
    }
}
