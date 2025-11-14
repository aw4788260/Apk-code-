package com.example.secureapp;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
// [ ✅✅ جديد: إضافة imports للقيود ]
import androidx.work.Constraints;
import androidx.work.NetworkType;

/**
 * هذا هو "الجسر" الذي يسمح للـ JavaScript داخل WebView
 * بالتحدث إلى كود Java الأصلي (Native).
 */
public class WebAppInterface {

    private Context mContext;
    private static final String TAG = "WebAppInterface";

    /**
     * المُنشئ (Constructor) يحفظ الـ Context
     * (الذي هو MainActivity) لاستخدامه لاحقاً
     */
    WebAppInterface(Context c) {
        mContext = c;
    }

    /**
     * هذه هي الدالة التي يستدعيها JavaScript
     * (يجب أن تحتوي على @JavascriptInterface)
     *
     * @param youtubeId  الـ ID الخاص بالفيديو (مثل "abc12345")
     * @param videoTitle العنوان الذي سيظهر في قائمة التحميلات
     */
    @JavascriptInterface
    public void downloadVideo(String youtubeId, String videoTitle) { 
        Log.d(TAG, "Download request received via JavaScript: " + videoTitle);

        try {
            // 1. إظهار رسالة سريعة للمستخدم
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() -> 
                    Toast.makeText(mContext, "بدء تحميل: " + videoTitle, Toast.LENGTH_SHORT).show()
                );
            }

            // 2. تجهيز البيانات لإرسالها إلى الـ Worker
            Data inputData = new Data.Builder()
                    .putString(DownloadWorker.KEY_YOUTUBE_ID, youtubeId)
                    .putString(DownloadWorker.KEY_VIDEO_TITLE, videoTitle)
                    .build();

            // [ ✅✅✅ هذا هو الإصلاح: إضافة قيود متساهلة ]
            Constraints downloadConstraints = new Constraints.Builder()
                    // (استخدم أي شبكة متاحة، واي فاي أو بيانات هاتف)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    // [ ✅ جديد: تشغيل التحميل حتى لو البطارية منخفضة ]
                    .setRequiresBatteryNotLow(false)
                    // [ ✅ جديد: تشغيل التحميل حتى لو المساحة منخفضة ]
                    .setRequiresStorageNotLow(false)
                    .build();

            // 3. إنشاء طلب تحميل لمرة واحدة
            OneTimeWorkRequest downloadWorkRequest =
                    new OneTimeWorkRequest.Builder(DownloadWorker.class)
                            .setInputData(inputData)
                            .setConstraints(downloadConstraints) // [ ✅ تطبيق القيود ]
                            .addTag("download_work_tag") 
                            .build();

            // 4. إرسال الطلب إلى WorkManager لبدء التحميل في الخلفية
            WorkManager.getInstance(mContext).enqueue(downloadWorkRequest);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start download worker", e);
            ((MainActivity) mContext).runOnUiThread(() -> 
                Toast.makeText(mContext, "فشل بدء التحميل", Toast.LENGTH_SHORT).show()
            );
        }
    }
}
