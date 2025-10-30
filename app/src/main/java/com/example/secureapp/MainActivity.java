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
import android.webkit.WebChromeClient; // <-- تأكد من وجود هذا
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

// --- [ ✅ الإضافات المطلوبة لملء الشاشة ] ---
import android.widget.FrameLayout; // <-- إضافة FrameLayout
import android.view.ViewGroup; // <-- إضافة ViewGroup
import android.content.pm.ActivityInfo; // <-- ✅✅ [تم إصلاح الخطأ المطبعي هنا] ✅✅

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

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;

    // --- [ متغيرات جديدة لملء الشاشة ] ---
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @SuppressLint({"HardwareIds", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // --- [ ربط حاوية ملء الشاشة ] ---
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        
        webView = findViewById(R.id.webView);
        loginLayout = findViewById(R.id.login_layout);
        userIdInput = findViewById(R.id.telegram_id_input);
        loginButton = findViewById(R.id.login_button);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // (كود الحافظة كما هو)
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

    private void showLogin() {
        loginLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String userId) {
        loginLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }

        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);

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

        // --- [ الإصلاح الرئيسي لملء الشاشة ] ---
        webView.setWebChromeClient(new MyWebChromeClient());
        // --- [ نهاية الإصلاح ] ---

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith(BASE_APP_URL)) {
                    return false;
                }
                return true;
            }
        });

        String finalUrl = BASE_APP_URL +
                          "?android_user_id=" + userId +
                          "&android_device_id=" + deviceId;

        webView.loadUrl(finalUrl);
    }

    // --- [ كلاس جديد للتعامل مع ملء الشاشة ] ---
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
            fullscreenContainer.setVisibility(View.VISIBLE);
            fullscreenContainer.addView(customView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            // فرض الوضع الأفقي
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
            fullscreenContainer.setVisibility(View.GONE);
            
            webView.setVisibility(View.VISIBLE);
            
            // العودة للوضع الرأسي
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            customViewCallback = null;
        }
    }
    
    // --- [ تعديل زر الرجوع ] ---
    @Override
    public void onBackPressed() {
        if (customView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } 
        else if (webView.canGoBack()) {
            webView.goBack();
        } 
        else if (webView.getVisibility() == View.VISIBLE) {
            showLogin();
        } 
        else {
            super.onBackPressed();
        }
    }

    // (باقي دوال onStop و onResume كما هي)
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
