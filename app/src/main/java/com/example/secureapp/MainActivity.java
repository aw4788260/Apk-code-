package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.content.pm.ActivityInfo;

import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;

import android.widget.TextView; 
import android.content.Intent;   
import android.net.Uri;         

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog; // [✅]

import androidx.work.WorkManager;

import java.io.File; // [✅]

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

        // 1. [🔒 حماية] التحقق من الروت وخيارات المطور قبل أي شيء
        if (!checkSecurityRequirements()) {
            return; // إيقاف التشغيل إذا فشل التحقق (سيتم إظهار رسالة وإغلاق التطبيق)
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

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
             @Override
             public void onPrimaryClipChanged() {
                try {
                    if (!clipboardManager.hasPrimaryClip()) return;
                    ClipData clip = clipboardManager.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;
                    ClipData.Item item = clip.getItemAt(0);
                    CharSequence text = item.getText();
                    if (text != null && (text.toString().contains("youtube.com") || text.toString().contains("youtu.be"))) {
                        clipboardManager.removePrimaryClipChangedListener(this);
                        for (int i = 1; i <= 20; i++) {
                            ClipData junkClip = ClipData.newPlainText("flood" + i, "Item " + i); 
                            clipboardManager.setPrimaryClip(junkClip);
                        }
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                        clipboardManager.addPrimaryClipChangedListener(this);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        clipboardManager.addPrimaryClipChangedListener(this);
                    } catch (Exception re) {}
                }
             }
         };

        String savedUserId = prefs.getString(PREF_USER_ID, null);
        if (savedUserId != null && !savedUserId.isEmpty()) {
            showWebView(savedUserId);
        } else {
            showLogin();
        }
    }

    // =========================================================
    // [🔒 دوال الحماية الجديدة]
    // =========================================================

    private boolean checkSecurityRequirements() {
        // 1. فحص خيارات المطور
        if (isDevOptionsEnabled()) {
            showSecurityAlert("خيارات المطور مفعلة", "الرجاء إغلاق خيارات المطور (Developer Options) من إعدادات الهاتف لضمان أمان التطبيق.");
            return false;
        }

        // 2. فحص الروت (Root)
        if (isDeviceRooted()) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("Security: Rooted Device Detected");
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(new SecurityException("Rooted Device Attempt"));
            showSecurityAlert("الجهاز غير آمن", "تم اكتشاف روت (Root) على هذا الجهاز. لا يمكن تشغيل التطبيق على أجهزة مروّتة.");
            return false;
        }

        return true;
    }

    private void showSecurityAlert(String title, String message) {
        // استخدام AlertDialog لمنع المستخدم من استخدام التطبيق
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false) // لا يمكن إغلاقها باللمس خارجها
            .setPositiveButton("إغلاق التطبيق", (dialog, which) -> {
                finishAffinity(); // إغلاق التطبيق وكل أنشطته
                System.exit(0);   // إنهاء العملية تماماً
            })
            .show();
    }

    // [دالة فحص خيارات المطور]
    private boolean isDevOptionsEnabled() {
        int devOptions = 0;
        try {
            devOptions = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
        return devOptions == 1;
    }

    // [دالة فحص الروت]
    private boolean isDeviceRooted() {
        // 1. فحص علامات البناء (Test-Keys)
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true;
        }

        // 2. فحص وجود ملفات الروت المعروفة في مسارات النظام
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        };

        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        return false;
    }

    // =========================================================

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
                prefs.edit().putString(PREF_USER_ID, userId).apply();
                showWebView(userId);
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"}) 
    private void showWebView(String userId) {
        loginLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (downloadsButton != null) downloadsButton.setVisibility(View.VISIBLE);

        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }

        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        
        // [✅ تعديل أمني هام] تعطيل الوصول للملفات المحلية
        // لمنع ثغرات سرقة البيانات عبر XSS
        ws.setAllowContentAccess(false); 
        ws.setAllowFileAccess(false); 
        ws.setCacheMode(WebSettings.LOAD_DEFAULT); // استخدم الكاش إذا وجد
        ws.setDatabaseEnabled(true);
        
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        
        

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        
        webView.setWebChromeClient(new MyWebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String allowedTelegramUrl = "https://t.me/A7MeDWaLiD0";

                if (url != null && url.equals(allowedTelegramUrl)) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not open link", Toast.LENGTH_SHORT).show();
                    }
                    return true; 
                }

                if (url != null && url.startsWith(BASE_APP_URL)) {
                    return false;
                }
                
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("WebView Error: " + description);
    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCustomKey("FailingURL", failingUrl);
    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(new Exception("WebView Load Error Code: " + errorCode));
                view.stopLoading();
                String htmlData = "<html><body style='background-color:#111; color:white; display:flex; justify-content:center; align-items:center; text-align:center; height:100%; font-family:sans-serif;'>"
                                + "<div>"
                                + "<h1>النت فصل 😟</h1>"
                                + "<p>الرجاء التحقق من اتصالك بالإنترنت.</p>"
                                + "</div>"
                                + "</body></html>";
                view.loadData(htmlData, "text/html", "UTF-8");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    onReceivedError(view, error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
                }
            }
        });

        int appVersionCode = BuildConfig.VERSION_CODE; // يجلب الرقم (مثل 312)
        
        String finalUrl = BASE_APP_URL +
                          "?android_user_id=" + userId +
                          "&android_device_id=" + deviceId +
                          "&app_ver=" + appVersionCode;

        webView.loadUrl(finalUrl);
    }

    private class MyWebChromeClient extends WebChromeClient {
        
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;

            webView.setVisibility(View.GONE);
            loginLayout.setVisibility(View.GONE);
            if (downloadsButton != null) downloadsButton.setVisibility(View.GONE); 
            fullscreenContainer.setVisibility(View.VISIBLE);
            fullscreenContainer.addView(customView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) {
                return;
            }

            fullscreenContainer.removeView(customView);
            customView = null;
            
            webView.setVisibility(View.VISIBLE);
            if (downloadsButton != null) downloadsButton.setVisibility(View.VISIBLE); 
            
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            customViewCallback = null;
        }

        // ✅ [جديد] إخفاء الرابط من رسائل Alert العادية
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(view.getContext())
                    .setTitle("تنبيه") // عنوان نظيف بدلاً من الرابط
                    .setMessage(message)
                    .setPositiveButton("موافق", (dialog, which) -> result.confirm())
                    .setCancelable(false)
                    .show();
            return true;
        }

        // ✅ [جديد] إخفاء الرابط من رسائل Confirm (المستخدمة للتحديث)
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(view.getContext())
                    .setTitle("تحديث") // عنوان نظيف
                    .setMessage(message)
                    .setPositiveButton("تحديث الآن", (dialog, which) -> result.confirm())
                    .setNegativeButton("إلغاء", (dialog, which) -> result.cancel())
                    .setCancelable(false)
                    .show();
            return true;
        }
    }
    
    @Override
    public void onBackPressed() {
        if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } 
        else if (webView.canGoBack()) {
            webView.goBack();
        } 
        else if (webView.getVisibility() == View.VISIBLE) {
            super.onBackPressed(); 
        } 
        else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
        
        if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // [🔒] فحص أمني مستمر:
        if (!checkSecurityRequirements()) { 
             return;
        }

        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            if (clipboardManager != null && clipboardListener != null) {
                clipboardManager.addPrimaryClipChangedListener(clipboardListener); 
            }
        }
    }
}
