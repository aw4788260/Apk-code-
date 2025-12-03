package com.example.secureapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 1. منع لقطة الشاشة (Screen Shot / Screen Record)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_pdf_viewer);

        // استقبال البيانات
        pdfUrl = getIntent().getStringExtra("PDF_URL");
        pdfId = getIntent().getStringExtra("PDF_ID");
        
        SharedPreferences prefs = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE);
        userId = prefs.getString("TelegramUserId", "User");

        initViews();
        setupWatermark();

        // 🚀 بدء التحقق والتحميل
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
        // تعيين نص العلامة المائية (ID المستخدم)
        watermark1.setText(userId);
        watermark2.setText(userId);
    }

    // =========================================================
    // 📂 منطق التخزين الآمن والتحميل
    // =========================================================

    private void checkAndLoadPdf() {
        File file = getSecureFile();
        
        if (file.exists() && file.length() > 0) {
            // ✅ الملف موجود: فك التشفير والعرض
            loadEncryptedPdf(file);
        } else {
            // ⬇️ الملف غير موجود: تحميل -> تشفير -> عرض
            downloadAndEncryptPdf(file);
        }
    }

    private File getSecureFile() {
        // ✅ استخدام getFilesDir() يضمن التخزين الداخلي المحمي
        // لا يمكن لأي تطبيق آخر أو للمستخدم الوصول لهذا المسار
        File dir = new File(getFilesDir(), "secure_pdfs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "doc_" + pdfId + ".enc");
    }

    private void downloadAndEncryptPdf(File targetFile) {
        progressBar.setVisibility(View.VISIBLE);
        
        OkHttpClient client = new OkHttpClient();
        
        // إضافة الهيدر السري للتحميل (للحماية من السرقة)
        Request request = new Request.Builder()
                .url(pdfUrl)
                .addHeader("x-app-secret", "My_Sup3r_S3cr3t_K3y_For_Android_App_Only") // تأكد من تطابقه مع السيرفر
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(PdfViewerActivity.this, "فشل التحميل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        // 🔐 تشفير الملف وحفظه
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
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                            os.flush();
                        }

                        // ✅ العرض بعد الحفظ الناجح
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            loadEncryptedPdf(targetFile);
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        targetFile.delete(); // حذف الملف التالف
                        runOnUiThread(() -> 
                            Toast.makeText(PdfViewerActivity.this, "خطأ في الحفظ الآمن", Toast.LENGTH_SHORT).show()
                        );
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(PdfViewerActivity.this, "خطأ من السيرفر: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void loadEncryptedPdf(File encryptedFile) {
        try {
            // 🔓 فك التشفير "أثناء العرض" (Stream) دون حفظ نسخة مفكوكة
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encFile = new EncryptedFile.Builder(
                    encryptedFile,
                    this,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            InputStream is = encFile.openFileInput();

            pdfView.fromStream(is)
                    .enableSwipe(true) // تفعيل السحب
                    .swipeHorizontal(false) // تمرير عمودي
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .enableAnnotationRendering(false)
                    .password(null)
                    .scrollHandle(null)
                    .enableAntialiasing(true)
                    .spacing(10) // مسافة بين الصفحات
                    .onLoad(nbPages -> progressBar.setVisibility(View.GONE))
                    .onError(t -> {
                        Toast.makeText(this, "ملف تالف", Toast.LENGTH_SHORT).show();
                        encryptedFile.delete(); // حذف الملف التالف لإعادة تحميله المرة القادمة
                    })
                    .load();

        } catch (Exception e) {
            Toast.makeText(this, "خطأ في فتح الملف: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            encryptedFile.delete(); // في حال تغير مفتاح التشفير أو تلف الملف
        }
    }
}
