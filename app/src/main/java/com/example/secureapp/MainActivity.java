package com.example.secureapp;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
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

    // ✅ الكود السري للتطبيق (يجب أن يطابق السيرفر)
    public static final String APP_SECRET = "My_Sup3r_S3cr3t_K3y_For_Android_App_Only";

    private static final String PREFS_NAME = "SecureAppPrefs";
    private static final String PREF_USER_ID = "TelegramUserId";

    private View loginLayout;
    
    // ✅ حقول الإدخال الجديدة
    private EditText usernameInput;
    private EditText passwordInput;
    
    private Button loginButton;
    private TextView contactLink;
    private Button downloadsButton;

    private SharedPreferences prefs;
    private String deviceId;

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;

    @SuppressLint({"HardwareIds"}) 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // تنظيف مهام الخلفية القديمة
        try {
            androidx.work.WorkManager.getInstance(this).cancelAllWork();
            androidx.work.WorkManager.getInstance(this).pruneWork();
        } catch (Exception e) { }

        // التحقق من متطلبات الأمان
        if (!checkSecurityRequirements()) {
            return;
        }

        DownloadLogger.logAppStartInfo(this);

        // منع تصوير الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        
        // الحصول على بصمة الجهاز
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // ربط العناصر
        loginLayout = findViewById(R.id.login_layout); 
        
        // ✅ ربط الحقول بالـ XML الجديد
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        
        loginButton = findViewById(R.id.login_button);
        contactLink = findViewById(R.id.contact_link); 
        downloadsButton = findViewById(R.id.downloads_button); 

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // رابط التواصل (محاولة فتح التطبيق مباشرة)
        contactLink.setOnClickListener(v -> {
            String telegramId = "A7MeDWaLiD0";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + telegramId));
                startActivity(intent);
            } catch (Exception e) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Telegram User", "@" + telegramId);
                cm.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "تم نسخ معرف المطور (@" + telegramId + ")", Toast.LENGTH_LONG).show();
            }
        });

        // زر التحميلات
        downloadsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DownloadsActivity.class);
            startActivity(intent);
        });

        setupClipboardProtection();

        // التحقق من حالة الدخول السابقة
        String savedUserId = prefs.getString(PREF_USER_ID, null);
        
        if (savedUserId != null && !savedUserId.isEmpty()) {
            openNativeHome();
        } else {
            showLogin();
        }
    }

    private void openNativeHome() {
        Intent intent = new Intent(MainActivity.this, NativeHomeActivity.class);
        startActivity(intent);
        finish();
    }

    // =============================================================
    // 🛡️ فحوصات الأمان (روت / خيارات مطور)
    // =============================================================

    private boolean checkSecurityRequirements() {
        if (isDevOptionsEnabled()) {
            showSecurityAlert("خيارات المطور مفعلة", "الرجاء إغلاق خيارات المطور (Developer Options) لضمان أمان التطبيق.");
            return false;
        }
        if (isDeviceRooted()) {
            FirebaseCrashlytics.getInstance().log("Security: Rooted Device Detected");
            FirebaseCrashlytics.getInstance().recordException(new SecurityException("Rooted Device Attempt"));
            showSecurityAlert("الجهاز غير آمن", "تم اكتشاف روت (Root) على هذا الجهاز. لا يمكن تشغيل التطبيق.");
            return false;
        }
        return true;
    }

    private void showSecurityAlert(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("إغلاق التطبيق", (dialog, which) -> {
                finishAffinity();
                System.exit(0);
            })
            .show();
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

    // --- حماية الحافظة (Clipboard) ---

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

    // =============================================================
    // 🔐 تسجيل الدخول
    // =============================================================

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
                Toast.makeText(MainActivity.this, "الرجاء إدخال اسم المستخدم وكلمة المرور", Toast.LENGTH_SHORT).show();
            } else {
                performLogin(username, password);
            }
        });
    }

    // ✅ دالة تسجيل الدخول الجديدة
    private void performLogin(String username, String password) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("جاري تسجيل الدخول...");
        dialog.setCancelable(false);
        dialog.show();

        // الاتصال بـ API تسجيل الدخول
        RetrofitClient.getApi().login(new LoginRequest(username, password, deviceId))
            .enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    dialog.dismiss();
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginData = response.body();
                        if (loginData.success) {
                            // ✅ تم الدخول وحفظ البيانات
                            prefs.edit()
                                .putString(PREF_USER_ID, loginData.userId)
                                .putString("FirstName", loginData.firstName)
                                .apply();
                            openNativeHome();
                        } else {
                            showErrorDialog("فشل الدخول", loginData.message);
                        }
                    } else if (response.code() == 403) {
                         showErrorDialog("تم الرفض", "هذا الحساب مربوط بجهاز آخر.\nلا يمكن الدخول إلا من الجهاز المسجل.");
                    } else if (response.code() == 401) {
                         showErrorDialog("خطأ", "اسم المستخدم أو كلمة المرور غير صحيحة.");
                    } else {
                        showErrorDialog("خطأ", "حدث خطأ في السيرفر: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    dialog.dismiss();
                    showErrorDialog("خطأ شبكة", "تأكد من اتصالك بالإنترنت وحاول مرة أخرى.");
                }
            });
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("موافق", null)
            .show();
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
