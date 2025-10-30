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
import android.content.ClipboardManager;
import android.content.ClipData;

// إضافات ملء الشاشة
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.content.pm.ActivityInfo;

// إضافات معالجة الأخطاء
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;

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

    // متغيرات ملء الشاشة
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

        // (كود التحقق من تسجيل الدخول - يبقى كما هو)
        // إذا لم يسجل دخوله من قبل، ستظهر صفحة تسجيل الدخول
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

        webView.setWebChromeClient(new MyWebChromeClient());

        // (كود معالجة فصل الإنترنت كما هو)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith(BASE_APP_URL)) {
                    return false;
                }
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
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

        String finalUrl = BASE_APP_URL +
                          "?android_user_id=" + userId +
                          "&android_device_id=" + deviceId;

        webView.loadUrl(finalUrl);
    }

    // (كلاس ملء الشاشة كما هو - مع إصلاح الشاشة البيضاء)
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
            
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            customViewCallback = null;
        }
    }
    
    // --- [ ✅✅✅ هذا هو الإصلاح ✅✅✅ ] ---
    @Override
    public void onBackPressed() {
        if (customView != null) {
            // 1. إذا كان في وضع ملء الشاشة، قم بالخروج منه
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
        } 
        else if (webView.canGoBack()) {
            // 2. إذا كان داخل صفحة (مثل صفحة المشاهدة)، ارجع لصفحة الكورسات
            webView.goBack();
        } 
        else if (webView.getVisibility() == View.VISIBLE) {
            // 3. [تم التعديل] إذا كان في صفحة الكورسات الرئيسية، اخرج من التطبيق
            super.onBackPressed(); // <-- (كانت showLogin())
        } 
        else {
            // 4. إذا كان في صفحة تسجيل الدخول (لأي سبب)، اخرج من التطبيق
            super.onBackPressed();
        }
    }
    // --- [ نهاية الإصلاح ] ---

    
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
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            if (clipboardManager != null && clipboardListener != null) {
                clipboardManager.addPrimaryClipChangedListener(clipboardListener);
            }
        }
    }
}
