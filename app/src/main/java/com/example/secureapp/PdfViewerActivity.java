package com.example.secureapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private String localPath; // ✅ متغير جديد للمسار

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // حماية لقطة الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_pdf_viewer);

        pdfUrl = getIntent().getStringExtra("PDF_URL");
        pdfId = getIntent().getStringExtra("PDF_ID");
        
        // ✅ استقبال المسار المحلي (إن وجد)
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
        // هذا يمنع "التحميل التلقائي" للمكتبة الدائمة
        File cacheDir = new File(getCacheDir(), "pdf_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return new File(cacheDir, "temp_" + pdfId + ".enc");
    }

    private void downloadAndEncryptPdf(File targetFile) {
        progressBar.setVisibility(View.VISIBLE);
        
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(pdfUrl)
                .addHeader("x-app-secret", "My_Sup3r_S3cr3t_K3y_For_Android_App_Only")
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
                    .swipeHorizontal(false) // تمرير عمودي (Scroll)
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
}
