package com.school.ai.terminal;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 - 未来科普终端 (UI演示版)
 * 此版本不依赖实际SDK，用于预览界面和交互
 */
public class MainActivity extends AppCompatActivity {

    private static final int ADMIN_CLICK_THRESHOLD = 5;
    private static final int ADMIN_CLICK_TIMEOUT = 2000;

    private ConfigManager config;
    private Handler handler = new Handler();
    
    private int adminClickCount = 0;

    private ImageView ivBackground;
    private TextView tvThemeTitle;
    private TextView tvAiResponse;
    private TextView tvUserInput;
    private WaveformView waveView;
    private View viewAdminTrigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ConfigManager(this);
        
        initViews();
        setupKioskMode();
        loadConfiguration();
        setupAdminTrigger();
        
        // 模拟启动效果
        simulateStartup();
    }

    private void initViews() {
        ivBackground = findViewById(R.id.iv_background);
        tvThemeTitle = findViewById(R.id.tv_theme_title);
        tvAiResponse = findViewById(R.id.tv_ai_response);
        tvUserInput = findViewById(R.id.tv_user_input);
        waveView = findViewById(R.id.wave_view);
        viewAdminTrigger = findViewById(R.id.view_admin_trigger);
    }

    private void setupKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void loadConfiguration() {
        tvThemeTitle.setText(config.getThemeName());
        
        String bgUri = config.getBgUri();
        if (bgUri != null && !bgUri.isEmpty()) {
            Glide.with(this).load(Uri.parse(bgUri)).centerCrop().into(ivBackground);
        }

        if (config.getAgentId().isEmpty()) {
            tvAiResponse.setText("设备未配置。\n请点击左上角5次进入设置。");
        }
    }

    private void setupAdminTrigger() {
        viewAdminTrigger.setOnClickListener(v -> {
            adminClickCount++;
            tvUserInput.setText((ADMIN_CLICK_THRESHOLD - adminClickCount) + "次");
            handler.removeCallbacksAndMessages(null);
            
            if (adminClickCount >= ADMIN_CLICK_THRESHOLD) {
                adminClickCount = 0;
                tvUserInput.setText("");
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                handler.postDelayed(() -> {
                    adminClickCount = 0;
                    tvUserInput.setText("");
                }, ADMIN_CLICK_TIMEOUT);
            }
        });
    }

    /**
     * 模拟启动动画
     */
    private void simulateStartup() {
        tvAiResponse.setText("正在启动...");
        
        new Handler().postDelayed(() -> {
            tvAiResponse.setText("系统就绪");
            waveView.setListening(true);
            
            new Handler().postDelayed(() -> {
                waveView.setListening(false);
                tvAiResponse.setText("待机中...\n请说话或点击左上角5次进入设置");
            }, 1500);
        }, 1000);
    }

    /**
     * 模拟语音识别结果
     */
    private void simulateVoiceInput(String text) {
        tvUserInput.setText(text);
        
        new Handler().postDelayed(() -> {
            tvAiResponse.setText("正在思考...");
            waveView.setSpeaking(true);
            
            new Handler().postDelayed(() -> {
                waveView.setSpeaking(false);
                tvAiResponse.setText("这是模拟的AI回答。\n\n在实际设备上，这里会显示\n阿里云灵芯SDK返回的回复。");
            }, 2000);
        }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfiguration();
    }

    @Override
    public void onBackPressed() {
        // 屏蔽返回键
    }
}
