package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;
// [ ✅✅ إضافة imports جديدة ]
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.Manifest; // (مهم جداً)

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * كلاس مساعد لتسجيل الأخطاء والعمليات الخاصة بالتحميل
 * في SharedPreferences لعرضها في DownloadsActivity.
 */
public class DownloadLogger {

    private static final String PREFS_NAME = "DownloadLogs";
    private static final String LOGS_KEY = "logs";

    /**
     * [ ✅✅ دالة جديدة: لتسجيل بيانات تشخيص التطبيق ]
     * تسجل إصدار الأندرويد، الـ SDK المستهدف، وحالة الأذونات.
     */
    public static void logAppStartInfo(Context context) {
        try {
            // (مسح السجلات القديمة لبدء تشخيص نظيف)
            // clearLogs(context); 
            // (ملاحظة: يمكنك إلغاء التعليق أعلاه إذا أردت مسح السجل مع كل فتح للتطبيق)

            logError(context, "AppStart", "--- App Diagnostics ---");

            // 1. تسجيل إصدارات الـ SDK
            String deviceOS = "Device OS: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
            logError(context, "AppStart", deviceOS);

            int targetSdk = context.getApplicationInfo().targetSdkVersion;
            int minSdk = context.getApplicationInfo().minSdkVersion;
            logError(context, "AppStart", "App Target SDK: " + targetSdk + " (Rules for Android " + (targetSdk <= 33 ? "13" : "14") + ")");
            logError(context, "AppStart", "App Min SDK: " + minSdk + " (Supports Android " + (minSdk == 23 ? "6" : "Other") + ")");

            // 2. تسجيل حالة الأذونات الهامة
            logError(context, "AppStart", "--- Permissions Status ---");
            
            // (الإذن المطلوب لـ Android 13+ لإظهار الإشعارات)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                boolean notifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                logError(context, "AppStart", "POST_NOTIFICATIONS (SDK 33+): " + (notifications ? "GRANTED" : "DENIED"));
            }

            // (الإذن العام للخدمة)
            boolean fgService = ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED;
            logError(context, "AppStart", "FOREGROUND_SERVICE (General): " + (fgService ? "GRANTED" : "DENIED"));

            // (الإذن الحاسم لـ SDK 34+)
            if (targetSdk >= 34) {
                // (هذا الإذن تتم إضافته فقط في Manifest، لا يطلبه المستخدم)
                logError(context, "AppStart", "FOREGROUND_SERVICE_DATA_SYNC: (Checking Manifest...)");
            }
            
            logError(context, "AppStart", "--------------------------");

        } catch (Exception e) {
            logError(context, "AppStart", "Error in logAppStartInfo: " + e.getMessage());
        }
    }


    /**
     * يسجل رسالة خطأ جديدة مع طابع زمني.
     */

    // في ملف DownloadLogger.java

public static void logError(Context context, String tag, String message) {
    try {
        // 1. الحفظ المحلي (الكود القديم)
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> logs = new HashSet<>(prefs.getStringSet(LOGS_KEY, new HashSet<>()));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        logs.add(timestamp + " [" + tag + "]: " + message);
        prefs.edit().putStringSet(LOGS_KEY, logs).apply();

        // 2. إرسال لفايربيس بذكاء
        // [✅ تعديل] نسجلها كـ "Log" فقط لتظهر في تفاصيل الكراش، ولا تظهر كمشكلة مستقلة
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log(tag + ": " + message);

        // [✅ شرط جديد] نسجلها كـ "Exception" فقط إذا كانت رسالة خطأ حقيقية
        // (مثلاً تحتوي على كلمة Exception أو Error أو Failed)
        if (message.toLowerCase().contains("exception") || 
            message.toLowerCase().contains("error") || 
            message.toLowerCase().contains("failed") ||
            tag.equals("DownloadWorker")) { // أو إذا كانت قادمة من الـ Worker
            
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(new Exception(tag + ": " + message));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    /**
     * يجلب كل السجلات المخزنة.
     */
    public static ArrayList<String> getLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> logs = prefs.getStringSet(LOGS_KEY, new HashSet<>());
        return new ArrayList<>(logs);
    }

    /**
     * يمسح كل السجلات.
     */
    public static void clearLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(LOGS_KEY).apply();
    }
}
