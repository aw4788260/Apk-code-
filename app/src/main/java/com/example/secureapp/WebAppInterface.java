package com.example.secureapp;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;

public class WebAppInterface {

    private Context mContext;
    private static final String TAG = "WebAppInterface";

    WebAppInterface(Context c) {
        mContext = c;
    }

    /**
     * دالة الجافاسكريبت التي يتم استدعاؤها من الموقع.
     * التعديل: تستقبل الآن youtubeId, videoTitle, و proxyUrl.
     */
    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle, String proxyUrl) {
        try {
            Log.d(TAG, "Download request received via JavaScript: " + videoTitle);
            Log.d(TAG, "Proxy URL: " + proxyUrl);
            
            DownloadLogger.logError(mContext, TAG, "Download request received for: " + videoTitle + " | Server: " + proxyUrl);

            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "بدء تحميل: " + videoTitle, Toast.LENGTH_SHORT).show()
                );
            }

            // 1. تجهيز البيانات لإرسالها للـ Worker (بما في ذلك الرابط الجديد)
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, videoTitle)
                    .putString("proxyUrl", proxyUrl) // ✅ تمرير رابط السيرفر
                    .build();

            // 2. شروط التحميل (وجود إنترنت)
            Constraints downloadConstraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresStorageNotLow(false)
                    .build();

            // 3. إنشاء طلب العمل
            OneTimeWorkRequest downloadWorkRequest =
                    new OneTimeWorkRequest.Builder(DownloadWorker.class)
                            .setInputData(inputData)
                            .setConstraints(downloadConstraints)
                            .addTag("download_work_tag")
                            .build();

            // 4. بدء المهمة
            WorkManager.getInstance(mContext).enqueue(downloadWorkRequest);
            DownloadLogger.logError(mContext, TAG, "WorkManager enqueued successfully.");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download worker", e);
            DownloadLogger.logError(mContext, TAG, "CRITICAL: Failed to enqueue worker: " + e.getMessage());
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "فشل بدء التحميل", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }
}
