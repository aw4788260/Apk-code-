package com.example.secureapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager; // للكشف عن الشاشة
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.CookieManager; // لإدارة الكوكيز
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int FILECHOOSER_RESULTCODE = 1;
    
    // متغيرات كشف التصوير
    private Handler screenCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable screenCheckRunnable;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"}) 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. حماية FLAG_SECURE (تمنع التقاط الصور وتجعل الفيديو أسود)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_webview);

        // بدء مراقبة تصوير الشاشة
        startScreenRecordingMonitor();

        String url = getIntent().getStringExtra("URL");
        if (url == null) { finish(); return; }

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // مهم للـ LocalStorage
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // ✅ 2. إصلاح مشكلة فقدان الجلسة (تفعيل الكوكيز)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // جلب البيانات
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String firstName = prefs.getString("FirstName", "User");
        boolean isAdmin = prefs.getBoolean("IsAdmin", false); // هل هو أدمن؟

        // ✅ 3. بناء كود الحقن (يشمل بيانات الأدمن إذا وجدت)
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append(String.format(
            "localStorage.setItem('auth_user_id', '%s');" +
            "localStorage.setItem('auth_device_id', '%s');" +
            "localStorage.setItem('auth_first_name', '%s');", 
            userId, deviceId, firstName
        ));

        // إذا كان أدمن، نحقن بيانات الأدمن أيضاً ليعمل الدخول التلقائي للوحة
        if (isAdmin) {
            jsBuilder.append(String.format(
                "localStorage.setItem('admin_user_id', '%s');" +
                "localStorage.setItem('is_admin_session', 'true');",
                userId
            ));
        }

        String jsInjection = jsBuilder.toString();

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // تنفيذ الحقن
                view.evaluateJavascript(jsInjection, null);
                
                // ✅ 4. حفظ الكوكيز في القرص فوراً
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("aw478260.dpdns.org")) {
                    return false; 
                }
                return true; 
            }
        });

        webView.loadUrl(url);
    }
    
    // =========================================================
    // 🛡️ كشف تصوير الشاشة (Screen Recording Detection)
    // =========================================================
    private void startScreenRecordingMonitor() {
        screenCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScreenRecording()) {
                    handleScreenRecordingDetected();
                } else {
                    screenCheckHandler.postDelayed(this, 1000); // فحص كل ثانية
                }
            }
        };
        screenCheckHandler.post(screenCheckRunnable);
    }

    private boolean isScreenRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            for (Display display : dm.getDisplays()) {
                if (display.getFlags() == Display.FLAG_PRESENTATION) {
                    return true; // تم اكتشاف شاشة عرض خارجية (تصوير)
                }
            }
        }
        return false;
    }

    private void handleScreenRecordingDetected() {
        screenCheckHandler.removeCallbacks(screenCheckRunnable);
        
        // إظهار تحذير وإغلاق التطبيق
        new AlertDialog.Builder(this)
            .setTitle("⛔ تنبيه أمني")
            .setMessage("تم اكتشاف برنامج لتصوير الشاشة!\n\nيمنع منعاً باتاً تصوير المحتوى. سيتم إغلاق التطبيق الآن لحماية الحقوق.")
            .setCancelable(false)
            .setPositiveButton("إغلاق", (dialog, which) -> {
                finishAffinity(); // إغلاق كل شيء
                System.exit(0);
            })
            .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ✅ 5. حفظ الكوكيز عند الخروج المؤقت
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        screenCheckHandler.removeCallbacks(screenCheckRunnable);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (uploadMessage == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (intent != null) {
                    String dataString = intent.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
