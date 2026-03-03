package com.school.ai.terminal;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * License 授权管理器
 * 负责设备注册、License 绑定
 * 基于阿里云 eAgent API
 */
public class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static final String API_BASE_URL = "https://api.eagent.edu-aliyun.com";
    
    // License 服务器地址 - 部署你自己的后端服务
    // private static final String LICENSE_SERVER_URL = "https://your-license-server.com";
    
    private final Context context;
    private final ConfigManager config;

    public LicenseManager(Context context) {
        this.context = context;
        this.config = new ConfigManager(context);
    }

    /**
     * 获取设备硬件标识 (MAC地址 + CPU信息)
     */
    public String getDeviceIdentifier() {
        StringBuilder sb = new StringBuilder();
        
        // 获取 MAC 地址
        try {
            String mac = getMacAddress();
            if (mac != null && !mac.isEmpty()) {
                sb.append(mac.replace(":", ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取MAC失败: " + e.getMessage());
        }
        
        // 备用: 使用设备序列号
        if (sb.length() < 8) {
            sb.append(Build.SERIAL);
        }
        
        // 确保有足够的标识符
        String identifier = sb.toString();
        if (identifier.length() < 8) {
            identifier = identifier + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        
        return identifier.toUpperCase();
    }

    /**
     * 获取 MAC 地址
     */
    private String getMacAddress() {
        try {
            // 尝试从网络接口获取
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
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
            Log.e(TAG, "获取MAC地址异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 注册设备并获取 License
     * 调用 POST /api/v1/devices/register
     */
    public LicenseResult registerDevice(String deviceName) {
        try {
            String deviceId = getDeviceIdentifier();
            String macAddress = getMacAddress();
            
            // 构造请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("deviceName", deviceName);
            requestBody.put("deviceType", "smart_terminal");
            requestBody.put("macAddress", macAddress != null ? macAddress : "00:00:00:00:00:00");
            requestBody.put("productKey", PRODUCT_KEY);
            
            // 发送请求
            String response = sendRequest("/api/v1/devices/register", "POST", requestBody.toString());
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                if (json.has("code") && json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    String registeredDeviceId = data.optString("deviceId", deviceId);
                    String deviceSecret = data.optString("deviceSecret", "");
                    String status = data.optString("status", "inactive");
                    
                    // 保存设备凭证
                    config.saveDeviceId(registeredDeviceId);
                    config.saveDeviceSecret(deviceSecret);
                    
                    return new LicenseResult(true, registeredDeviceId, deviceSecret, status, null);
                } else {
                    String message = json.optString("message", "注册失败");
                    return new LicenseResult(false, null, null, null, message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "设备注册异常: " + e.getMessage());
            return new LicenseResult(false, null, null, null, e.getMessage());
        }
        
        return new LicenseResult(false, null, null, null, "网络请求失败");
    }

    /**
     * 绑定设备到用户/应用
     * 调用 POST /api/v1/devices/bind
     */
    public boolean bindDevice(String userId, String bindType) {
        try {
            String deviceId = config.getDeviceId();
            if (deviceId == null || deviceId.isEmpty()) {
                Log.e(TAG, "设备未注册");
                return false;
            }
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("deviceId", deviceId);
            requestBody.put("userId", userId);
            requestBody.put("bindType", bindType); // owner/guest
            
            String response = sendRequest("/api/v1/devices/bind", "POST", requestBody.toString());
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                return json.has("code") && json.getInt("code") == 200;
            }
        } catch (Exception e) {
            Log.e(TAG, "设备绑定异常: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * 查询设备状态
     * 调用 GET /api/v1/devices/{deviceId}
     */
    public DeviceInfo queryDeviceInfo() {
        try {
            String deviceId = config.getDeviceId();
            if (deviceId == null || deviceId.isEmpty()) {
                return null;
            }
            
            String response = sendRequest("/api/v1/devices/" + deviceId, "GET", null);
            
            if (response != null) {
                JSONObject json = new JSONObject(response);
                if (json.has("code") && json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    return new DeviceInfo(
                        data.optString("deviceId"),
                        data.optString("deviceName"),
                        data.optString("status"),
                        data.optString("lastOnlineTime"),
                        data.optString("firmwareVersion"),
                        data.optInt("batteryLevel", -1)
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "查询设备信息异常: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 发送 HTTP 请求
     */
    private String sendRequest(String endpoint, String method, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_BASE_URL + endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            if (body != null && (method.equals("POST") || method.equals("PUT"))) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "API响应码: " + responseCode);
            
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    StringBuilder sb = new StringBuilder();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                    return sb.toString();
                }
            } else {
                Log.e(TAG, "API错误: " + responseCode);
            }
            
        } catch (IOException e) {
            Log.e(TAG, "网络请求异常: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return null;
    }

    /**
     * License 注册结果
     */
    public static class LicenseResult {
        public final boolean success;
        public final String deviceId;
        public final String deviceSecret;
        public final String status;
        public final String errorMessage;

        public LicenseResult(boolean success, String deviceId, String deviceSecret, String status, String errorMessage) {
            this.success = success;
            this.deviceId = deviceId;
            this.deviceSecret = deviceSecret;
            this.status = status;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 设备信息
     */
    public static class DeviceInfo {
        public final String deviceId;
        public final String deviceName;
        public final String status;
        public final String lastOnlineTime;
        public final String firmwareVersion;
        public final int batteryLevel;

        public DeviceInfo(String deviceId, String deviceName, String status, String lastOnlineTime, 
                         String firmwareVersion, int batteryLevel) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.status = status;
            this.lastOnlineTime = lastOnlineTime;
            this.firmwareVersion = firmwareVersion;
            this.batteryLevel = batteryLevel;
        }
    }
}
