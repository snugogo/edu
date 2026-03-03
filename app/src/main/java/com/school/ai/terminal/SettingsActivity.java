package com.school.ai.terminal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

/**
 * 设置页面 - 配置智能体ID、License等信息
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText etAgentId, etAppId, etSN, etAppKey, etThemeName;
    private ImageView ivPreview;
    private ConfigManager config;
    private String tempBgUri = null;

    // 图片选择 launcher
    private final ActivityResultLauncher<Intent> pickImageLauncher = 
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // 获取永久读权限
                        try {
                            getContentResolver().takePersistableUriPermission(
                                uri, 
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            // 权限获取失败，但仍可使用
                        }
                        
                        tempBgUri = uri.toString();
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
        
        initViews();
        loadCurrentConfig();
        setupListeners();
    }

    private void initViews() {
        etAgentId = findViewById(R.id.et_agent_id);
        etAppId = findViewById(R.id.et_app_id);
        etSN = findViewById(R.id.et_sn);
        etAppKey = findViewById(R.id.et_app_key);
        etThemeName = findViewById(R.id.et_theme_name);
        ivPreview = findViewById(R.id.iv_preview);
        
        Button btnSave = findViewById(R.id.btn_save);
        Button btnPickImage = findViewById(R.id.btn_pick_image);
        Button btnClear = findViewById(R.id.btn_clear);
        
        btnSave.setOnClickListener(v -> saveConfig());
        btnPickImage.setOnClickListener(v -> pickImage());
        btnClear.setOnClickListener(v -> clearConfig());
    }

    private void loadCurrentConfig() {
        etAgentId.setText(config.getAgentId());
        etAppId.setText(config.getAppId());
        etSN.setText(config.getSN());
        etAppKey.setText(config.getAppKey());
        etThemeName.setText(config.getThemeName());
        
        // 加载背景图预览
        String bgUri = config.getBgUri();
        if (bgUri != null && !bgUri.isEmpty()) {
            Glide.with(this)
                .load(Uri.parse(bgUri))
                .centerCrop()
                .into(ivPreview);
        }
    }

    private void setupListeners() {
        // 可以添加输入验证等逻辑
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickImageLauncher.launch(intent);
    }

    private void saveConfig() {
        String agentId = etAgentId.getText().toString().trim();
        String appId = etAppId.getText().toString().trim();
        String sn = etSN.getText().toString().trim();
        String appKey = etAppKey.getText().toString().trim();
        String themeName = etThemeName.getText().toString().trim();
        
        // 基本验证
        if (agentId.isEmpty()) {
            etAgentId.setError("请输入智能体ID");
            return;
        }
        
        if (appId.isEmpty()) {
            etAppId.setError("请输入AppId");
            return;
        }
        
        // 保存配置
        config.saveAgentId(agentId);
        config.saveAppId(appId);
        config.saveSN(sn);
        config.saveAppKey(appKey);
        config.saveThemeName(themeName.isEmpty() ? "未来科普终端" : themeName);
        
        if (tempBgUri != null) {
            config.saveBgUri(tempBgUri);
        }
        
        Toast.makeText(this, "配置已保存，需要重启应用生效", Toast.LENGTH_LONG).show();
        
        // 返回主界面
        finish();
    }
    
    private void clearConfig() {
        config.saveAgentId("");
        config.saveAppId("");
        config.saveSN("");
        config.saveAppKey("");
        config.saveThemeName("未来科普终端");
        config.saveBgUri("");
        
        etAgentId.setText("");
        etAppId.setText("");
        etSN.setText("");
        etAppKey.setText("");
        etThemeName.setText("未来科普终端");
        ivPreview.setImageResource(android.R.color.darker_gray);
        
        Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show();
    }
}
