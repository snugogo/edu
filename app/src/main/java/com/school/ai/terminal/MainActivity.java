package com.school.ai.terminal;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 主界面 - 未来科普终端 v1.2
 * - 幻灯片背景展示
 * - 紧急通知跑马灯
 * - OTA自动更新
 * - 中英双语支持
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int ADMIN_CLICK_THRESHOLD = 5;
    private static final int ADMIN_CLICK_TIMEOUT = 2000;
    private static final int SLIDESHOW_INTERVAL = 8000;
    
    private ConfigManager config;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private int adminClickCount = 0;
    private Timer slideshowTimer;
    private Timer notificationCheckTimer;
    private int currentImageIndex = 0;
    private List<String> backgroundImages = new ArrayList<>();

    private ImageView ivBackground;
    private TextView tvThemeTitle;
    private TextView tvAiResponse;
    private TextView tvUserInput;
    private TextView tvClock;
    private TextView tvMarquee;
    private WaveformView waveView;
    private View viewAdminTrigger;

    private boolean isEnglish = false;

    // 权限请求Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher = 
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean audioGranted = result.getOrDefault(Manifest.permission.RECORD_AUDIO, false);
            if (audioGranted != null && audioGranted) {
            } else {
                showToast(isEnglish ? "Microphone permission denied" : "录音权限被拒绝");
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
        
        checkPermissionsAndInit();
        startSlideshow();
        syncThemeFromServer();
        checkForUpdates();
        
        // 每30秒检查一次紧急通知
        startNotificationCheck();
    }

    private void initViews() {
        ivBackground = findViewById(R.id.iv_background);
        tvThemeTitle = findViewById(R.id.tv_theme_title);
        tvAiResponse = findViewById(R.id.tv_ai_response);
        tvUserInput = findViewById(R.id.tv_user_input);
        tvClock = findViewById(R.id.tv_clock);
        tvMarquee = findViewById(R.id.tv_marquee);
        waveView = findViewById(R.id.wave_view);
        viewAdminTrigger = findViewById(R.id.view_admin_trigger);
        
        startClock();
    }

    private void startClock() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    if (tvClock != null) {
                        tvClock.setText(sdf.format(new java.util.Date()));
                    }
                });
            }
        }, 0, 1000);
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
        isEnglish = "en".equals(config.getLanguage());
        
        String themeName = config.getThemeName();
        if (isEnglish && config.getThemeNameEn() != null && !config.getThemeNameEn().isEmpty()) {
            themeName = config.getThemeNameEn();
        }
        tvThemeTitle.setText(themeName);
        
        String bgUri = config.getBgUri();
        if (bgUri != null && !bgUri.isEmpty()) {
            Glide.with(this).load(Uri.parse(bgUri)).centerCrop().into(ivBackground);
        } else {
            ivBackground.setImageResource(R.drawable.ic_launcher_background);
        }

        if (config.getAgentId().isEmpty()) {
            tvAiResponse.setText(isEnglish ? 
                "Device not configured.\nConfigure License server in settings" :
                "设备未配置。\n系统设置中配置License服务器");
        } else {
            tvAiResponse.setText(isEnglish ?
                "Ready\nSay \"Hey AI\" to wake me" :
                "系统就绪\n请说\"小科小科\"唤醒");
        }
        
        // 初始化跑马灯
        initMarquee();
    }

    /**
     * 初始化跑马灯
     */
    private void initMarquee() {
        if (tvMarquee != null) {
            tvMarquee.setSelected(true);
            tvMarquee.setVisibility(View.GONE);
        }
    }

    /**
     * 显示紧急通知跑马灯
     */
    private void showNotification(String content) {
        if (tvMarquee != null && content != null && !content.isEmpty()) {
            tvMarquee.setText(content);
            tvMarquee.setVisibility(View.VISIBLE);
            tvMarquee.setBackgroundColor(Color.parseColor("#CCFF0000"));
        }
    }

    /**
     * 隐藏跑马灯
     */
    private void hideNotification() {
        if (tvMarquee != null) {
            tvMarquee.setVisibility(View.GONE);
        }
    }

    /**
     * 启动紧急通知检查
     */
    private void startNotificationCheck() {
        notificationCheckTimer = new Timer();
        notificationCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkNotification();
            }
        }, 10000, 30000); // 30秒检查一次
    }

    /**
     * 检查紧急通知
     */
    private void checkNotification() {
        String serverUrl = config.getLicenseServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) return;
        
        final String macAddress = getDeviceMacAddress();
        
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/v1/devices");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStream is = conn.getInputStream()) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                    
                    JSONObject response = new JSONObject(sb.toString());
                    if (response.getInt("code") == 200) {
                        JSONArray devices = response.getJSONArray("data");
                        
                        for (int i = 0; i < devices.length(); i++) {
                            JSONObject device = devices.getJSONObject(i);
                            if (macAddress.equalsIgnoreCase(device.optString("mac_address", ""))) {
                                
                                // 检查通知
                                if (device.has("notification") && !device.isNull("notification")) {
                                    JSONObject notif = device.getJSONObject("notification");
                                    String content = isEnglish ? 
                                        notif.optString("content_en", "") : 
                                        notif.optString("content", "");
                                    
                                    if (content.isEmpty()) {
                                        content = notif.optString("content", "");
                                    }
                                    
                                    final String finalContent = content;
                                    handler.post(() -> {
                                        if (finalContent != null && !finalContent.isEmpty()) {
                                            showNotification(finalContent);
                                        } else {
                                            hideNotification();
                                        }
                                    });
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Check notification failed: " + e.getMessage());
            }
        }).start();
    }

    private void setupAdminTrigger() {
        viewAdminTrigger.setOnClickListener(v -> {
            adminClickCount++;
            
            if (adminClickCount >= ADMIN_CLICK_THRESHOLD) {
                adminClickCount = 0;
                openAndroidSettings();
            } else {
                tvUserInput.setText((ADMIN_CLICK_THRESHOLD - adminClickCount) + "次");
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    adminClickCount = 0;
                    tvUserInput.setText("");
                }, ADMIN_CLICK_TIMEOUT);
            }
        });
    }

    private void openAndroidSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            showToast(isEnglish ? "Entered system settings" : "已进入系统设置");
        } catch (Exception e) {
            Log.e(TAG, "Open settings failed: " + e.getMessage());
        }
    }

    private void syncThemeFromServer() {
        String serverUrl = config.getLicenseServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.w(TAG, "No license server configured");
            return;
        }
        
        final String macAddress = getDeviceMacAddress();
        
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/v1/devices");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStream is = conn.getInputStream()) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                    
                    JSONObject response = new JSONObject(sb.toString());
                    if (response.getInt("code") == 200) {
                        JSONArray devices = response.getJSONArray("data");
                        
                        for (int i = 0; i < devices.length(); i++) {
                            JSONObject device = devices.getJSONObject(i);
                            if (macAddress.equalsIgnoreCase(device.optString("mac_address", ""))) {
                                
                                String themeName = device.optString("theme_name", "");
                                String themeNameEn = device.optString("theme_name_en", "");
                                String agentCode = device.optString("agent_code", "");
                                String agentName = device.optString("agent_name", "");
                                String imagesStr = device.optString("images", "[]");
                                String language = device.optString("language", "zh");
                                
                                // 解析图片
                                JSONArray images = new JSONArray(imagesStr);
                                backgroundImages.clear();
                                for (int j = 0; j < images.length(); j++) {
                                    backgroundImages.add(images.getString(j));
                                }
                                
                                final String finalThemeName = themeName.isEmpty() ? "Future Terminal" : 
                                    (isEnglish && !themeNameEn.isEmpty() ? themeNameEn : themeName);
                                final String finalAgentCode = agentCode;
                                
                                // 保存语言设置
                                config.saveLanguage(language);
                                isEnglish = "en".equals(language);
                                
                                handler.post(() -> {
                                    if (!finalThemeName.isEmpty()) {
                                        config.saveThemeName(finalThemeName);
                                        if (isEnglish && !themeNameEn.isEmpty()) {
                                            config.saveThemeNameEn(themeNameEn);
                                        }
                                        tvThemeTitle.setText(finalThemeName);
                                    }
                                    if (!finalAgentCode.isEmpty()) {
                                        config.saveAgentId(finalAgentCode);
                                    }
                                });
                                
                                Log.i(TAG, "Sync theme: " + finalThemeName + ", images: " + backgroundImages.size());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync theme failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * OTA 检查更新
     */
    private void checkForUpdates() {
        String serverUrl = config.getLicenseServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) return;
        
        String deviceId = config.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) return;
        
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/v1/ota/check?deviceId=" + deviceId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStream is = conn.getInputStream()) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                        }
                    }
                    
                    JSONObject response = new JSONObject(sb.toString());
                    if (response.getInt("code") == 200) {
                        JSONObject data = response.getJSONObject("data");
                        
                        if (data.getBoolean("hasUpdate")) {
                            String downloadUrl = data.optString("downloadUrl", "");
                            String versionName = data.optString("versionName", "");
                            boolean forceUpdate = data.getBoolean("forceUpdate");
                            
                            if (forceUpdate || shouldUpdate()) {
                                handler.post(() -> {
                                    showUpdateDialog(versionName, downloadUrl, forceUpdate);
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Check update failed: " + e.getMessage());
            }
        }).start();
    }

    private boolean shouldUpdate() {
        return Math.random() < 0.1; // 10%概率检查
    }

    private void showUpdateDialog(String version, String url, boolean force) {
        String title = isEnglish ? "Update Available" : "发现新版本";
        String message = isEnglish ? 
            "Version " + version + " is available. Download now?" : 
            "版本 " + version + " 可用。立即下载?";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(isEnglish ? "Update" : "更新", (dialog, which) -> {
            downloadAndInstall(url);
        });
        
        if (!force) {
            builder.setNegativeButton(isEnglish ? "Later" : "稍后", null);
        }
        
        builder.setCancelable(!force);
        builder.show();
    }

    private void downloadAndInstall(String url) {
        showToast(isEnglish ? "Downloading..." : "正在下载...");
        
        // 实际应该下载APK并安装
        // 这里简化处理：打开浏览器下载
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startSlideshow() {
        if (backgroundImages.isEmpty()) {
            return;
        }
        
        slideshowTimer = new Timer();
        slideshowTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (backgroundImages.isEmpty()) return;
                
                handler.post(() -> {
                    if (backgroundImages.size() > 1) {
                        currentImageIndex = (currentImageIndex + 1) % backgroundImages.size();
                        String imageUrl = backgroundImages.get(currentImageIndex);
                        
                        Glide.with(MainActivity.this)
                            .load(imageUrl)
                            .centerCrop()
                            .into(ivBackground);
                        
                        ivBackground.setAlpha(0.3f);
                        ivBackground.animate().alpha(1.0f).setDuration(1000).start();
                    }
                });
            }
        }, SLIDESHOW_INTERVAL, SLIDESHOW_INTERVAL);
    }

    private void checkPermissionsAndInit() {
        if (checkPermission()) {
            tvAiResponse.setText(isEnglish ?
                "Ready\nSay \"Hey AI\" to wake me" :
                "系统就绪\n请说\"小科小科\"唤醒");
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String getDeviceMacAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = interfaces.nextElement();
                    if (ni != null && !ni.isLoopback() && ni.isUp()) {
                        byte[] mac = ni.getHardwareAddress();
                        if (mac != null && mac.length == 6) {
                            StringBuilder sb = new StringBuilder();
                            for (byte b : mac) {
                                sb.append(String.format("%02X:", b));
                            }
                            return sb.substring(0, sb.length() - 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Get MAC failed: " + e.getMessage());
        }
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfiguration();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (slideshowTimer != null) slideshowTimer.cancel();
        if (notificationCheckTimer != null) notificationCheckTimer.cancel();
    }

    @Override
    public void onBackPressed() {
        // 屏蔽返回键
    }
}
