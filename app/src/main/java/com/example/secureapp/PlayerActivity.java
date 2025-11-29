package com.example.secureapp;

import android.annotation.SuppressLint;
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

import com.example.secureapp.network.VideoApiResponse;
import com.google.gson.Gson;
// (تم حذف استيراد TypeToken لتجنب المشكلة)

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays; // ✅ إضافة Arrays
import java.util.List;
import java.util.Random;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    private TextView speedBtn;
    private TextView qualityBtn;
    private TextView speedOverlay;

    private String videoPath;
    private String userWatermark;
    
    private List<VideoApiResponse.QualityOption> qualityList;

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
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        watermarkText = findViewById(R.id.watermark_text);
        speedBtn = findViewById(R.id.speed_btn);
        qualityBtn = findViewById(R.id.quality_btn);
        speedOverlay = findViewById(R.id.speed_overlay);

        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");
        
        // ✅ [التصحيح هنا] استخدام المصفوفة بدلاً من TypeToken لتفادي الكراش
        String qualitiesJson = getIntent().getStringExtra("QUALITIES_JSON");
        if (qualitiesJson != null) {
            try {
                VideoApiResponse.QualityOption[] optionsArray = new Gson().fromJson(qualitiesJson, VideoApiResponse.QualityOption[].class);
                if (optionsArray != null) {
                    qualityList = Arrays.asList(optionsArray);
                }
            } catch (Exception e) {
                e.printStackTrace(); // تجاهل الخطأ إذا حدث أثناء التحويل
            }
        }

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
        
        if (qualityList != null && !qualityList.isEmpty()) {
            qualityBtn.setVisibility(View.VISIBLE);
            qualityBtn.setOnClickListener(v -> showQualityDialog());
        } else {
            qualityBtn.setVisibility(View.GONE);
        }

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
                        if (playerView.isControllerFullyVisible()) playerView.hideController();
                        else playerView.showController();
                    }
                    return true;
            }
            return false;
        });

        initializePlayer(videoPath, 0); 
    }

    private void initializePlayer(String url, long startPosition) {
        if (player == null) {
            player = new ExoPlayer.Builder(this)
                    .setSeekBackIncrementMs(10000)
                    .setSeekForwardIncrementMs(10000)
                    .build();
            playerView.setPlayer(player);
            
            playerView.setShowFastForwardButton(true);
            playerView.setShowRewindButton(true);
            playerView.setControllerShowTimeoutMs(4000); 
            
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Toast.makeText(PlayerActivity.this, "حدث خطأ: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        Uri videoUri;
        if (url.startsWith("http") || url.startsWith("https")) {
            videoUri = Uri.parse(url);
        } else {
            videoUri = Uri.fromFile(new File(url));
        }

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));

        player.setMediaSource(mediaSource);
        player.prepare();
        if (startPosition > 0) {
            player.seekTo(startPosition);
        }
        player.play();
    }

    private void showQualityDialog() {
        if (qualityList == null) return;

        String[] items = new String[qualityList.size()];
        for (int i = 0; i < qualityList.size(); i++) {
            items[i] = qualityList.get(i).quality + "p";
        }

        new AlertDialog.Builder(this)
                .setTitle("اختر الجودة")
                .setItems(items, (dialog, which) -> {
                    changeQuality(qualityList.get(which).url);
                })
                .show();
    }

    private void changeQuality(String newUrl) {
        if (player != null) {
            long currentPos = player.getCurrentPosition();
            boolean isPlaying = player.isPlaying();
            
            Uri videoUri = Uri.parse(newUrl);
            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));
            
            player.setMediaSource(mediaSource);
            player.prepare();
            player.seekTo(currentPos);
            if (isPlaying) player.play();
            
            Toast.makeText(this, "تم تغيير الجودة", Toast.LENGTH_SHORT).show();
        }
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
    }
}
