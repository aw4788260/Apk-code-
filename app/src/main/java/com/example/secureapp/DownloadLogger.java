package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;

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
     * يسجل رسالة خطأ جديدة مع طابع زمني.
     */
    public static void logError(Context context, String tag, String message) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // جلب السجلات الحالية. نستخدم new HashSet لضمان أن القائمة قابلة للتعديل.
            Set<String> logs = new HashSet<>(prefs.getStringSet(LOGS_KEY, new HashSet<>()));

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = timestamp + " [" + tag + "]: " + message;

            logs.add(logEntry);

            prefs.edit().putStringSet(LOGS_KEY, logs).apply();
        } catch (Exception e) {
            // (تجاهل الخطأ الذي قد يحدث أثناء تسجيل الخطأ)
            e.printStackTrace();
        }
    }

    /**
     * يجلب كل السجلات المخزنة.
     */
    public static ArrayList<String> getLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> logs = prefs.getStringSet(LOGS_KEY, new HashSet<>());
        // تحويلها إلى ArrayList لسهولة الترتيب
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
