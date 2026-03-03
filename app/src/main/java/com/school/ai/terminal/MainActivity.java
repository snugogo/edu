package com.school.ai.terminal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.util.Objects;

/**
 * 主界面 - 未来科普终端
 * 集成阿里云灵芯 SDK
 * 
 * 使用说明:
 * 1. 将阿里云灵芯 SDK (.aar) 放入 app/libs 文件夹
 * 2. 在 build.gradle 中取消注释 implementation fileTree
 * 3. 申请 License 后填入智能体ID
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int ADMIN_CLICK_THRESHOLD = 5;
    private static final int ADMIN_CLICK_TIMEOUT = 2000;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private ConfigManager config;
    // private LingxinVoiceChat lingxinVoiceChat; // TODO: 取消注释并添加SDK后启用
    private Handler handler = new Handler();
    
    private int adminClickCount = 0;

    private ImageView ivBackground;
    private TextView tvThemeTitle;
    private TextView tvAiResponse;
    private TextView tvUserInput;
    private WaveformView waveView;
    private View viewAdminTrigger;

    // 权限请求Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean audioGranted = result.getOrDefault(Manifest.permission.RECORD_AUDIO, false);
            if (audioGranted != null && audioGranted) {
                // initLingxinSDK(); // TODO: 取消注释并添加SDK后启用
            } else {
                Toast.makeText(this, "录音权限被拒绝，部分功能无法使用", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ConfigManager(this);
        
        initViews();
        setupKioskMode();
        loadConfiguration();
        setupAdminTrigger();
        
        // 检查并请求权限，然后初始化SDK
        checkPermissionsAndInit();
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
            Glide.with(this).load(android.net.Uri.parse(bgUri)).centerCrop().into(ivBackground);
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
                startActivity(new android.content.Intent(this, SettingsActivity.class));
            } else {
                handler.postDelayed(() -> {
                    adminClickCount = 0;
                    tvUserInput.setText("");
                }, ADMIN_CLICK_TIMEOUT);
            }
        });
    }

    /**
     * 检查权限并初始化SDK
     */
    private void checkPermissionsAndInit() {
        if (checkPermission()) {
            // initLingxinSDK(); // TODO: 取消注释并添加SDK后启用
            tvAiResponse.setText("系统就绪\n请在设置中配置智能体ID");
        } else {
            requestAudioPermission();
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestAudioPermission() {
        permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
    }

    /**
     * 初始化阿里云灵芯 SDK
     * 
     * TODO: 将以下代码取消注释，并确保已添加SDK依赖
     */
    /*
    private void initLingxinSDK() {
        String agentId = config.getAgentId();
        if (agentId == null || agentId.isEmpty()) {
            Log.w(TAG, "AgentID 未配置，跳过SDK初始化");
            return;
        }
        
        try {
            // 获取SDK实例
            lingxinVoiceChat = LingxinVoiceChat.getInstance();
            
            // 创建配置提供者
            InitConfigProvider configProvider = new InitConfigProvider() {
                @Override
                public String getAppId() {
                    return config.getAppId();
                }

                @Override
                public String getSN() {
                    return config.getSN();
                }

                @Override
                public String getAppKey() {
                    return config.getAppKey();
                }

                @Override
                public String getAgentCode() {
                    return agentId;
                }

                @Override
                public LingxinRecorder getRecorder() {
                    return null;
                }
            };
            
            // 初始化SDK
            lingxinVoiceChat.initVoiceChat(this, configProvider);
            
            Log.i(TAG, "阿里云灵芯 SDK 初始化成功");
            tvAiResponse.setText("系统就绪\n请说\"小科小科\"唤醒");
            
        } catch (Exception e) {
            Log.e(TAG, "SDK初始化失败: " + Objects.requireNonNull(e.getMessage()));
            tvAiResponse.setText("SDK初始化失败\n" + e.getMessage());
        }
    }
    */

    /**
     * 唤醒语音对话
     * TODO: 取消注释并添加SDK后启用
     */
    /*
    private void wakeupVoiceChat() {
        if (lingxinVoiceChat != null) {
            try {
                lingxinVoiceChat.wakeupOrTerminalVoiceChat();
                tvAiResponse.setText("正在倾听...");
                waveView.setSpeaking(false);
                tvUserInput.setText("");
            } catch (Exception e) {
                Log.e(TAG, "唤醒失败: " + e.getMessage());
            }
        }
    }
    */

    @Override
    protected void onResume() {
        super.onResume();
        loadConfiguration();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // if (lingxinVoiceChat != null) {
        //     try {
        //         lingxinVoiceChat.destroyVoiceChat();
        //     } catch (Exception e) {
        //         Log.e(TAG, "销毁失败: " + e.getMessage());
        //     }
        // }
    }

    @Override
    public void onBackPressed() {
        // 屏蔽返回键
    }
}
