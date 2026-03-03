package com.school.ai.terminal;

import com.aliyun.lingxinsdk.InitConfigProvider;
import com.aliyun.lingxinsdk.recorder.LingxinRecorder;

/**
 * SDK 配置提供者 - 实现阿里云灵芯 SDK 所需的配置接口
 * 在申请 License 时获取这些信息
 */
public class SDKConfigProvider implements InitConfigProvider {
    
    private final String appId;
    private final String sn;
    private final String appKey;
    private final String agentCode;

    public SDKConfigProvider(String appId, String sn, String appKey, String agentCode) {
        this.appId = appId;
        this.sn = sn;
        this.appKey = appKey;
        this.agentCode = agentCode;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public String getSN() {
        return sn;
    }

    @Override
    public String getAppKey() {
        return appKey;
    }

    @Override
    public String getAgentCode() {
        return agentCode;
    }

    @Override
    public LingxinRecorder getRecorder() {
        // 返回 null 使用默认录音实现
        return null;
    }
}
