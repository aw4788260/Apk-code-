package com.example.secureapp;

import android.content.Context;
import android.content.DialogInterface;
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

    WebAppInterface(Context c) {
        mContext = c;
    }

    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl) {
        // نتأكد من أننا في الـ Activity لنستطيع عرض Dialog
        if (!(mContext instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) mContext;

        activity.runOnUiThread(() -> 
            Toast.makeText(mContext, "جاري جلب الجودات المتاحة...", Toast.LENGTH_SHORT).show()
        );

        // تشغيل عملية الجلب في خيط خلفي (Thread) لمنع تجميد الواجهة
        new Thread(() -> {
            try {
                // 1. تجهيز رابط السيرفر
                String finalProxyUrl = (proxyUrl != null && !proxyUrl.isEmpty()) ? proxyUrl : "https://web-production-3a04a.up.railway.app";
                if (finalProxyUrl.endsWith("/")) finalProxyUrl = finalProxyUrl.substring(0, finalProxyUrl.length() - 1);
                
                String apiUrl = finalProxyUrl + "/api/get-hls-playlist?youtubeId=" + youtubeId;

                // 2. الاتصال بالسيرفر
                OkHttpClient client = new OkHttpClient.Builder()
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder().url(apiUrl).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new Exception("Server Error: " + response.code());

                    String jsonBody = response.body().string();
                    JSONObject json = new JSONObject(jsonBody);
                    
                    // 3. استخراج الجودات
                    if (!json.has("availableQualities")) throw new Exception("No qualities found");
                    
                    JSONArray qualitiesArray = json.getJSONArray("availableQualities");
                    
                    List<String> qualityNames = new ArrayList<>();
                    List<String> qualityUrls = new ArrayList<>();
                    
                    for (int i = 0; i < qualitiesArray.length(); i++) {
                        JSONObject q = qualitiesArray.getJSONObject(i);
                        int quality = q.optInt("quality", 0);
                        String url = q.getString("url");
                        
                        qualityNames.add(quality + "p");
                        qualityUrls.add(url);
                    }

                    // 4. العودة للواجهة الرئيسية لعرض القائمة
                    activity.runOnUiThread(() -> showQualitySelectionDialog(videoTitle, youtubeId, qualityNames, qualityUrls));
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching qualities", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(mContext, "فشل جلب الجودات: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void showQualitySelectionDialog(String title, String youtubeId, List<String> names, List<String> urls) {
        if (names.isEmpty()) {
            Toast.makeText(mContext, "لا توجد جودات متاحة للتحميل.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] namesArray = names.toArray(new String[0]);

        new AlertDialog.Builder(mContext)
                .setTitle("اختر جودة التحميل: " + title)
                .setItems(namesArray, (dialog, which) -> {
                    String selectedUrl = urls.get(which);
                    String selectedQuality = names.get(which);
                    startDownloadWorker(youtubeId, title + " (" + selectedQuality + ")", selectedUrl);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startDownloadWorker(String youtubeId, String title, String directUrl) {
        try {
            // إرسال الرابط المباشر (directUrl) للـ Worker
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, title)
                    .putString("specificUrl", directUrl) // ✅ المفتاح الجديد
                    .build();

            Constraints downloadConstraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest downloadWorkRequest =
                    new OneTimeWorkRequest.Builder(DownloadWorker.class)
                            .setInputData(inputData)
                            .setConstraints(downloadConstraints)
                            .addTag("download_work_tag")
                            .build();

            WorkManager.getInstance(mContext).enqueue(downloadWorkRequest);
            
            Toast.makeText(mContext, "بدأ التحميل: " + title, Toast.LENGTH_SHORT).show();
            DownloadLogger.logError(mContext, TAG, "Enqueued download for: " + title);

        } catch (Exception e) {
            Toast.makeText(mContext, "فشل بدء التحميل", Toast.LENGTH_SHORT).show();
        }
    }
}
