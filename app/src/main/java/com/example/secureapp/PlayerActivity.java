package com.example.secureapp;

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
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        watermarkText = findViewById(R.id.watermark_text);
        speedBtn = findViewById(R.id.speed_btn);

        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            // نبدأ الحركة فوراً
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

    // ✅✅ دالة الحركة الناعمة الجديدة (مطابقة للويب)
    private void startWatermarkAnimation() {
        final Random random = new Random();

        watermarkRunnable = new Runnable() {
            @Override
            public void run() {
                // ننتظر حتى يتم تحميل الواجهة لمعرفة الأبعاد
                if (watermarkText.getWidth() == 0 || playerView.getWidth() == 0) { 
                    watermarkHandler.postDelayed(this, 500);
                    return;
                }

                // 1. حساب الحدود المتاحة (عرض الشاشة - عرض النص) لضمان البقاء بالداخل
                int parentWidth = playerView.getWidth();
                int parentHeight = playerView.getHeight();
                
                float maxX = parentWidth - watermarkText.getWidth() - 20; // هامش أمان 20
                float maxY = parentHeight - watermarkText.getHeight() - 20;

                // منع القيم السالبة
                if (maxX < 0) maxX = 0;
                if (maxY < 0) maxY = 0;

                // 2. اختيار موقع عشوائي جديد
                float x = random.nextFloat() * maxX;
                float y = random.nextFloat() * maxY;

                // 3. ✅ التحريك بنعومة (Smooth Animation) بدلاً من القفز
                watermarkText.animate()
                        .x(x)
                        .y(y)
                        .setDuration(2000) // مدة الحركة 2 ثانية (مثل CSS ease-in-out)
                        .start();

                // 4. تكرار العملية كل 5 ثواني
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
