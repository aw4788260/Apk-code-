package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.Random;

// هذه العلامة ضرورية لأن بعض دوال ExoPlayer لا تزال تجريبية (Unstable)
@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    private TextView speedBtn;
    private TextView speedOverlay;

    private String videoPath; // يمكن أن يكون رابط URL أو مسار ملف
    private String userWatermark;

    private Handler watermarkHandler = new Handler(Looper.getMainLooper());
    private Runnable watermarkRunnable;
    
    private boolean isSpeedingUp = false;
    private Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                isSpeedingUp = true;
                player.setPlaybackParameters(new PlaybackParameters(2.0f));
                if (speedOverlay != null) speedOverlay.setVisibility(View.VISIBLE);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 حماية: منع تصوير الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        // إبقاء الشاشة مضاءة
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        watermarkText = findViewById(R.id.watermark_text);
        speedBtn = findViewById(R.id.speed_btn);
        speedOverlay = findViewById(R.id.speed_overlay);

        // استقبال البيانات
        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");
        
        // التحقق من وجود الرابط
        if (videoPath == null || videoPath.isEmpty()) {
            Toast.makeText(this, "رابط الفيديو مفقود!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            startWatermarkAnimation();
        }
        
        speedBtn.setOnClickListener(v -> showSpeedDialog());

        // ميزة الضغط المطول لتسريع الفيديو
        playerView.setOnTouchListener((v, event) -> {
            if (player == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.postDelayed(longPressRunnable, 300); 
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPressRunnable);
                    if (isSpeedingUp) {
                        player.setPlaybackParameters(new PlaybackParameters(1.0f));
                        if (speedOverlay != null) speedOverlay.setVisibility(View.GONE);
                        isSpeedingUp = false;
                    } else {
                        // إظهار/إخفاء التحكم عند النقرة العادية
                        if (playerView.isControllerFullyVisible()) playerView.hideController();
                        else playerView.showController();
                    }
                    return true;
            }
            return false;
        });

        initializePlayer();
    }

    private void initializePlayer() {
        // 1. تحديد نوع المصدر (أونلاين أم ملف محلي)
        Uri videoUri;
        if (videoPath.startsWith("http") || videoPath.startsWith("https")) {
            // رابط إنترنت
            videoUri = Uri.parse(videoPath);
        } else {
            // ملف محلي
            videoUri = Uri.fromFile(new File(videoPath));
        }

        // 2. إعداد مصدر الوسائط (يدعم HLS, DASH, Progressive تلقائياً)
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        
        // استخدام DefaultMediaSourceFactory هو الأفضل للدعم الشامل
        MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUri));

        // 3. بناء المشغل
        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();
        
        playerView.setPlayer(player);
        
        // تفعيل أزرار التحكم
        playerView.setShowFastForwardButton(true);
        playerView.setShowRewindButton(true);
        playerView.setControllerShowTimeoutMs(4000); 
        
        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
        
        // التعامل مع الأخطاء (مثل انقطاع النت)
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "حدث خطأ في التشغيل: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "1.0x", "1.25x", "1.5x", "2.0x"};
        float[] values = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
        new AlertDialog.Builder(this)
                .setTitle("سرعة التشغيل")
                .setItems(speeds, (dialog, which) -> {
                    if (player != null) {
                        player.setPlaybackParameters(new PlaybackParameters(values[which]));
                        speedBtn.setText(values[which] + "x");
                    }
                }).show();
    }

    private void startWatermarkAnimation() {
        final Random random = new Random();
        watermarkRunnable = new Runnable() {
            @Override
            public void run() {
                if (watermarkText.getWidth() == 0 || playerView.getWidth() == 0) { 
                    watermarkHandler.postDelayed(this, 500); return;
                }
                int pW = playerView.getWidth(); int pH = playerView.getHeight();
                float maxY = pH - watermarkText.getHeight();
                float minY = 0;
                
                // تعديل الحدود في الوضع الرأسي لتجنب خروج العلامة عن الفيديو الفعلي
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    float videoH = pW * 9f / 16f;
                    float top = (pH - videoH) / 2f;
                    minY = top; 
                    maxY = top + videoH - watermarkText.getHeight();
                }
                
                float x = random.nextFloat() * (pW - watermarkText.getWidth());
                float y = minY + random.nextFloat() * (Math.max(0, maxY - minY));
                
                watermarkText.animate().x(x).y(y).setDuration(2000).start();
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
        // ⚠️ تم إزالة كود الحذف التلقائي للملف لضمان عدم حذف الملفات المحملة أو الروابط
    }
}
