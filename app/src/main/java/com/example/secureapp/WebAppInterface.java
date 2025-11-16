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

    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle) {
        // [ ✅✅ تعديل: تم تغليف كل شيء بـ try/catch ]
        try {
            Log.d(TAG, "Download request received via JavaScript: " + videoTitle);
            DownloadLogger.logError(mContext, TAG, "Download request received for: " + videoTitle); // [ ✅ لوج ]

            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "بدء تحميل: " + videoTitle, Toast.LENGTH_SHORT).show()
                );
            }

            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, videoTitle)
                    .build();

            Constraints downloadConstraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresStorageNotLow(false)
                    .build();

            OneTimeWorkRequest downloadWorkRequest =
                    new OneTimeWorkRequest.Builder(DownloadWorker.class)
                            .setInputData(inputData)
                            .setConstraints(downloadConstraints)
                            .addTag("download_work_tag")
                            .build();

            WorkManager.getInstance(mContext).enqueue(downloadWorkRequest);
            DownloadLogger.logError(mContext, TAG, "WorkManager enqueued successfully."); // [ ✅ لوج ]

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download worker", e);
            DownloadLogger.logError(mContext, TAG, "CRITICAL: Failed to enqueue worker: " + e.getMessage()); // [ ✅ لوج ]
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() ->
                    Toast.makeText(mContext, "فشل بدء التحميل", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }
}
