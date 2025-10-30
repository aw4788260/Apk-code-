// app/src/main/java/com/example/secureapp/MainActivity.java
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

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ‼️‼️ هذا هو الرابط الذي أعطيتني إياه ‼️‼️
    private static final String BASE_APP_URL = "https://secured-bot.vercel.app/app";

    private static final String PREFS_NAME = "SecureAppPrefs";
    private static final String PREF_USER_ID = "TelegramUserId";

    private WebView webView;
    private LinearLayout loginLayout;
    private EditText userIdInput;
    private Button loginButton;

    private SharedPreferences prefs;
    private String deviceId; 

    @SuppressLint({"HardwareIds", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. منع تصوير الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
                             
        setContentView(R.layout.activity_main);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        webView = findViewById(R.id.webView);
        loginLayout = findViewById(R.id.login_layout);
        userIdInput = findViewById(R.id.telegram_id_input);
        loginButton = findViewById(R.id.login_button);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedUserId = prefs.getString(PREF_USER_ID, null); 

        if (savedUserId != null && !savedUserId.isEmpty()) {
            showWebView(savedUserId);
        } else {
            showLogin();
        }
    }

    private void showLogin() {
        loginLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = userIdInput.getText().toString().trim();
                
                if (userId.isEmpty()) {
                    Toast.makeText(MainActivity.this, "الرجاء إدخال ID صالح", Toast.LENGTH_SHORT).show();
                } else {
                    prefs.edit().putString(PREF_USER_ID, userId).apply();
                    showWebView(userId);
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String userId) {
        loginLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        // 2. منع النسخ (بالضغط المطول)
        webView.setLongClickable(false);
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true; 
            }
        });

        // (إعدادات الـ WebView التي تسمح بتشغيل الفيديو)
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);        
        ws.setDomStorageEnabled(true);        
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccess(true); 
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);     
        ws.setDisplayZoomControls(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.setWebChromeClient(new WebChromeClient());

        // [ 3 & 4. منع فتح الروابط وتعطيل الحافظة ]
        webView.setWebViewClient(new WebViewClient() {
            
            // 3. منع فتح روابط خارجية (يوتيوب)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith(BASE_APP_URL)) {
                    return false; // اسمح بالروابط الداخلية
                }
                return true; // امنع أي روابط خارجية (مثل يوتيوب)
            }

            // 4. تعطيل الحافظة (لمنع زر "نسخ الرابط")
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // نقوم بحقن جافاسكريبت لتعطيل واجهة الحافظة
                view.evaluateJavascript(
                    "try {" +
                    "  Object.defineProperty(navigator, 'clipboard', { value: undefined, writable: false });" +
                    "} catch(e) {}" +
                    "try {" +
                    "  document.execCommand = function() { return false; };" +
                    "} catch(e) {}",
                    null
                );
            }
        });

        // --- إرسال بصمة الجهاز والـ ID في الرابط ---
        String finalUrl = BASE_APP_URL + 
                          "?android_user_id=" + userId + 
                          "&android_device_id=" + deviceId;
        
        webView.loadUrl(finalUrl);
    }

    // --- [ ✅ 5. هذا هو الإصلاح الخاص بزر الرجوع ] ---
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            // إذا كان المتصفح يستطيع الرجوع (مثل الرجوع من صفحة مشاهدة الفيديو)
            // سيعود إلى صفحة الكورسات
            webView.goBack();
        } else {
            // إذا كان المتصفح في أول صفحة (صفحة الكورسات)
            if (webView.getVisibility() == View.VISIBLE) {
                // لا تخرج من التطبيق، بل أرجع العرض إلى شاشة تسجيل الدخول
                showLogin();
            } else {
                // إذا كان المستخدم أصلاً في شاشة تسجيل الدخول، قم بإغلاق التطبيق
                super.onBackPressed();
            }
        }
    }
}
