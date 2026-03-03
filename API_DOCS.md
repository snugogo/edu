阿里云eAgent服务端API文档

> 文档版本: v1.0  
> 更新日期: 2026-03-04  
> 来源: https://eagent.edu-aliyun.com/console/device#/help/apipure_auth

---

目录

1. API说明和鉴权说明
2. 设备管理接口
3. APP接入指南
4. APP生产接口
5. 音色复刻接口
6. 数据查询接口
7. 错误码说明

---

一、API说明和鉴权说明

1.1 概述

阿里云eAgent平台提供标准的服务端API接口，支持设备管理、应用管理、音色复刻等功能。所有API均采用RESTful设计风格，返回JSON格式数据。

1.2 认证方式

平台支持以下三种认证方式：

1.2.1 API Key认证

适用场景: 简单的服务端调用，快速接入

请求头格式:
Authorization: Bearer {API_KEY}

1.2.2 JWT认证（JSON Web Token）

适用场景: 需要状态保持的多租户系统

特点:
- 使用HMAC算法、RSA或ECDSA进行签名
- 支持Token过期机制
- 可在客户端和服务端之间安全传输信息

请求头格式:
Authorization: Bearer {JWT_TOKEN}

1.2.3 HMAC认证（AK/SK）

适用场景: 高安全性要求的场景

签名流程:

1. 构造签名串:
HTTP_METHOD + "\n"
+ CONTENT_MD5 + "\n"
+ CONTENT_TYPE + "\n"
+ DATE + "\n"
+ CanonicalizedHeaders
+ CanonicalizedResource

2. 生成签名:
Mac hmacSha256 = Mac.getInstance("HmacSHA256");
byte[] secretBytes = secret.getBytes("UTF-8");
hmacSha256.init(new SecretKeySpec(secretBytes, 0, secretBytes.length, "HmacSHA256"));
byte[] result = hmacSha256.doFinal(stringToSign.getBytes("UTF-8"));
String sign = Base64.encodeBase64String(result);

3. 请求头示例:
x-ca-key: 203753385
x-ca-signature-method: HmacSHA256
x-ca-signature-headers: x-ca-timestamp,x-ca-key,x-ca-nonce,x-ca-signature-method
x-ca-signature: xfX+bZxY2yl7EB/qdoDy9v/uscw3Nnj1pgoU+Bm6xdM=
x-ca-timestamp: 1525872629832
x-ca-nonce: c9f15cbf-f4ac-4a6c-b54d-f51abf4b5b44

1.3 公共请求头

| 参数名 | 必填 | 说明 | 示例  Content-Type | 是 | 请求体格式 | application/json  Authorization | 是 | 认证信息 | Bearer {token}  x-ca-timestamp | 是(HMAC) | 时间戳 | 1525872629832  x-ca-nonce | 是(HMAC) | 随机字符串 | UUID |

1.4 公共响应格式

成功响应:
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "requestId": "req_123456789"
}

错误响应:
{
  "code": 400,
  "message": "Invalid parameter",
  "data": null,
  "requestId": "req_987654321"
}

---

二、设备管理接口

2.1 注册设备

接口地址: POST /api/v1/devices/register

请求参数:

| 参数名 | 类型 | 必填 | 说明  deviceName | string | 是 | 设备名称  deviceType | string | 是 | 设备类型  macAddress | string | 是 | MAC地址  productKey | string | 是 | 产品Key |

请求示例:
{
  "deviceName": "AI-Toy-001",
  "deviceType": "smart_doll",
  "macAddress": "A1:B2:C3:D4:E5:F6",
  "productKey": "pk123456789"
}

响应示例:
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "dev_abc123",
    "deviceSecret": "sec_xyz789",
    "status": "inactive"
  }
}

2.2 查询设备信息

接口地址: GET /api/v1/devices/{deviceId}

路径参数:

| 参数名 | 类型 | 必填 | 说明  deviceId | string | 是 | 设备ID |

响应示例:
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "dev_abc123",
    "deviceName": "AI-Toy-001",
    "status": "online",
    "lastOnlineTime": "2026-03-04T10:30:00Z",
    "firmwareVersion": "v1.2.3",
    "batteryLevel": 85
  }
}

2.3 更新设备状态

接口地址: PUT /api/v1/devices/{deviceId}/status

请求参数:

| 参数名 | 类型 | 必填 | 说明  status | string | 是 | 设备状态(online/offline/sleep)  batteryLevel | int | 否 | 电量百分比 |

2.4 设备列表查询

接口地址: GET /api/v1/devices

查询参数:

| 参数名 | 类型 | 必填 | 说明  pageNum | int | 否 | 页码，默认1  pageSize | int | 否 | 每页数量，默认20  status | string | 否 | 状态筛选  productKey | string | 否 | 产品Key筛选 |

响应示例:
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 150,
    "pageNum": 1,
    "pageSize": 20,
    "list": [
      {
        "deviceId": "dev_001",
        "deviceName": "AI-Toy-001",
        "status": "online"
      }
    ]
  }
}

---

三、APP接入指南

3.1 接入流程

1. 注册开发者账号 → 2. 创建应用 → 3. 获取AppKey/AppSecret → 4. 集成SDK → 5. 设备绑定 → 6. 功能调用

3.2 获取应用凭证

接口地址: POST /api/v1/apps/credentials

请求参数:

| 参数名 | 类型 | 必填 | 说明  appName | string | 是 | 应用名称  platform | string | 是 | 平台(ios/android)  bundleId | string | 是 | 包名 |

响应示例:
{
  "code": 200,
  "data": {
    "appKey": "ak_123456",
    "appSecret": "as_789012",
    "apiEndpoint": "https://api.eagent.edu-aliyun.com"
  }
}

3.3 设备绑定

接口地址: POST /api/v1/devices/bind

请求参数:

| 参数名 | 类型 | 必填 | 说明  deviceId | string | 是 | 设备ID  userId | string | 是 | 用户ID  bindType | string | 是 | 绑定类型(owner/guest) |

---

四、APP生产接口

4.1 创建生产批次

接口地址: POST /api/v1/production/batches

请求参数:

| 参数名 | 类型 | 必填 | 说明  productKey | string | 是 | 产品Key  quantity | int | 是 | 生产数量  batchName | string | 是 | 批次名称  factoryId | string | 是 | 工厂ID |

响应示例:
{
  "code": 200,
  "data": {
    "batchId": "batch_20240304_001",
    "status": "created",
    "deviceCredentials": [
      {
        "deviceId": "dev_001",
        "deviceSecret": "sec_001"
      }
    ]
  }
}

4.2 查询生产批次

接口地址: GET /api/v1/production/batches/{batchId}

响应示例:
{
  "code": 200,
  "data": {
    "batchId": "batch_20240304_001",
    "status": "completed",
    "totalCount": 1000,
    "successCount": 998,
    "failCount": 2,
    "createTime": "2026-03-04T08:00:00Z",
    "completeTime": "2026-03-04T18:00:00Z"
  }
}

4.3 下载设备证书

接口地址: GET /api/v1/production/batches/{batchId}/credentials

查询参数:

| 参数名 | 类型 | 必填 | 说明  format | string | 否 | 格式(csv/json)，默认csv |

---

五、音色复刻接口

5.1 提交音色复刻任务

接口地址: POST /api/v1/voice/clone

请求参数:

| 参数名 | 类型 | 必填 | 说明  voiceName | string | 是 | 音色名称  audioUrl | string | 是 | 音频文件URL  gender | string | 是 | 性别(male/female)  language | string | 是 | 语言(zh/en/ja等)  description | string | 否 | 音色描述 |

响应示例:
{
  "code": 200,
  "data": {
    "taskId": "task_voice_001",
    "status": "processing",
    "estimatedTime": 300
  }
}

5.2 查询音色复刻状态

接口地址: GET /api/v1/voice/clone/{taskId}

响应示例:
{
  "code": 200,
  "data": {
    "taskId": "task_voice_001",
    "status": "completed",
    "voiceId": "voice_custom_001",
    "progress": 100,
    "result": {
      "voiceId": "voice_custom_001",
      "sampleUrl": "https://xxx/sample.mp3",
      "quality": "high"
    }
  }
}

5.3 获取音色列表

接口地址: GET /api/v1/voice/list

查询参数:

| 参数名 | 类型 | 必填 | 说明  type | string | 否 | 类型(system/custom)  language | string | 否 | 语言筛选 |

响应示例:
{
  "code": 200,
  "data": {
    "total": 50,
    "list": [
      {
        "voiceId": "voice_001",
        "voiceName": "温柔女声",
        "type": "system",
        "language": "zh",
        "gender": "female"
      }
    ]
  }
}

---

六、数据查询接口

6.1 查询设备使用统计

接口地址: GET /api/v1/statistics/device/{deviceId}

查询参数:

| 参数名 | 类型 | 必填 | 说明  startTime | string | 是 | 开始时间(ISO 8601)  endTime | string | 是 | 结束时间(ISO 8601)  metric | string | 否 | 指标类型 |

响应示例:
{
  "code": 200,
  "data": {
    "deviceId": "dev_001",
    "period": "2026-03-01/2026-03-04",
    "totalOnlineTime": 3600,
    "interactionCount": 150,
    "averageSessionDuration": 120
  }
}

6.2 查询对话记录

接口地址: GET /api/v1/conversations

查询参数:

| 参数名 | 类型 | 必填 | 说明  deviceId | string | 否 | 设备ID筛选  userId | string | 否 | 用户ID筛选  startTime | string | 否 | 开始时间  endTime | string | 否 | 结束时间  pageNum | int | 否 | 页码  pageSize | int | 否 | 每页数量 |

响应示例:
{
  "code": 200,
  "data": {
    "total": 1000,
    "list": [
      {
        "conversationId": "conv_001",
        "deviceId": "dev_001",
        "userId": "user_001",
        "startTime": "2026-03-04T10:00:00Z",
        "duration": 300,
        "messageCount": 20
      }
    ]
  }
}

6.3 查询Token消耗

接口地址: GET /api/v1/billing/token-usage

查询参数:

| 参数名 | 类型 | 必填 | 说明  startDate | string | 是 | 开始日期(YYYY-MM-DD)  endDate | string | 是 | 结束日期(YYYY-MM-DD)  granularity | string | 否 | 粒度(day/hour)，默认day |

响应示例:
{
  "code": 200,
  "data": {
    "totalInputTokens": 1000000,
    "totalOutputTokens": 500000,
    "details": [
      {
        "date": "2026-03-04",
        "inputTokens": 100000,
        "outputTokens": 50000,
        "cost": 0.5
      }
    ]
  }
}

---

七、错误码说明

7.1 系统级错误码

| 错误码 | 错误信息 | 说明  200 | success | 请求成功  400 | bad request | 请求参数错误  401 | unauthorized | 未授权，认证失败  403 | forbidden | 禁止访问，权限不足  404 | not found | 资源不存在  429 | too many requests | 请求过于频繁，被限流  500 | internal server error | 服务器内部错误  503 | service unavailable | 服务暂不可用 |

7.2 业务错误码

| 错误码 | 错误信息 | 说明  400001 | invalid device id | 设备ID无效  400002 | device offline | 设备离线  400003 | invalid voice id | 音色ID无效  400004 | quota exceeded | 配额已用完  400005 | task not found | 任务不存在  400006 | task processing | 任务处理中  400007 | invalid audio format | 音频格式不支持  400008 | voice clone failed | 音色复刻失败 |

7.3 认证错误码

| 错误码 | 错误信息 | 说明  401001 | missing authorization | 缺少认证信息  401002 | invalid api key | API Key无效  401003 | expired token | Token已过期  401004 | invalid signature | 签名错误  401005 | nonce reused | Nonce重复使用  401006 | timestamp expired | 时间戳过期 |

---

附录

A. 支持的语言列表

| 语言代码 | 语言名称  zh | 中文  en | 英语  ja | 日语  ko | 韩语  fr | 法语  de | 德语  es | 西班牙语 |

B. 设备状态定义

| 状态 | 说明  inactive | 未激活  online | 在线  offline | 离线  sleep | 休眠  error | 故障 |

C. SDK下载

| 平台 | 下载链接  Android | https://eagent.edu-aliyun.com/sdk/android  iOS | https://eagent.edu-aliyun.com/sdk/ios  Linux | https://eagent.edu-aliyun.com/sdk/linux |

---

文档维护: 阿里云eAgent团队  
技术支持: support@eagent.aliyun.com
