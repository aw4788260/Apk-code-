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

        // ✅✅✅ تفعيل ميزة منع تصوير الشاشة ✅✅✅
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        // --- جلب بصمة الجهاز الفعلية ---
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // تهيئة الـ Views
        webView = findViewById(R.id.webView);
        loginLayout = findViewById(R.id.login_layout);
        userIdInput = findViewById(R.id.telegram_id_input);
        loginButton = findViewById(R.id.login_button);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // التحقق إذا كان المستخدم مسجل من قبل
        String savedUserId = prefs.getString(PREF_USER_ID, null);

        // --- إصلاح خطأ الـ ID الفارغ ---
        if (savedUserId != null && !savedUserId.isEmpty()) {
            // المستخدم مسجل، اعرض الـ WebView مباشرة
            showWebView(savedUserId);
        } else {
            // مستخدم جديد، اعرض شاشة تسجيل الدخول
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
                    // احفظ الـ ID
                    prefs.edit().putString(PREF_USER_ID, userId).apply();
                    // اعرض الـ WebView
                    showWebView(userId);
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String userId) {
        loginLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        // إعدادات الـ WebView
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
        webView.setWebViewClient(new WebViewClient());

        // --- إرسال بصمة الجهاز والـ ID في الرابط ---
        String finalUrl = BASE_APP_URL + 
                          "?android_user_id=" + userId + 
                          "&android_device_id=" + deviceId;
        
        webView.loadUrl(finalUrl);
    }

    // --- [ ✅ هذا هو التعديل المطلوب ] ---
    // للتعامل مع زر الرجوع (Back) ليرجع في الـ WebView
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            // 1. إذا كان المتصفح يستطيع الرجوع (مثل الرجوع من صفحة مشاهدة الفيديو)
            webView.goBack();
        } else {
            // 2. إذا كان المتصفح في أول صفحة (صفحة الكورسات)
            if (webView.getVisibility() == View.VISIBLE) {
                // لا تخرج من التطبيق، بل أرجع العرض إلى شاشة تسجيل الدخول
                showLogin();
            } else {
                // 3. إذا كان المستخدم أصلاً في شاشة تسجيل الدخول، قم بإغلاق التطبيق
                super.onBackPressed();
            }
        }
    }
}
