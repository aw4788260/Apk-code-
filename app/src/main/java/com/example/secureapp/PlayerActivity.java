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
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C; // ✅ هام لاختيار المسارات
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder; // ✅ مكتبة اختيار المسارات

import com.example.secureapp.network.VideoApiResponse;
import com.google.gson.Gson;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView watermarkText;
    private ImageButton settingsBtn; // الزر المدمج
    private TextView speedOverlay;

    private String videoPath;
    private String userWatermark;
    private List<VideoApiResponse.QualityOption> qualityList;
    
    // متغيرات لحفظ الحالة للعرض
    private String currentQualityLabel = "تلقائي"; 
    private String currentSpeedLabel = "1.0x";

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
        speedOverlay = findViewById(R.id.speed_overlay);
        
        // ✅ البحث عن الزر داخل PlayerView لأنه جزء من التصميم المخصص
        settingsBtn = playerView.findViewById(R.id.settings_btn);

        videoPath = getIntent().getStringExtra("VIDEO_PATH");
        userWatermark = getIntent().getStringExtra("WATERMARK_TEXT");
        
        String qualitiesJson = getIntent().getStringExtra("QUALITIES_JSON");
        if (qualitiesJson != null) {
            try {
                VideoApiResponse.QualityOption[] optionsArray = new Gson().fromJson(qualitiesJson, VideoApiResponse.QualityOption[].class);
                if (optionsArray != null) {
                    qualityList = Arrays.asList(optionsArray);
                    if(!qualityList.isEmpty()) currentQualityLabel = qualityList.get(0).quality + "p";
                }
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            currentQualityLabel = "ملف محلي";
        }

        if (videoPath == null || videoPath.isEmpty()) {
            finish();
            return;
        }

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            startWatermarkAnimation();
        }
        
        // ✅ تفعيل زر الإعدادات الموحد
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> showMainMenu());
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

    // ✅ القائمة الرئيسية الموحدة (داخل الترس)
    private void showMainMenu() {
        boolean hasQualityOptions = (qualityList != null && !qualityList.isEmpty());
        
        String[] options = {
            "📺 الجودة (" + currentQualityLabel + ")",
            "⚡ سرعة التشغيل (" + currentSpeedLabel + ")",
            "🔊 مسارات الصوت (Audio)"
        };

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) { // الجودة
                        if (hasQualityOptions) showQualityDialog();
                        else Toast.makeText(this, "غير متاح لهذا الفيديو", Toast.LENGTH_SHORT).show();
                    } 
                    else if (which == 1) { // السرعة
                        showSpeedDialog();
                    } 
                    else if (which == 2) { // الصوت
                        showAudioTrackSelection();
                    }
                })
                .show();
    }

    // عرض نافذة اختيار مسار الصوت
    private void showAudioTrackSelection() {
        if (player == null) return;
        TrackSelectionDialogBuilder trackSelectionDialogBuilder = 
                new TrackSelectionDialogBuilder(this, "اختر الصوت", player, C.TRACK_TYPE_AUDIO);
        trackSelectionDialogBuilder.setAllowAdaptiveSelections(false);
        trackSelectionDialogBuilder.build().show();
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
                    VideoApiResponse.QualityOption selected = qualityList.get(which);
                    currentQualityLabel = selected.quality + "p";
                    changeQuality(selected.url);
                })
                .show();
    }

    private void changeQuality(String newUrl) {
        if (player != null) {
            long currentPos = player.getCurrentPosition();
            boolean isPlaying = player.isPlaying();
            float currentSpeed = player.getPlaybackParameters().speed;

            Uri videoUri = Uri.parse(newUrl);
            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));
            
            player.setMediaSource(mediaSource);
            player.prepare();
            player.seekTo(currentPos);
            player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
            if (isPlaying) player.play();
            
            Toast.makeText(this, "تم التحويل إلى " + currentQualityLabel, Toast.LENGTH_SHORT).show();
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
                        currentSpeedLabel = speeds[which];
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
