package com.school.ai.terminal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

/**
 * 设置页面 - 配置智能体ID、License等信息
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText etAgentId, etApiKey, etProductKey, etAppId, etSN, etAppKey, etThemeName;
    private TextView tvDeviceStatus, tvDeviceId;
    private ImageView ivPreview;
    private ConfigManager config;
    private LicenseManager licenseManager;
    private String tempBgUri = null;
    private Handler handler = new Handler(Looper.getMainLooper());

    // 图片选择 launcher
    private final ActivityResultLauncher<Intent> pickImageLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                uri, 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                        }
                        
                        tempBgUri = uri.toString();
                        ivPreview.setVisibility(View.VISIBLE);
                        Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .into(ivPreview);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        config = new ConfigManager(this);
        licenseManager = new LicenseManager(this);
        
        initViews();
        loadCurrentConfig();
        updateDeviceStatus();
    }

    private void initViews() {
        etAgentId = findViewById(R.id.et_agent_id);
        etApiKey = findViewById(R.id.et_api_key);
        etProductKey = findViewById(R.id.et_product_key);
        etAppId = findViewById(R.id.et_app_id);
        etSN = findViewById(R.id.et_sn);
        etAppKey = findViewById(R.id.et_app_key);
        etThemeName = findViewById(R.id.et_theme_name);
        
        tvDeviceStatus = findViewById(R.id.tv_device_status);
        tvDeviceId = findViewById(R.id.tv_device_id);
        ivPreview = findViewById(R.id.iv_preview);
        
        Button btnSave = findViewById(R.id.btn_save);
        Button btnRegister = findViewById(R.id.btn_register);
        Button btnPickImage = findViewById(R.id.btn_pick_img);
        Button btnClear = findViewById(R.id.btn_clear_img);
        
        btnSave.setOnClickListener(v -> saveConfig());
        btnRegister.setOnClickListener(v -> registerDevice());
        btnPickImage.setOnClickListener(v -> pickImage());
        btnClear.setOnClickListener(v -> clearImage());
    }

    private void loadCurrentConfig() {
        etAgentId.setText(config.getAgentId());
        etApiKey.setText(config.getApiKey());
        etProductKey.setText(config.getProductKey());
        etAppId.setText(config.getAppId());
        etSN.setText(config.getSN());
        etAppKey.setText(config.getAppKey());
        etThemeName.setText(config.getThemeName());
        
        // 加载背景图预览
        String bgUri = config.getBgUri();
        if (bgUri != null && !bgUri.isEmpty()) {
            ivPreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                .load(Uri.parse(bgUri))
                .centerCrop()
                .into(ivPreview);
        }
        
        // 显示设备标识
        String deviceIdentifier = licenseManager.getDeviceIdentifier();
        tvDeviceId.setText("设备标识: " + deviceIdentifier);
    }

    private void updateDeviceStatus() {
        String deviceId = config.getDeviceId();
        String licenseStatus = config.getLicenseStatus();
        
        if (deviceId != null && !deviceId.isEmpty()) {
            tvDeviceStatus.setText("设备状态: 已注册 (" + licenseStatus + ")");
            tvDeviceStatus.setTextColor(0xFF4CAF50); // 绿色
        } else {
            tvDeviceStatus.setText("设备状态: 未注册");
            tvDeviceStatus.setTextColor(0xFFFF9800); // 橙色
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickImageLauncher.launch(intent);
    }

    private void clearImage() {
        tempBgUri = "";
        ivPreview.setVisibility(View.GONE);
        ivPreview.setImageDrawable(null);
    }

    private void registerDevice() {
        String apiKey = etApiKey.getText().toString().trim();
        String productKey = etProductKey.getText().toString().trim();
        
        if (apiKey.isEmpty()) {
            etApiKey.setError("请输入API Key");
            return;
        }
        
        if (productKey.isEmpty()) {
            etProductKey.setError("请输入Product Key");
            return;
        }
        
        // 保存 API 配置
        config.saveApiKey(apiKey);
        config.saveProductKey(productKey);
        
        // 禁用按钮，显示加载状态
        Button btnRegister = findViewById(R.id.btn_register);
        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");
        
        // 在后台线程注册设备
        new Thread(() -> {
            String deviceName = "Terminal_" + licenseManager.getDeviceIdentifier().substring(0, 8);
            LicenseManager.LicenseResult result = licenseManager.registerDevice(deviceName);
            
            handler.post(() -> {
                btnRegister.setEnabled(true);
                btnRegister.setText("注册设备");
                
                if (result.success) {
                    Toast.makeText(this, "设备注册成功!", Toast.LENGTH_SHORT).show();
                    config.saveDeviceId(result.deviceId);
                    config.saveDeviceSecret(result.deviceSecret);
                    config.saveLicenseStatus(result.status);
                    updateDeviceStatus();
                } else {
                    Toast.makeText(this, "注册失败: " + result.errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void saveConfig() {
        String agentId = etAgentId.getText().toString().trim();
        String appId = etAppId.getText().toString().trim();
        String sn = etSN.getText().toString().trim();
        String appKey = etAppKey.getText().toString().trim();
        String themeName = etThemeName.getText().toString().trim();
        
        // 保存配置
        config.saveAgentId(agentId);
        config.saveApiKey(etApiKey.getText().toString().trim());
        config.saveProductKey(etProductKey.getText().toString().trim());
        config.saveAppId(appId);
        config.saveSN(sn);
        config.saveAppKey(appKey);
        config.saveThemeName(themeName.isEmpty() ? "未来科普终端" : themeName);
        
        if (tempBgUri != null && !tempBgUri.isEmpty()) {
            config.saveBgUri(tempBgUri);
        }
        
        Toast.makeText(this, "配置已保存，需要重启应用生效", Toast.LENGTH_LONG).show();
        
        finish();
    }
}
