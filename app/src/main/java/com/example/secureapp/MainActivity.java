package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secureapp.network.LoginRequest;
import com.example.secureapp.network.LoginResponse;
import com.example.secureapp.network.RetrofitClient;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    public static final String APP_SECRET = "My_Sup3r_S3cr3t_K3y_For_Android_App_Only";
    private static final String PREFS_NAME = "SecureAppPrefs";
    private static final String PREF_USER_ID = "TelegramUserId";

    private View loginLayout;
    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView contactLink;
    private Button downloadsButton;
    private TextView registerLink; 

    private SharedPreferences prefs;
    private String deviceId;

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    
    // ✅ مكون التحميل المخصص (Overlay)
    private FrameLayout loadingOverlay;
    private ProgressBar loadingSpinner;

    @SuppressLint({"HardwareIds"}) 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            androidx.work.WorkManager.getInstance(this).cancelAllWork();
            androidx.work.WorkManager.getInstance(this).pruneWork();
        } catch (Exception e) { }

        if (!checkSecurityRequirements()) {
            return;
        }

        DownloadLogger.logAppStartInfo(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        
        // إعداد عنصر التحميل برمجياً
        setupLoadingOverlay();
        
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        loginLayout = findViewById(R.id.login_layout); 
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        contactLink = findViewById(R.id.contact_link); 
        downloadsButton = findViewById(R.id.downloads_button); 
        registerLink = findViewById(R.id.register_link); 

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        contactLink.setOnClickListener(v -> {
            String telegramId = "A7MeDWaLiD0";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + telegramId));
                startActivity(intent);
            } catch (Exception e) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Telegram User", "@" + telegramId);
                cm.setPrimaryClip(clip);
                showCustomToast("تم نسخ معرف المطور (@" + telegramId + ")", false);
            }
        });

        downloadsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DownloadsActivity.class);
            startActivity(intent);
        });
        
        // ✅ زر الانتقال للتسجيل
        if (registerLink != null) {
            registerLink.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            });
        }

        setupClipboardProtection();

        String savedUserId = prefs.getString(PREF_USER_ID, null);
        if (savedUserId != null && !savedUserId.isEmpty()) {
            openNativeHome();
        } else {
            showLogin();
        }
    }

    // ✅ إعداد طبقة التحميل (Overlay)
    private void setupLoadingOverlay() {
        ViewGroup root = findViewById(android.R.id.content);
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        loadingOverlay.setBackgroundColor(0x80000000); // خلفية نصف شفافة
        loadingOverlay.setClickable(true); // منع النقر
        loadingOverlay.setVisibility(View.GONE);

        loadingSpinner = new ProgressBar(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER;
        loadingOverlay.addView(loadingSpinner, params);

        root.addView(loadingOverlay);
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ✅ دالة Toast مخصصة وجميلة
    public void showCustomToast(String message, boolean isError) {
        Toast toast = Toast.makeText(this, (isError ? "⚠️ " : "✅ ") + message, Toast.LENGTH_LONG);
        toast.show();
    }

    // ✅ دالة عرض النوافذ الأنيقة (تم تصحيح الخطأ هنا)
    private void showStylishDialog(String title, String message, boolean isError) {
        // تم حذف الستايل المخصص الذي سبب الخطأ، وسنعتمد على الثيم الافتراضي للتطبيق
        new AlertDialog.Builder(this) 
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("موافق", null)
            .setIcon(isError ? android.R.drawable.ic_dialog_alert : android.R.drawable.ic_dialog_info)
            .show();
    }

    private void openNativeHome() {
        Intent intent = new Intent(MainActivity.this, NativeHomeActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean checkSecurityRequirements() {
        if (isDevOptionsEnabled()) {
            showStylishDialog("تنبيه أمني", "الرجاء إغلاق خيارات المطور (Developer Options) لضمان أمان التطبيق.", true);
            return false;
        }
        if (isDeviceRooted()) {
            FirebaseCrashlytics.getInstance().log("Security: Rooted Device Detected");
            FirebaseCrashlytics.getInstance().recordException(new SecurityException("Rooted Device Attempt"));
            showStylishDialog("جهاز غير آمن", "تم اكتشاف روت (Root) على هذا الجهاز. لا يمكن تشغيل التطبيق.", true);
            return false;
        }
        return true;
    }

    private boolean isDevOptionsEnabled() {
        int devOptions = 0;
        try {
            devOptions = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        } catch (Settings.SettingNotFoundException e) { return false; }
        return devOptions == 1;
    }

    private boolean isDeviceRooted() {
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) return true;
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su" };
        for (String path : paths) { if (new File(path).exists()) return true; }
        return false;
    }

    private void setupClipboardProtection() {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardListener = () -> {
            try {
                if (!clipboardManager.hasPrimaryClip()) return;
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return;
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null && (text.toString().contains("youtube.com") || text.toString().contains("youtu.be"))) {
                    clipboardManager.removePrimaryClipChangedListener(clipboardListener);
                    for (int i = 1; i <= 20; i++) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("flood" + i, "Item " + i));
                    }
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                    clipboardManager.addPrimaryClipChangedListener(clipboardListener);
                }
            } catch (Exception e) { 
                try { clipboardManager.addPrimaryClipChangedListener(clipboardListener); } catch (Exception re) {} 
            }
        };
    }

    private void showLogin() {
        loginLayout.setVisibility(View.VISIBLE);
        if (downloadsButton != null) downloadsButton.setVisibility(View.GONE);
        
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }

        loginButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                showCustomToast("الرجاء إدخال البيانات كاملة", true);
            } else {
                performLogin(username, password);
            }
        });
    }

    private void performLogin(String username, String password) {
        showLoading(true);

        RetrofitClient.getApi().login(new LoginRequest(username, password, deviceId))
            .enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    showLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginData = response.body();
                        if (loginData.success) {
                            prefs.edit()
                                .putString(PREF_USER_ID, loginData.userId)
                                .putString("FirstName", loginData.firstName)
                                .putBoolean("IsAdmin", loginData.isAdmin) 
                                .apply();
                            
                            showCustomToast("تم تسجيل الدخول بنجاح", false);
                            openNativeHome();
                        } else {
                            showStylishDialog("فشل الدخول", loginData.message, true);
                        }
                    } else if (response.code() == 403) {
                         showStylishDialog("تم الرفض", "هذا الحساب مربوط بجهاز آخر.\nلا يمكن الدخول إلا من الجهاز المسجل.", true);
                    } else if (response.code() == 401) {
                         showStylishDialog("خطأ في البيانات", "اسم المستخدم أو كلمة المرور غير صحيحة.", true);
                    } else {
                        showStylishDialog("خطأ خادم", "حدث خطأ غير متوقع: " + response.code(), true);
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    showLoading(false);
                    showStylishDialog("خطأ في الاتصال", "يرجى التحقق من الإنترنت والمحاولة مرة أخرى.", true);
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!checkSecurityRequirements()) return;
        if (clipboardManager != null) {
             clipboardManager.addPrimaryClipChangedListener(clipboardListener); 
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (clipboardManager != null && clipboardListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
    }
}
