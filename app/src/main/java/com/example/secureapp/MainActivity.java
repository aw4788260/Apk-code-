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

// --- [ الإضافات المطلوبة ] ---
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_APP_URL = "https://secured-bot.vercel.app/app";
    private static final String PREFS_NAME = "SecureAppPrefs";
    private static final String PREF_USER_ID = "TelegramUserId";

    private WebView webView;
    private LinearLayout loginLayout;
    private EditText userIdInput;
    private Button loginButton;

    private SharedPreferences prefs;
    private String deviceId; 

    // متغيرات لتعطيل الحافظة
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;

    @SuppressLint({"HardwareIds", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // (موجود من قبل) منع تصوير الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
                             
        setContentView(R.layout.activity_main);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        webView = findViewById(R.id.webView);
        loginLayout = findViewById(R.id.login_layout);
        userIdInput = findViewById(R.id.telegram_id_input);
        loginButton = findViewById(R.id.login_button);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // --- [ ✅ هذا هو الكود المعدل لـ "إغراق الحافظة" ] ---
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                try {
                    // 1. نتأكد أن هناك شيئاً في الحافظة
                    if (!clipboardManager.hasPrimaryClip()) return;
                    
                    ClipData clip = clipboardManager.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;

                    ClipData.Item item = clip.getItemAt(0);
                    CharSequence text = item.getText();

                    // 2. نتحقق هل هو رابط يوتيوب (وليس أحد عناصر الإغراق)
                    if (text != null && (text.toString().contains("youtube.com") || text.toString().contains("youtu.be"))) {
                        
                        // 3. (الأهم) نزيل المستمع مؤقتاً لمنع الانهيار
                        clipboardManager.removePrimaryClipChangedListener(this);

                        // 4. نبدأ "الإغراق" بـ 20 عنصر مختلف (كما طلبت)
                        for (int i = 1; i <= 20; i++) {
                            // "label" هو اسم العنصر، "Item " + i هو المحتوى الفعلي
                            ClipData junkClip = ClipData.newPlainText("flood" + i, "Item " + i); 
                            clipboardManager.setPrimaryClip(junkClip);
                        }
                        
                        // 5. التنظيف النهائي (مسح آخر عنصر وهمي)
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));

                        // 6. نعيد المستمع للعمل
                        clipboardManager.addPrimaryClipChangedListener(this);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // في حال حدوث أي خطأ، نحاول إعادة المستمع
                    try {
                        clipboardManager.addPrimaryClipChangedListener(this);
                    } catch (Exception re) {}
                }
            }
        };
        // --- [ نهاية الكود المعدل ] ---

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

        // إيقاف المستمع عند الرجوع لتسجيل الدخول
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
        
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

        // تفعيل المستمع عند عرض الـ WebView
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }

        // (موجود من قبل) منع النسخ (بالضغط المطول)
        webView.setLongClickable(false);
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true; 
            }
        });

        // (موجود من قبل) إعدادات الـ WebView
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

        // [ (موجود من قبل) منع فتح الروابط الخارجية ]
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith(BASE_APP_URL)) {
                    return false; // اسمح بالروابط الداخلية
                }
                return true; // امنع أي روابط خارجية (مثل يوتيوب)
            }
        });

        // إرسال الرابط
        String finalUrl = BASE_APP_URL + 
                          "?android_user_id=" + userId + 
                          "&android_device_id=" + deviceId;
        
        webView.loadUrl(finalUrl);
    }

    // (موجود من قبل) للتعامل مع زر الرجوع
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            // سيعود من (المشاهدة) إلى (الكورسات)
            webView.goBack();
        } else {
            if (webView.getVisibility() == View.VISIBLE) {
                // سيعود من (الكورسات) إلى (تسجيل الدخول)
                showLogin();
            } else {
                // سيخرج من التطبيق
                super.onBackPressed();
            }
        }
    }

    // إدارة المستمع عند إيقاف/استئناف التطبيق
    @Override
    protected void onStop() {
        super.onStop();
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            if (clipboardManager != null && clipboardListener != null) {
                clipboardManager.addPrimaryClipChangedListener(clipboardListener);
            }
        }
    }
}
