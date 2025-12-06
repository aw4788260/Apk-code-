package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"}) 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 حماية قصوى (منع لقطة الشاشة)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_webview);

        String url = getIntent().getStringExtra("URL");
        if (url == null) { finish(); return; }

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // ✅ 1. ربط واجهة التطبيق (لزر العودة والوظائف)
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // ✅ 2. تجهيز بيانات الدخول لحقنها في المتصفح
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String firstName = prefs.getString("FirstName", "User");

        // كود جافاسكريبت لحفظ البيانات في LocalStorage الخاص بالـ WebView
        String jsInjection = String.format(
            "localStorage.setItem('auth_user_id', '%s');" +
            "localStorage.setItem('auth_device_id', '%s');" +
            "localStorage.setItem('auth_first_name', '%s');", 
            userId, deviceId, firstName
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 💉 تنفيذ الحقن بمجرد انتهاء تحميل الصفحة
                view.evaluateJavascript(jsInjection, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // السماح بالروابط الداخلية للموقع
                if (url.contains("aw478260.dpdns.org") || url.contains("secured-bot.vercel.app")) {
                    return false; // تحميل داخل الـ WebView
                }
                // فتح الروابط الخارجية في المتصفح العادي
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        });

        webView.loadUrl(url);
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
