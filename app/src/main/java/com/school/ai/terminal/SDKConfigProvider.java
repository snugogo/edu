package com.school.ai.terminal;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SDK 配置提供者 - 本地实现用于编译
 * 实际使用时需要替换为阿里云灵芯 SDK
 */
public class SDKConfigProvider {
    
    private final String appId;
    private final String sn;
    private final String appKey;
    private final String agentCode;
    private final Context context;

    public SDKConfigProvider(Context context, String appId, String sn, String appKey, String agentCode) {
        this.context = context;
        this.appId = appId;
        this.sn = sn;
        this.appKey = appKey;
        this.agentCode = agentCode;
    }

    public String getAppId() {
        return appId;
    }

    public String getSN() {
        return sn;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getAgentCode() {
        return agentCode;
    }

    /**
     * 保存配置到 SharedPreferences
     */
    public void saveConfig() {
        SharedPreferences prefs = context.getSharedPreferences("sdk_config", Context.MODE_PRIVATE);
        prefs.edit()
            .putString("app_id", appId)
            .putString("sn", sn)
            .putString("app_key", appKey)
            .putString("agent_code", agentCode)
            .apply();
    }
    
    /**
     * 从 SharedPreferences 加载配置
     */
    public static SDKConfigProvider loadFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("sdk_config", Context.MODE_PRIVATE);
        return new SDKConfigProvider(
            context,
            prefs.getString("app_id", ""),
            prefs.getString("sn", ""),
            prefs.getString("app_key", ""),
            prefs.getString("agent_code", "")
        );
    }
}
