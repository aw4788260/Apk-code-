package com.example.secureapp;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

// ✅ استيرادات ExoPlayer (Media3)
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters; // للتحكم في السرعة
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.Random;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    
    // ✅ متغير زر السرعة
    private TextView speedBtn;
    private float currentSpeed = 1.0f; // السرعة الافتراضية

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
        speedBtn = findViewById(R.id.speed_btn); // ✅ ربط الزر

        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            startWatermarkAnimation();
        }
        
        // ✅ تفعيل زر السرعة
        speedBtn.setOnClickListener(v -> showSpeedDialog());

        initializePlayer();
    }

    private void initializePlayer() {
        if (videoPath == null) {
            Toast.makeText(this, "خطأ: مسار الفيديو مفقود", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(new File(videoPath)));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    // ✅✅ دالة إظهار قائمة السرعات
    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "1.0x", "1.25x", "1.5x", "2.0x"};
        float[] values = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("اختر سرعة التشغيل");
        builder.setItems(speeds, (dialog, which) -> {
            float speed = values[which];
            setPlaybackSpeed(speed);
        });
        builder.show();
    }

    // ✅✅ دالة تطبيق السرعة
    private void setPlaybackSpeed(float speed) {
        if (player != null) {
            PlaybackParameters params = new PlaybackParameters(speed);
            player.setPlaybackParameters(params);
            currentSpeed = speed;
            speedBtn.setText(speed + "x"); // تحديث نص الزر
        }
    }

    private void startWatermarkAnimation() {
        final Random random = new Random();
        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;

        watermarkRunnable = new Runnable() {
            @Override
            public void run() {
                if (watermarkText.getWidth() == 0) { 
                    watermarkHandler.postDelayed(this, 500);
                    return;
                }

                float x = random.nextFloat() * (screenWidth - watermarkText.getWidth());
                float y = random.nextFloat() * (screenHeight - watermarkText.getHeight());
                
                watermarkText.setX(x);
                watermarkText.setY(y);

                watermarkHandler.postDelayed(this, 4000);
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
