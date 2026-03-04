package com.school.ai.terminal;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 配置管理器 - 负责保存 AgentID、License、SDK配置等
 */
public class ConfigManager {
    private static final String PREF_NAME = "SchoolAI_Config";
    private SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==========================================
    // 智能体配置
    // ==========================================
    
    // 智能体ID (AgentCode) - 必填
    public void saveAgentId(String id) {
        prefs.edit().putString("agent_id", id).apply();
    }

    public String getAgentId() {
        return prefs.getString("agent_id", "");
    }

    // 主题名称
    public void saveThemeName(String name) {
        prefs.edit().putString("theme_name", name).apply();
    }

    public String getThemeName() {
        return prefs.getString("theme_name", "未来科普终端");
    }

    // 背景图 URI
    public void saveBgUri(String uri) {
        prefs.edit().putString("bg_uri", uri).apply();
    }

    public String getBgUri() {
        return prefs.getString("bg_uri", null);
    }

    // ==========================================
    // 阿里云灵芯 SDK 配置 - 申请License后获取
    // ==========================================
    
    // AppId
    public void saveAppId(String id) {
        prefs.edit().putString("app_id", id).apply();
    }

    public String getAppId() {
        return prefs.getString("app_id", "");
    }

    // 设备序列号 SN
    public void saveSN(String sn) {
        prefs.edit().putString("sn", sn).apply();
    }

    public String getSN() {
        return prefs.getString("sn", "");
    }

    // AppKey
    public void saveAppKey(String key) {
        prefs.edit().putString("app_key", key).apply();
    }

    public String getAppKey() {
        return prefs.getString("app_key", "");
    }

    // ==========================================
    // API 配置
    // ==========================================
    
    // API Key
    public void saveApiKey(String key) {
        prefs.edit().putString("api_key", key).apply();
    }

    public String getApiKey() {
        return prefs.getString("api_key", "");
    }

    // Product Key
    public void saveProductKey(String key) {
        prefs.edit().putString("product_key", key).apply();
    }

    public String getProductKey() {
        return prefs.getString("product_key", "");
    }

    // ==========================================
    // 设备 License 信息
    // ==========================================
    
    // 设备ID (注册后获取)
    public void saveDeviceId(String id) {
        prefs.edit().putString("device_id", id).apply();
    }

    public String getDeviceId() {
        return prefs.getString("device_id", "");
    }

    // 设备密钥 (注册后获取)
    public void saveDeviceSecret(String secret) {
        prefs.edit().putString("device_secret", secret).apply();
    }

    public String getDeviceSecret() {
        return prefs.getString("device_secret", "");
    }

    // License 激活状态
    public void saveLicenseStatus(String status) {
        prefs.edit().putString("license_status", status).apply();
    }

    public String getLicenseStatus() {
        return prefs.getString("license_status", "");
    }

    // License 服务器地址
    public void saveLicenseServerUrl(String url) {
        prefs.edit().putString("license_server_url", url).apply();
    }

    public String getLicenseServerUrl() {
        return prefs.getString("license_server_url", null);
    }

    // ==========================================
    // 语言设置
    // ==========================================
    
    // 语言设置 (zh/en)
    public void saveLanguage(String language) {
        prefs.edit().putString("language", language).apply();
    }

    public String getLanguage() {
        return prefs.getString("language", "zh");
    }

    // 英文主题名称
    public void saveThemeNameEn(String name) {
        prefs.edit().putString("theme_name_en", name).apply();
    }

    public String getThemeNameEn() {
        return prefs.getString("theme_name_en", "");
    }
}
