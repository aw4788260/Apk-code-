package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 حماية قصوى: منع تصوير الشاشة أثناء الامتحان أو المشاهدة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_webview);

        String url = getIntent().getStringExtra("URL");
        if (url == null) { finish(); return; }

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        
        // تفعيل الجافاسكريبت والتخزين ليعمل الموقع بكفاءة
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // لتشغيل الفيديو تلقائياً

        // منع فتح الروابط في متصفح خارجي
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // السماح فقط بروابط البوت الخاصة بك
                if (url.contains("secured-bot.vercel.app")) {
                    return false; // افتحه داخل التطبيق
                }
                // أي رابط خارجي (مثل تلجرام) يفتح في التطبيق المناسب
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        });

        webView.loadUrl(url);
    }
    
    // عند الضغط على رجوع، يعود للصفحة السابقة في الموقع وليس الخروج فوراً
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
