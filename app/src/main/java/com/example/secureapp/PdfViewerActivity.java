package com.example.secureapp;

import android.content.Context; // ✅
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager; // ✅ لكشف الشاشة
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display; // ✅
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PdfViewerActivity extends AppCompatActivity {

    private PDFView pdfView;
    private ProgressBar progressBar;
    private TextView watermark1, watermark2;
    private ImageButton btnBack;

    private String pdfUrl;
    private String pdfId;
    private String userId;
    private String localPath;

    // ✅ متغيرات كشف تصوير الشاشة
    private Handler screenCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable screenCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 حماية لقطة الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_pdf_viewer);

        // ✅ بدء مراقبة تصوير الشاشة فوراً
        startScreenRecordingMonitor();

        pdfUrl = getIntent().getStringExtra("PDF_URL");
        pdfId = getIntent().getStringExtra("PDF_ID");
        
        localPath = getIntent().getStringExtra("LOCAL_PATH");
        
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE);
        userId = prefs.getString("TelegramUserId", "User");

        initViews();
        setupWatermark();

        checkAndLoadPdf();
    }

    private void initViews() {
        pdfView = findViewById(R.id.pdfView);
        progressBar = findViewById(R.id.progressBar);
        watermark1 = findViewById(R.id.watermark_1);
        watermark2 = findViewById(R.id.watermark_2);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupWatermark() {
        watermark1.setText(userId);
        watermark2.setText(userId);
    }

    private void checkAndLoadPdf() {
        File file = getTargetFile();
        
        if (file.exists() && file.length() > 0) {
            loadEncryptedPdf(file);
        } else {
            if (pdfUrl == null || pdfUrl.isEmpty()) {
                Toast.makeText(this, "لا يوجد رابط للملف", Toast.LENGTH_SHORT).show();
                return;
            }
            downloadAndEncryptPdf(file);
        }
    }

    // ✅ الدالة الذكية لتحديد مكان الملف
    private File getTargetFile() {
        if (localPath != null) {
            // 1. إذا تم تمرير مسار محدد (من التحميلات)، نستخدمه
            return new File(localPath);
        }
        
        // 2. إذا لم يمرر مسار (أونلاين)، نستخدم الكاش المؤقت
        File cacheDir = new File(getCacheDir(), "pdf_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return new File(cacheDir, "temp_" + pdfId + ".enc");
    }

    private void downloadAndEncryptPdf(File targetFile) {
        progressBar.setVisibility(View.VISIBLE);
        
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE);
        String userId = prefs.getString("TelegramUserId", "");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(pdfUrl)
                .addHeader("x-user-id", userId)
                .addHeader("x-device-id", deviceId)
                .addHeader("x-app-secret", MainActivity.APP_SECRET)
                .build();

        client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(PdfViewerActivity.this, "فشل التحميل", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                                    targetFile,
                                    PdfViewerActivity.this,
                                    masterKeyAlias,
                                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                            ).build();

                            try (OutputStream os = encryptedFile.openFileOutput();
                                 InputStream is = response.body().byteStream()) {
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = is.read(buffer)) != -1) {
                                    os.write(buffer, 0, read);
                                }
                                os.flush();
                            }

                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                loadEncryptedPdf(targetFile);
                            });

                        } catch (Exception e) {
                            e.printStackTrace();
                            targetFile.delete();
                        }
                    }
                }
            });
    }

    private void loadEncryptedPdf(File encryptedFile) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encFile = new EncryptedFile.Builder(
                    encryptedFile,
                    this,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            InputStream is = encFile.openFileInput();

            pdfView.fromStream(is)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .enableAnnotationRendering(false)
                    .password(null)
                    .scrollHandle(null)
                    .enableAntialiasing(true)
                    .spacing(10)
                    .onLoad(nbPages -> progressBar.setVisibility(View.GONE))
                    .onError(t -> {
                        Toast.makeText(this, "الملف تالف أو المفتاح تغير", Toast.LENGTH_SHORT).show();
                        encryptedFile.delete();
                    })
                    .load();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في فتح الملف", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================
    // 🛡️ كشف تصوير الشاشة (Screen Recording Detection)
    // =========================================================
    private void startScreenRecordingMonitor() {
        screenCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScreenRecording()) {
                    handleScreenRecordingDetected();
                } else {
                    screenCheckHandler.postDelayed(this, 1000); // فحص كل ثانية
                }
            }
        };
        screenCheckHandler.post(screenCheckRunnable);
    }

    private boolean isScreenRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            for (Display display : dm.getDisplays()) {
                if (display.getFlags() == Display.FLAG_PRESENTATION) {
                    return true; // تم اكتشاف شاشة عرض خارجية (تصوير)
                }
            }
        }
        return false;
    }

    private void handleScreenRecordingDetected() {
        screenCheckHandler.removeCallbacks(screenCheckRunnable);
        
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                .setTitle("⛔ تنبيه أمني")
                .setMessage("تم اكتشاف برنامج لتصوير الشاشة!\n\nيمنع منعاً باتاً تصوير المحتوى. سيتم إغلاق العارض الآن.")
                .setCancelable(false)
                .setPositiveButton("إغلاق", (dialog, which) -> {
                    finish(); // إغلاق العارض
                })
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ إيقاف المراقبة عند الخروج
        screenCheckHandler.removeCallbacks(screenCheckRunnable);
    }
}
