package com.example.secureapp;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class WebAppInterface {

    private Context mContext;
    private static final String TAG = "WebAppInterface";

    WebAppInterface(Context c) {
        mContext = c;
    }

    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle) { 
        Log.d(TAG, "Download request received via JavaScript: " + videoTitle);

        try {
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() -> 
                    Toast.makeText(mContext, "بدء تحميل: " + videoTitle, Toast.LENGTH_SHORT).show()
                );
            }

            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, videoTitle)
                    .build();

            OneTimeWorkRequest downloadWorkRequest =
                    new OneTimeWorkRequest.Builder(DownloadWorker.class)
                            .setInputData(inputData)
                            .addTag("download_work_tag") // [ ✅ وسم المتابعة ]
                            .build();

            WorkManager.getInstance(mContext).enqueue(downloadWorkRequest);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download worker", e);
            ((MainActivity) mContext).runOnUiThread(() -> 
                Toast.makeText(mContext, "فشل بدء التحميل", Toast.LENGTH_SHORT).show()
            );
        }
    }
}
