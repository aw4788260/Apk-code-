package com.example.secureapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    
    // ✅ متغيرات خاصة برفع الملفات
    private ValueCallback<Uri[]> uploadMessage;
    private static final int FILECHOOSER_RESULTCODE = 1;

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
        
        // ✅ السماح بالوصول للملفات (ضروري لبعض إصدارات الأندرويد)
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

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

        // ✅ إضافة WebChromeClient لدعم اختيار الملفات (input type="file")
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                // إلغاء أي طلب سابق لم يتم التعامل معه
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
                    Toast.makeText(WebViewActivity.this, "لا يمكن فتح مدير الملفات", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 💉 تنفيذ الحقن بمجرد انتهاء تحميل الصفحة
                view.evaluateJavascript(jsInjection, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 🛡️ 1. السماح فقط بالدومين الرسمي الخاص بنا
                if (url.contains("aw478260.dpdns.org")) {
                    return false; // تحميل داخل الـ WebView (مسموح)
                }

                // ⛔ 2. حظر أي رابط خارجي آخر
                return true; 
            }
        });

        webView.loadUrl(url);
    }
    
    // ✅ استقبال نتيجة اختيار الملف من النظام
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
