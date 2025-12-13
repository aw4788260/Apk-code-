package com.example.secureapp;

import android.annotation.SuppressLint;
import android.content.Context; // ✅
import android.content.res.Configuration;
import android.hardware.display.DisplayManager; // ✅ لكشف الشاشة
import android.media.AudioManager; // ✅ لمنع تسجيل الصوت
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display; // ✅
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder; 

import com.example.secureapp.network.VideoApiResponse;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
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
    private TextView speedOverlay;

    private String videoPath;
    private String userWatermark;
    private List<VideoApiResponse.QualityOption> qualityList;
    
    private String currentQualityLabel = "تلقائي"; 
    private String currentSpeedLabel = "1.0x";

    private boolean playWhenReady = true;
    private int currentItem = 0;
    private long playbackPosition = 0;

    private Handler watermarkHandler = new Handler(Looper.getMainLooper());
    private Runnable watermarkRunnable;
    
    // ✅ متغيرات كشف تصوير الشاشة
    private Handler screenCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable screenCheckRunnable;
    
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
        
        // 1. حماية قصوى
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 2. الحماية الصوتية (تصحيح الخطأ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // ✅ استخدام الرقم 3 بدلاً من الثابت
                audioManager.setAllowedCapturePolicy(3);
            }
        }

        setContentView(R.layout.activity_player);

        // ✅ بدء مراقبة تصوير الشاشة فوراً
        startScreenRecordingMonitor();

        playerView = findViewById(R.id.player_view);
        watermarkText = findViewById(R.id.watermark_text);
        speedOverlay = findViewById(R.id.speed_overlay);

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
            Toast.makeText(this, "رابط الفيديو مفقود!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (userWatermark != null) {
            watermarkText.setText(userWatermark);
            startWatermarkAnimation();
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
                        if (player != null) player.setPlaybackParameters(new PlaybackParameters(1.0f));
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
    }

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this)
                    .setSeekBackIncrementMs(10000)
                    .setSeekForwardIncrementMs(10000)
                    .build();
            playerView.setPlayer(player);
            
            playerView.setControllerShowTimeoutMs(4000); 
            
            View settingsButton = playerView.findViewById(androidx.media3.ui.R.id.exo_settings);
            if (settingsButton != null) {
                settingsButton.setVisibility(View.VISIBLE);
                settingsButton.setOnClickListener(v -> showMainMenu());
            }

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    FirebaseCrashlytics.getInstance().recordException(error);
                    Toast.makeText(PlayerActivity.this, "حدث خطأ: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            MediaSource mediaSource;
            if (videoPath.startsWith("http") || videoPath.startsWith("https")) {
                Uri videoUri = Uri.parse(videoPath);
                DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
                mediaSource = new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));
            } else {
                File encryptedFile = new File(videoPath);
                EncryptedFileDataSourceFactory secureFactory = new EncryptedFileDataSourceFactory(this, encryptedFile);
                mediaSource = new ProgressiveMediaSource.Factory(secureFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.fromFile(encryptedFile)));
            }

            player.setMediaSource(mediaSource);
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentItem, playbackPosition);
            player.prepare();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentItem = player.getCurrentMediaItemIndex();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            for (Display display : dm.getDisplays()) {
                if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleScreenRecordingDetected() {
        // إيقاف الفيديو فوراً ومسح المحتوى
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }

        screenCheckHandler.removeCallbacks(screenCheckRunnable);
        
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                .setTitle("⛔ تنبيه أمني")
                .setMessage("تم اكتشاف برنامج لتصوير الشاشة!\n\nيمنع منعاً باتاً تصوير المحتوى. سيتم إغلاق المشغل الآن.")
                .setCancelable(false)
                .setPositiveButton("إغلاق", (dialog, which) -> {
                    finish(); // إغلاق المشغل
                })
                .show();
        }
    }

    // =========================================================
    // ✅ دورة حياة النشاط (Lifecycle)
    // =========================================================

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayer();
        }
        watermarkHandler.removeCallbacks(watermarkRunnable); 
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ إيقاف المراقبة عند الخروج
        screenCheckHandler.removeCallbacks(screenCheckRunnable);
    }

    // =========================================================

    private void showMainMenu() {
        boolean hasQualityOptions = (qualityList != null && !qualityList.isEmpty());
        
        String[] options = {
            "الجودة (" + currentQualityLabel + ")",
            "سرعة التشغيل (" + currentSpeedLabel + ")",
            "مسارات الصوت (Audio)"
        };

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (hasQualityOptions) showQualityDialog();
                        else Toast.makeText(this, "تغيير الجودة غير متاح", Toast.LENGTH_SHORT).show();
                    } 
                    else if (which == 1) showSpeedDialog();
                    else if (which == 2) showAudioTrackSelection();
                })
                .show();
    }

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
        for (int i = 0; i < qualityList.size(); i++) items[i] = qualityList.get(i).quality + "p";

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
            // ✅ حفظ المكان الحالي قبل تغيير الجودة
            long currentPos = player.getCurrentPosition();
            boolean isPlaying = player.isPlaying();
            float currentSpeed = player.getPlaybackParameters().speed;

            // تحديث الرابط (لإعادة التحميل عند العودة أيضا)
            videoPath = newUrl; 

            Uri videoUri = Uri.parse(newUrl);
            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));
            
            player.setMediaSource(mediaSource);
            player.prepare();
            player.seekTo(currentPos);
            player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
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
}
