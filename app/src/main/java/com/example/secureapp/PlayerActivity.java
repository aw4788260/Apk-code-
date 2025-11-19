package com.example.secureapp;

import android.content.DialogInterface;
import android.content.res.Configuration; // ✅ مهم للتحقق من الاتجاه
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.Random;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    private TextView speedBtn;
    private float currentSpeed = 1.0f; 

    private String videoPath;
    private String userWatermark;
    
    private Handler watermarkHandler = new Handler(Looper.getMainLooper());
    private Runnable watermarkRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ✅ 1. منع تصوير الشاشة داخل المشغل
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
            Toast.makeText(this, "خطأ: مسار الفيديو مفقود", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ ضبط التقديم والتأخير ليكون 10 ثواني
        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();
        
        playerView.setPlayer(player);

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
            currentSpeed = speed;
            speedBtn.setText(speed + "x");
        }
    }

    // ✅ دالة الحركة الذكية (المقيدة في العمودي، والحرة في الأفقي)
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
                
                // الحدود الافتراضية (كامل الشاشة)
                float minX = 0;
                float maxX = parentWidth - watermarkText.getWidth();
                float minY = 0;
                float maxY = parentHeight - watermarkText.getHeight();

                // ✅ التحقق من الاتجاه (عمودي أم أفقي)
                int orientation = getResources().getConfiguration().orientation;

                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // حساب أبعاد الفيديو (نسبة 16:9 تقريباً) لتقييد الحركة داخله
                    float videoHeight = parentWidth * 9f / 16f;
                    
                    // حساب الهامش العلوي لأن الفيديو يكون في المنتصف
                    float topMargin = (parentHeight - videoHeight) / 2f;

                    // تقييد الحركة الرأسية لتكون داخل منطقة الفيديو فقط
                    minY = topMargin;
                    maxY = topMargin + videoHeight - watermarkText.getHeight();
                    
                    // تصحيح القيم إذا خرجت عن الحدود المنطقية
                    if (minY < 0) minY = 0;
                    if (maxY > parentHeight - watermarkText.getHeight()) maxY = parentHeight - watermarkText.getHeight();
                    if (maxY < minY) maxY = minY; // حماية إضافية
                }

                // تصحيح القيم الأفقية
                if (maxX < minX) maxX = minX;

                // اختيار موقع عشوائي داخل الحدود المحسوبة
                float x = minX + random.nextFloat() * (maxX - minX);
                float y = minY + random.nextFloat() * (maxY - minY);

                // التحريك بنعومة (Smooth Animation)
                watermarkText.animate()
                        .x(x)
                        .y(y)
                        .setDuration(2000)
                        .start();

                // تكرار كل 5 ثواني
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
        // حذف الملف المؤقت عند الخروج
        if (videoPath != null) {
            try {
                new File(videoPath).delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
