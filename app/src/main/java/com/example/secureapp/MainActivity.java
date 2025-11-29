package com.example.secureapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secureapp.network.DeviceCheckRequest;
import com.example.secureapp.network.DeviceCheckResponse;
import com.example.secureapp.network.RetrofitClient;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_APP_URL = "https://secured-bot.vercel.app/app";
    private static final String PREFS_NAME = "SecureAppPrefs";
    private static final String PREF_USER_ID = "TelegramUserId";

    private WebView webView;
    private View loginLayout;
    private EditText userIdInput;
    private Button loginButton;
    private TextView contactLink;
    private Button downloadsButton;

    private SharedPreferences prefs;
    private String deviceId;

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @SuppressLint({"HardwareIds", "SetJavaScriptEnabled", "JavascriptInterface"}) 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            androidx.work.WorkManager.getInstance(this).cancelAllWork();
            androidx.work.WorkManager.getInstance(this).pruneWork();
        } catch (Exception e) {
        }

        if (!checkSecurityRequirements()) {
            return;
        }

        DownloadLogger.logAppStartInfo(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        fullscreenContainer = findViewById(R.id.fullscreen_container);
        webView = findViewById(R.id.webView);
        loginLayout = findViewById(R.id.login_layout); 
        userIdInput = findViewById(R.id.telegram_id_input);
        loginButton = findViewById(R.id.login_button);
        contactLink = findViewById(R.id.contact_link); 
        downloadsButton = findViewById(R.id.downloads_button); 

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        contactLink.setOnClickListener(v -> {
            String telegramUrl = "https://t.me/A7MeDWaLiD0";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        });

        downloadsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DownloadsActivity.class);
            startActivity(intent);
        });

        setupClipboardProtection();

        String savedUserId = prefs.getString(PREF_USER_ID, null);
        
        if (savedUserId != null && !savedUserId.isEmpty()) {
            openNativeHome();
        } else {
            showLogin();
        }
    }

    private void openNativeHome() {
        Intent intent = new Intent(MainActivity.this, NativeHomeActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean checkSecurityRequirements() {
        if (isDevOptionsEnabled()) {
            showSecurityAlert("خيارات المطور مفعلة", "الرجاء إغلاق خيارات المطور (Developer Options) من إعدادات الهاتف لضمان أمان التطبيق.");
            return false;
        }
        if (isDeviceRooted()) {
            FirebaseCrashlytics.getInstance().log("Security: Rooted Device Detected");
            FirebaseCrashlytics.getInstance().recordException(new SecurityException("Rooted Device Attempt"));
            showSecurityAlert("الجهاز غير آمن", "تم اكتشاف روت (Root) على هذا الجهاز. لا يمكن تشغيل التطبيق على أجهزة مروّتة.");
            return false;
        }
        return true;
    }

    private void showSecurityAlert(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("إغلاق التطبيق", (dialog, which) -> {
                finishAffinity();
                System.exit(0);
            })
            .show(); // ✅ تم حذف الحرف # من هنا
    }

    private boolean isDevOptionsEnabled() {
        int devOptions = 0;
        try {
            devOptions = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        } catch (Settings.SettingNotFoundException e) { return false; }
        return devOptions == 1;
    }

    private boolean isDeviceRooted() {
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) return true;
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su" };
        for (String path : paths) { if (new File(path).exists()) return true; }
        return false;
    }

    private void setupClipboardProtection() {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = () -> {
            try {
                if (!clipboardManager.hasPrimaryClip()) return;
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return;
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null && (text.toString().contains("youtube.com") || text.toString().contains("youtu.be"))) {
                    clipboardManager.removePrimaryClipChangedListener(clipboardListener);
                    for (int i = 1; i <= 20; i++) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("flood" + i, "Item " + i));
                    }
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                    clipboardManager.addPrimaryClipChangedListener(clipboardListener);
                }
            } catch (Exception e) { 
                try { clipboardManager.addPrimaryClipChangedListener(clipboardListener); } catch (Exception re) {} 
            }
        };
    }

    private void showLogin() {
        loginLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        if (downloadsButton != null) downloadsButton.setVisibility(View.GONE);
        
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }

        loginButton.setOnClickListener(v -> {
            String userId = userIdInput.getText().toString().trim();
            if (userId.isEmpty()) {
                Toast.makeText(MainActivity.this, "الرجاء إدخال ID صالح", Toast.LENGTH_SHORT).show();
            } else {
                performLoginCheck(userId);
            }
        });
    }

    private void performLoginCheck(String userId) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("جاري التحقق من الجهاز...");
        dialog.setCancelable(false);
        dialog.show();

        RetrofitClient.getApi().checkDevice(new DeviceCheckRequest(userId, deviceId))
            .enqueue(new Callback<DeviceCheckResponse>() {
                @Override
                public void onResponse(Call<DeviceCheckResponse> call, Response<DeviceCheckResponse> response) {
                    dialog.dismiss();
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().success) {
                            // ✅ نجاح
                            prefs.edit().putString(PREF_USER_ID, userId).apply();
                            openNativeHome();
                        } else {
                            // ❌ فشل: جهاز مختلف (لو السيرفر رجع 200 مع success=false)
                            showDeviceMismatchDialog();
                        }
                    } 
                    // ✅✅ معالجة كود 403 (جهاز مختلف)
                    else if (response.code() == 403) {
                        showDeviceMismatchDialog();
                    }
                    else {
                        showErrorDialog("خطأ", "فشل الاتصال بالسيرفر. تأكد من الإنترنت.");
                    }
                }

                @Override
                public void onFailure(Call<DeviceCheckResponse> call, Throwable t) {
                    dialog.dismiss();
                    showErrorDialog("خطأ شبكة", "تأكد من اتصالك بالإنترنت وحاول مرة أخرى.");
                }
            });
    }

    // ✅ دالة جديدة لعرض رسالة البصمة بوضوح
    private void showDeviceMismatchDialog() {
        new AlertDialog.Builder(this)
            .setTitle("⛔ جهاز غير مصرح به")
            .setMessage("هذا الحساب مرتبط بجهاز آخر.\n\nلا يمكن استخدام الحساب إلا على الجهاز الأصلي الذي تم التسجيل منه.")
            .setPositiveButton("حسناً", null)
            .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("موافق", null)
            .show();
    }

    // --- (أكواد الويب القديمة - مبقاة كمرجع) ---

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"}) 
    private void showWebView(String userId) {
        loginLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowContentAccess(false); 
        ws.setAllowFileAccess(false); 
        ws.setDatabaseEnabled(true);
        
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.setWebChromeClient(new MyWebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        
        int appVersionCode = BuildConfig.VERSION_CODE;
        String finalUrl = BASE_APP_URL + "?android_user_id=" + userId + "&android_device_id=" + deviceId + "&app_ver=" + appVersionCode;
        webView.loadUrl(finalUrl);
    }

    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customViewCallback = callback;
            webView.setVisibility(View.GONE);
            loginLayout.setVisibility(View.GONE);
            fullscreenContainer.setVisibility(View.VISIBLE);
            fullscreenContainer.addView(customView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            fullscreenContainer.removeView(customView);
            customView = null;
            webView.setVisibility(View.VISIBLE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }
    
    @Override
    public void onBackPressed() {
        if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } else if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!checkSecurityRequirements()) return;
        if (webView != null && webView.getVisibility() == View.VISIBLE && clipboardManager != null) {
             clipboardManager.addPrimaryClipChangedListener(clipboardListener); 
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
    }
}
