# AI-PAY 统一支付平台

AI-PAY 是一个统一支付网关，对外提供两套 REST API：

- **商户收单 API**（`/v1/`）：供接入方系统调用，用于创建收款订单、退款、接收异步通知。
- **管理后台 API**（`/admin/v1/`）：供运营人员使用，用于商户管理、渠道配置、订单查询。

**在线接口文档（Swagger UI）：** `http://{host}:{port}/swagger-ui.html`

---

## 目录

1. [认证机制](#认证机制)
2. [支付渠道](#支付渠道)
3. [商户收单 API](#商户收单-api)
   - [创建收款订单](#创建收款订单)
   - [查询收款订单](#查询收款订单)
   - [订单列表](#订单列表)
4. [退款 API](#退款-api)
   - [申请退款](#申请退款)
   - [查询退款](#查询退款)
   - [退款列表](#退款列表)
5. [异步通知接收](#异步通知接收)
6. [错误码参考](#错误码参考)
7. [管理后台 API](#管理后台-api)
8. [最佳实践](#最佳实践)

---

## 认证机制

### 商户收单 API（API Key）

商户收单接口使用 HTTP Basic Auth，以 API Key 作为用户名，密码留空：

```
Authorization: Basic base64("{apiKey}:")
```

**示例（curl）：**

```bash
curl -u "sk_live_your_api_key_here:" https://{host}/v1/charges \
  -H "Content-Type: application/json" \
  -d '{ ... }'
```

API Key 由平台管理员在后台创建 App 后生成，格式为 `sk_live_` 或 `sk_test_` 前缀的字符串。

### 管理后台 API（JWT Bearer）

管理接口先通过登录接口换取 JWT，后续请求在 Header 中携带：

```
Authorization: Bearer {access_token}
```

---

## 支付渠道

| 渠道代码              | 说明                      | 备注                          |
|-----------------------|---------------------------|-------------------------------|
| `wechat_jsapi`        | 微信公众号支付（JSAPI）   | 需提供 `open_id`              |
| `wechat_h5`           | 微信 H5 支付              | 适用于非微信内浏览器           |
| `wechat_native`       | 微信扫码支付（Native）    | 返回二维码链接                |
| `wechat_miniprogram`  | 微信小程序支付            | 需提供 `open_id`              |
| `alipay_wap`          | 支付宝手机网站支付        | 返回跳转 URL                  |

---

## 商户收单 API

### 创建收款订单

**`POST /v1/charges`**

**请求体：**

```json
{
  "order_no": "ORDER20240101001",
  "channel": "wechat_jsapi",
  "amount": 9900,
  "currency": "cny",
  "subject": "商品标题",
  "body": "商品描述",
  "client_ip": "127.0.0.1",
  "time_expire": 1800,
  "channel_extra": {
    "open_id": "oUpF8uMuAJO_M2pxb1Q9zNjWeS6o"
  },
  "metadata": {
    "user_id": "12345"
  }
}
```

| 字段            | 类型     | 必填 | 说明                                            |
|-----------------|----------|------|-------------------------------------------------|
| `order_no`      | string   | 否   | 商户侧订单号，用于幂等去重                       |
| `channel`       | string   | 是   | 支付渠道代码，见[支付渠道](#支付渠道)表          |
| `amount`        | integer  | 是   | 支付金额，**单位：分**（人民币）                 |
| `currency`      | string   | 否   | 货币，默认 `cny`                                |
| `subject`       | string   | 否   | 商品标题                                        |
| `body`          | string   | 否   | 商品描述                                        |
| `client_ip`     | string   | 否   | 用户 IP，部分渠道必须                            |
| `time_expire`   | integer  | 否   | 过期时长（秒），默认 1800                        |
| `channel_extra` | object   | 条件 | 渠道附加参数，JSAPI/小程序必须包含 `open_id`    |
| `metadata`      | object   | 否   | 自定义透传字段                                  |

**成功响应（200）：**

```json
{
  "id": "ch_1a2b3c4d5e6f",
  "object": "charge",
  "status": "pending",
  "paid": false,
  "amount": 9900,
  "currency": "cny",
  "subject": "商品标题",
  "out_trade_no": "ORDER20240101001",
  "channel": "wechat_jsapi",
  "created_at": "2024-01-01T10:00:00",
  "credential": {
    "appId": "wx1234567890",
    "timeStamp": "1704067200",
    "nonceStr": "randomstring",
    "package": "prepay_id=wx...",
    "signType": "RSA",
    "paySign": "signature..."
  }
}
```

`credential` 字段为前端调起支付所需参数，各渠道内容不同：

| 渠道                    | `credential` 内容                           |
|-------------------------|---------------------------------------------|
| `wechat_jsapi`          | 微信 JSAPI 支付参数（`appId`、`paySign` 等）|
| `wechat_miniprogram`    | 微信小程序支付参数                           |
| `wechat_h5`             | `{ "h5_url": "https://wx.tenpay.com/..." }` |
| `wechat_native`         | `{ "code_url": "weixin://wxpay/..." }`      |
| `alipay_wap`            | `{ "pay_url": "https://openapi.alipay..." }`|

**失败响应（400）：**

```json
{
  "error": {
    "code": "channel_not_configured",
    "message": "Payment channel not configured for this app"
  }
}
```

---

### 查询收款订单

**`GET /v1/charges/{chargeId}`**

| 参数       | 位置 | 说明           |
|------------|------|----------------|
| `chargeId` | path | 平台侧订单 ID  |

**成功响应（200）：** 格式同创建订单响应。

**已支付订单额外字段：**

```json
{
  "id": "ch_1a2b3c4d5e6f",
  "status": "success",
  "paid": true
}
```

**失败响应：**
- `404`：订单不存在
- `403`：该订单不属于当前 App

---

### 订单列表

**`GET /v1/charges`**

| 参数     | 类型    | 必填 | 默认值 | 说明                                    |
|----------|---------|------|--------|-----------------------------------------|
| `page`   | integer | 否   | 1      | 页码                                    |
| `size`   | integer | 否   | 20     | 每页数量                                |
| `status` | string  | 否   | -      | 按状态筛选：`pending`/`success`/`failed`|

**成功响应（200）：**

```json
{
  "object": "list",
  "data": [ { "..." : "..." } ],
  "total": 100,
  "page": 1,
  "size": 20
}
```

---

## 退款 API

### 申请退款

**`POST /v1/charges/{chargeId}/refunds`**

**请求体：**

```json
{
  "amount": 5000,
  "out_refund_no": "REFUND20240101001",
  "description": "用户申请退款"
}
```

| 字段            | 类型    | 必填 | 说明                                      |
|-----------------|---------|------|-------------------------------------------|
| `amount`        | integer | 是   | 退款金额，**单位：分**，不得超过可退金额  |
| `out_refund_no` | string  | 否   | 商户侧退款单号，用于幂等去重              |
| `description`   | string  | 否   | 退款原因                                  |

**成功响应（200）：**

```json
{
  "id": "re_1a2b3c4d5e6f",
  "object": "refund",
  "charge_id": "ch_1a2b3c4d5e6f",
  "amount": 5000,
  "status": "pending",
  "out_refund_no": "REFUND20240101001",
  "created_at": "2024-01-01T11:00:00"
}
```

**失败响应（400）：**

```json
{
  "error": {
    "code": "refund_amount_exceeded",
    "message": "Refund amount exceeds remaining refundable amount"
  }
}
```

---

### 查询退款

**`GET /v1/refunds/{refundId}`**

**成功响应（200）：** 格式同申请退款响应。

---

### 退款列表

**`GET /v1/charges/{chargeId}/refunds`**

| 参数   | 类型    | 必填 | 默认值 | 说明 |
|--------|---------|------|--------|------|
| `page` | integer | 否   | 1      | 页码 |
| `size` | integer | 否   | 20     | 每页 |

**成功响应（200）：**

```json
{
  "object": "list",
  "data": [ { "..." : "..." } ],
  "total": 3
}
```

---

## 异步通知接收

支付成功后，各渠道会向平台推送回调，平台验签并更新订单状态后再通知您的业务系统。以下端点供渠道服务器使用，接入方无需主动调用。

### 微信支付回调

**`POST /v1/notify/wechat/{appId}`**

| 参数      | 位置  | 说明                                                       |
|-----------|-------|------------------------------------------------------------|
| `appId`   | path  | 平台侧 App ID                                              |
| `channel` | query | 可选，指定渠道（默认 `wechat_jsapi`），如 `wechat_native` |

成功响应：`{ "code": "SUCCESS", "message": "成功" }`
失败响应：`{ "code": "FAIL", "message": "Notification processing failed" }`

### 支付宝回调

**`POST /v1/notify/alipay/{appId}`**

| 参数    | 位置 | 说明          |
|---------|------|---------------|
| `appId` | path | 平台侧 App ID |

成功响应：`success`（纯文本）
失败响应：`fail`（纯文本）

> **幂等保证：** 平台使用 Redis SETNX 对每个 `trade_no` 进行幂等保护，相同通知重复推送只处理一次。

---

## 错误码参考

所有业务错误以统一结构返回：

```json
{
  "error": {
    "code": "error_code",
    "message": "Human-readable description"
  }
}
```

| 错误码                      | HTTP 状态 | 说明                                     |
|-----------------------------|-----------|------------------------------------------|
| `invalid_request`           | 400       | 请求参数缺失或格式错误                   |
| `invalid_api_key`           | 401       | API Key 无效或缺失                        |
| `forbidden`                 | 403       | 当前账号无权访问该资源                   |
| `not_found`                 | 404       | 资源不存在                               |
| `channel_not_configured`    | 400       | 该 App 未配置对应支付渠道                |
| `charge_not_found`          | 404       | 收款订单不存在                           |
| `charge_already_paid`       | 400       | 订单已支付，不可重复支付                 |
| `refund_amount_exceeded`    | 400       | 退款金额超出可退余额                     |
| `channel_error`             | 400       | 上游渠道返回错误（含渠道原始错误信息）   |
| `notify_signature_invalid`  | 400       | 渠道回调签名验证失败                     |
| `merchant_not_found`        | 404       | 商户不存在                               |
| `app_not_found`             | 404       | App 不存在                               |

服务端异常统一返回 `500`。

---

## 管理后台 API

> 所有 `/admin/v1/` 接口均需 JWT Bearer 认证；标注"仅超管"的接口额外校验 `is_admin: true`。

### 登录

**`POST /admin/v1/auth/login`**

**请求体：**

```json
{
  "username": "admin",
  "password": "your_password"
}
```

**成功响应（200）：**

```json
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 28800,
  "operator_id": 1,
  "merchant_id": 1,
  "is_admin": true
}
```

Token 有效期 8 小时（28800 秒）。登录失败返回 `401`。

---

### 商户管理

| 方法   | 路径                            | 说明                   | 权限   |
|--------|---------------------------------|------------------------|--------|
| GET    | `/admin/v1/merchants`           | 分页查询所有商户       | 登录即可 |
| POST   | `/admin/v1/merchants`           | 创建商户               | 仅超管 |
| GET    | `/admin/v1/merchants/{id}`      | 查询单个商户           | 登录即可 |
| PUT    | `/admin/v1/merchants/{id}`      | 更新商户信息           | 仅超管 |
| GET    | `/admin/v1/merchants/{id}/apps` | 查询商户下的 App 列表  | 登录即可 |
| POST   | `/admin/v1/merchants/{id}/apps` | 创建 App               | 仅超管 |

**创建商户请求体：**

```json
{
  "name": "示例商户",
  "contact_email": "contact@example.com",
  "contact_phone": "13800138000"
}
```

**创建 App 请求体：**

```json
{
  "name": "我的小程序"
}
```

创建 App 后，响应中包含 `api_key`，请妥善保存，该密钥仅在创建时返回一次。

---

### 渠道配置

| 方法 | 路径                                        | 说明         |
|------|---------------------------------------------|--------------|
| GET  | `/admin/v1/apps/{appId}/channels/{channel}` | 查询渠道状态 |
| PUT  | `/admin/v1/apps/{appId}/channels/{channel}` | 保存渠道配置 |

**微信 JSAPI 渠道配置请求体：**

```json
{
  "mchId": "1234567890",
  "apiV3Key": "your_api_v3_key_32_bytes_long_str",
  "serialNo": "your_certificate_serial_number",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "appId": "wx1234567890"
}
```

**支付宝 WAP 渠道配置请求体：**

```json
{
  "alipayAppId": "2021000000000000",
  "privateKey": "MIIEvgIBADANBgkq...",
  "alipayPublicKey": "MIIBIjANBgkq..."
}
```

渠道凭证使用 AES-256-GCM 加密存储，查询接口不返回密钥明文。

---

### 订单查询（管理）

| 方法 | 路径                            | 说明                                        |
|------|---------------------------------|---------------------------------------------|
| GET  | `/admin/v1/charges`             | 分页查询（支持 `merchantId`、`status` 筛选）|
| GET  | `/admin/v1/charges/{chargeId}`  | 查询单个订单                                |
| GET  | `/admin/v1/refunds`             | 查询退款列表（需 `chargeId` 参数）          |

---

### 操作员管理

| 方法 | 路径                                    | 说明           | 权限   |
|------|-----------------------------------------|----------------|--------|
| POST | `/admin/v1/operators`                   | 创建操作员     | 仅超管 |
| PUT  | `/admin/v1/operators/{id}/permissions`  | 更新操作员权限 | 仅超管 |

**创建操作员请求体：**

```json
{
  "username": "operator1",
  "password": "secure_password",
  "real_name": "张三",
  "is_admin": false
}
```

**更新权限请求体（数组）：**

```json
[
  { "module": "charges", "can_operate": 1 },
  { "module": "refunds", "can_operate": 0 }
]
```

`can_operate: 1` 表示操作权，`0` 表示仅查看权。

---

## 最佳实践

### 1. 金额单位

所有金额字段均以**分（fen）**为单位传入，类型为整数。例如 ¥99.00 应传 `9900`。浮点数会导致精度丢失，平台不接受小数。

### 2. 订单号唯一性

`order_no`（商户侧订单号）需在您的系统内全局唯一。平台使用该字段实现幂等：相同 `order_no` 的重复请求不会重复创建订单。建议格式：`{业务前缀}{时间戳}{随机数}`。

### 3. 获取 open_id

使用 `wechat_jsapi` 或 `wechat_miniprogram` 渠道时，必须在 `channel_extra.open_id` 中提供用户的微信 `open_id`。请在前端通过微信 OAuth2 授权（公众号）或 `wx.login()`（小程序）提前获取后传给服务端。

### 4. 支付结果确认策略

建议采用**双重确认**策略：
- 前端完成支付后立即调用 `GET /v1/charges/{chargeId}` 轮询，最多轮询 5 次（间隔 2 秒）。
- 同时配置回调地址监听平台异步通知，以处理网络异常导致前端未收到结果的情况。
- 以平台通知或查询接口的 `paid: true` 为最终支付凭证，不依赖前端跳转参数。

### 5. 生产环境安全

- API Key 仅在服务端使用，绝不暴露到前端或移动客户端。
- HTTPS 全程通信，拒绝明文 HTTP 请求。
- 渠道密钥（微信 API V3 Key、支付宝私钥）通过管理后台配置，平台加密存储。
- JWT Token 有效期 8 小时，过期后重新调用登录接口换取。
