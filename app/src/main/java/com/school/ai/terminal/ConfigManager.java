package com.school.ai.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import com.aliyun.lingxinsdk.InitConfigProvider;
import com.aliyun.lingxinsdk.recorder.LingxinRecorder;

/**
 * 配置管理器 - 负责保存 AgentID、License 等配置
 */
public class ConfigManager {
    private static final String PREF_NAME = "SchoolAI_Config";
    private SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // 智能体ID (AgentCode)
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

    // 阿里云 AppId
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
}
