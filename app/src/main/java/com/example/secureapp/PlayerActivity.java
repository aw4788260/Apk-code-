package com.example.secureapp;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

// استيرادات Media3 الأساسية
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

// [ ✅✅ استيرادات الحل الجذري ]
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.io.File;
import java.util.Random;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    private TextView speedBtn;
    
    private String videoPath;
    private String userWatermark;
    
    private Handler watermarkHandler = new Handler(Looper.getMainLooper());
    private Runnable watermarkRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // منع تصوير الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        watermarkText = findViewById(R.id.watermark_text);
        speedBtn = findViewById(R.id.speed_btn);

        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            startWatermarkAnimation();
        }
        
        speedBtn.setOnClickListener(v -> showSpeedDialog());

        initializePlayer();
    }

    private void initializePlayer() {
        if (videoPath == null) {
            Toast.makeText(this, "خطأ: المسار غير موجود", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // [ ✅✅ الحل الجذري: إعدادات متقدمة لاستخراج الوقت من ملفات TS ]
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(
                        // السماح بالبحث في إطارات غير رئيسية (مهم للـ Seek)
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES |
                        // إجبار المشغل على مسح الملف لحساب المدة (Fix Duration issue)
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                )
                // تفعيل البحث القائم على البت-ريت (يحل مشكلة عدم وجود فهرس زمني)
                .setConstantBitrateSeekingEnabled(true);

        // [ ✅✅ استخدام ProgressiveMediaSource ]
        // هذا النوع مخصص للملفات المحلية (File-based) ويجبر المشغل على التعامل معه كملف له نهاية
        // وليس كبث مباشر (Live Stream)
        ProgressiveMediaSource.Factory mediaSourceFactory =
                new ProgressiveMediaSource.Factory(
                        new DefaultDataSource.Factory(this), 
                        extractorsFactory
                );

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory) // ربط المصنع الخاص بنا
                .setSeekBackIncrementMs(10000)    // 10 ثواني تأخير
                .setSeekForwardIncrementMs(10000) // 10 ثواني تقديم
                .build();
        
        playerView.setPlayer(player);

        // تشغيل الملف
        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(new File(videoPath)));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "1.0x", "1.25x", "1.5x", "2.0x"};
        float[] values = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("سرعة التشغيل");
        builder.setItems(speeds, (dialog, which) -> {
            float speed = values[which];
            setPlaybackSpeed(speed);
        });
        builder.show();
    }

    private void setPlaybackSpeed(float speed) {
        if (player != null) {
            PlaybackParameters params = new PlaybackParameters(speed);
            player.setPlaybackParameters(params);
            speedBtn.setText(speed + "x");
        }
    }

    private void startWatermarkAnimation() {
        final Random random = new Random();
        watermarkRunnable = new Runnable() {
            @Override
            public void run() {
                if (watermarkText.getWidth() == 0 || playerView.getWidth() == 0) { 
                    watermarkHandler.postDelayed(this, 500);
                    return;
                }

                int parentWidth = playerView.getWidth();
                int parentHeight = playerView.getHeight();
                
                float minX = 0;
                float maxX = parentWidth - watermarkText.getWidth();
                float minY = 0;
                float maxY = parentHeight - watermarkText.getHeight();

                int orientation = getResources().getConfiguration().orientation;

                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // تقييد الحركة في الوضع العمودي لتكون فوق الفيديو فقط (تقريباً 16:9 في المنتصف)
                    float videoHeight = parentWidth * 9f / 16f;
                    float topMargin = (parentHeight - videoHeight) / 2f;

                    minY = topMargin;
                    maxY = topMargin + videoHeight - watermarkText.getHeight();
                    
                    if (minY < 0) minY = 0;
                    if (maxY > parentHeight - watermarkText.getHeight()) maxY = parentHeight - watermarkText.getHeight();
                    if (maxY < minY) maxY = minY;
                }

                if (maxX < minX) maxX = minX;

                float x = minX + random.nextFloat() * (maxX - minX);
                float y = minY + random.nextFloat() * (maxY - minY);

                watermarkText.animate()
                        .x(x)
                        .y(y)
                        .setDuration(2000)
                        .start();

                watermarkHandler.postDelayed(this, 5000);
            }
        };
        watermarkHandler.post(watermarkRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
        watermarkHandler.removeCallbacks(watermarkRunnable);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoPath != null) {
            try {
                new File(videoPath).delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
