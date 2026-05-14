# Pay-Core 支付核心服务 — 设计规范

**项目：** AI-PAY 企业级聚合支付系统（子项目 1/4）  
**日期：** 2026-05-14  
**技术栈：** Java 21 + Spring Boot + MyBatis-Plus + MySQL 8 + Redis

---

## 1. 项目范围

Pay-Core 是整个 AI-PAY 系统的基础服务，提供：

- 聚合支付中台：统一下单、支付凭证生成、异步回调处理、退款、查询
- 统一账户体系：四层结构（平台 → 商户 → 应用 → 渠道）
- 商户端 REST API（API Key 认证）
- 管理后台专用 API（JWT 认证）
- OpenSpec 文档（springdoc-openapi 自动生成）

**支持渠道（初始版本）：**
- 微信支付：JSAPI（公众号）、H5（手机浏览器）、Native（扫码）、小程序支付
- 支付宝：手机网站支付（alipay.trade.wap.pay）

后续可扩展：支付宝 PC 支付、App 支付、银联等。

---

## 2. 技术架构

### 2.1 多模块 Maven 结构

```
ai-pay/                         # 父 pom，统一版本管理
├── pay-common/                 # 枚举、DTO、异常、工具类、常量
├── pay-core/                   # 核心业务：订单、退款、账户、对账
├── pay-channel/                # 渠道抽象接口 + 各渠道实现
│   ├── pay-channel-api/        # PayChannel 接口定义
│   ├── pay-channel-wechat/     # 微信 JSAPI + H5 适配器
│   └── pay-channel-alipay/     # 支付宝手机网站适配器
├── pay-api/                    # 商户端 REST API + OpenSpec
├── pay-admin-api/              # 管理后台 REST API（JWT）
└── pay-bootstrap/              # Spring Boot 启动入口，组装所有模块
```

单一 JAR 打包部署，模块间通过 Maven 依赖隔离，新增渠道只需添加新子模块。

### 2.2 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.x | 框架 |
| MyBatis-Plus | 3.5.x | ORM |
| springdoc-openapi | 2.x | OpenSpec 文档自动生成 |
| wechatpay-apache-httpclient | 最新 | 微信支付 V3 SDK |
| alipay-sdk-java | 最新 | 支付宝 SDK |
| redisson | 3.x | Redis 分布式锁 / 幂等 |
| jjwt | 0.12.x | JWT 生成验证 |
| jasypt-spring-boot | 3.x | 配置文件加密 |

---

## 3. 数据库设计（MySQL 8）

### 3.1 账户体系（4 层）

#### merchant — 商户
```sql
CREATE TABLE merchant (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no  VARCHAR(32) NOT NULL UNIQUE COMMENT 'MCH202405001',
  name         VARCHAR(128) NOT NULL,
  status       TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
  contact_email VARCHAR(128),
  contact_phone VARCHAR(32),
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### app — 应用
```sql
CREATE TABLE app (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id       VARCHAR(32) NOT NULL UNIQUE COMMENT 'app_前缀+随机串',
  merchant_id  BIGINT NOT NULL,
  name         VARCHAR(128) NOT NULL,
  live_key     VARCHAR(128) NOT NULL COMMENT 'SHA256哈希存储，明文仅展示一次',
  test_key     VARCHAR(128) NOT NULL COMMENT 'SHA256哈希存储',
  status       TINYINT NOT NULL DEFAULT 1,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
);
```

#### channel_config — 渠道配置
```sql
CREATE TABLE channel_config (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id      BIGINT NOT NULL,
  channel     VARCHAR(32) NOT NULL COMMENT 'wechat_jsapi|wechat_h5|wechat_native|wechat_miniprogram|alipay_wap',
  config_json TEXT NOT NULL COMMENT 'AES-256-GCM 加密，存 mch_id/appid/key/cert 等',
  status      TINYINT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel (app_id, channel)
);
```

### 3.2 权限体系

#### operator — 操作员
```sql
CREATE TABLE operator (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id  BIGINT NOT NULL,
  username     VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  real_name    VARCHAR(64),
  is_admin     TINYINT NOT NULL DEFAULT 0 COMMENT '1=商户管理员',
  status       TINYINT NOT NULL DEFAULT 1,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
);
```

#### operator_permission — 操作员权限
```sql
CREATE TABLE operator_permission (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  module      VARCHAR(32) NOT NULL COMMENT 'orders|refunds|channels|reconcile|apps|operators',
  can_view    TINYINT NOT NULL DEFAULT 0,
  can_operate TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_op_module (operator_id, module)
);
```

### 3.3 交易流水

#### charge — 支付订单
```sql
CREATE TABLE charge (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  charge_id        VARCHAR(32) NOT NULL UNIQUE COMMENT 'ch_前缀+随机串',
  app_id           BIGINT NOT NULL,
  merchant_id      BIGINT NOT NULL,
  out_trade_no     VARCHAR(64) NOT NULL COMMENT '商户系统订单号',
  channel          VARCHAR(32) NOT NULL COMMENT 'wechat_jsapi|wechat_h5|wechat_native|wechat_miniprogram|alipay_wap',
  amount           INT NOT NULL COMMENT '金额，单位：分',
  currency         VARCHAR(8) NOT NULL DEFAULT 'cny',
  subject          VARCHAR(256) NOT NULL,
  body             VARCHAR(512),
  client_ip        VARCHAR(64),
  status           VARCHAR(16) NOT NULL DEFAULT 'created' COMMENT 'created|pending|paid|refunded|expired|closed',
  paid             TINYINT NOT NULL DEFAULT 0,
  paid_at          DATETIME,
  time_expire      DATETIME,
  transaction_no   VARCHAR(64) COMMENT '渠道流水号',
  channel_extra    JSON COMMENT '渠道扩展参数，如 open_id',
  credential       JSON COMMENT '支付凭证，返回给客户端',
  failure_code     VARCHAR(32),
  failure_msg      VARCHAR(256),
  amount_refunded  INT NOT NULL DEFAULT 0,
  metadata         JSON,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_app_id (app_id),
  INDEX idx_merchant_id (merchant_id),
  INDEX idx_out_trade_no (out_trade_no),
  INDEX idx_transaction_no (transaction_no),
  INDEX idx_created_at (created_at)
);
```

**状态机：**
```
CREATED → PENDING → PAID → REFUNDED
                 ↓
              EXPIRED
```

#### refund — 退款单
```sql
CREATE TABLE refund (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_id     VARCHAR(32) NOT NULL UNIQUE COMMENT 're_前缀+随机串',
  charge_id     VARCHAR(32) NOT NULL,
  app_id        BIGINT NOT NULL,
  merchant_id   BIGINT NOT NULL,
  out_refund_no VARCHAR(64) NOT NULL,
  amount        INT NOT NULL COMMENT '退款金额，单位：分',
  description   VARCHAR(256),
  status        VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|success|failed',
  transaction_no VARCHAR(64) COMMENT '渠道退款流水号',
  failure_code  VARCHAR(32),
  failure_msg   VARCHAR(256),
  succeed_at    DATETIME,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_charge_id (charge_id)
);
```

#### notify_record — 回调幂等记录
```sql
CREATE TABLE notify_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  notify_key    VARCHAR(128) NOT NULL UNIQUE COMMENT 'channel:transaction_no',
  charge_id     VARCHAR(32),
  channel       VARCHAR(32) NOT NULL,
  raw_body      TEXT NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'processing' COMMENT 'processing|success|failed',
  process_count INT NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.4 对账

#### reconcile_record — 对账记录
```sql
CREATE TABLE reconcile_record (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id           BIGINT NOT NULL,
  merchant_id      BIGINT NOT NULL,
  channel          VARCHAR(32) NOT NULL,
  reconcile_date   DATE NOT NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|reconciling|matched|unmatched',
  total_count      INT NOT NULL DEFAULT 0,
  total_amount     BIGINT NOT NULL DEFAULT 0,
  matched_count    INT NOT NULL DEFAULT 0,
  unmatched_count  INT NOT NULL DEFAULT 0,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel_date (app_id, channel, reconcile_date)
);
```

---

## 4. API 设计

### 4.1 商户端 API（pay-api）

**认证：** HTTP Basic Auth，API Key 作为用户名，密码留空。  
**Base URL：** `/v1`  
**响应格式：** 统一 JSON，错误时返回 `{ "error": { "code": "...", "message": "..." } }`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/charges` | 创建支付订单 |
| GET  | `/v1/charges` | 查询订单列表（分页） |
| GET  | `/v1/charges/:id` | 查询单个订单 |
| POST | `/v1/charges/:id/refunds` | 发起退款 |
| GET  | `/v1/charges/:id/refunds` | 查询订单退款列表 |
| GET  | `/v1/refunds/:id` | 查询退款详情 |
| POST | `/v1/notify/wechat/:appId` | 微信支付异步通知（无需认证） |
| POST | `/v1/notify/alipay/:appId` | 支付宝异步通知（无需认证） |

**POST /v1/charges 请求体：**
```json
{
  "order_no": "ORDER_20240515_001",
  "channel": "wechat_jsapi",
  "amount": 9900,
  "currency": "cny",
  "subject": "商品名称",
  "body": "商品描述",
  "client_ip": "192.168.1.1",
  "time_expire": 1800,
  "channel_extra": { "open_id": "oUpF8xxx" },
  "metadata": {}
}
```

`channel_extra` 各渠道必填字段：

| channel | channel_extra 必填字段 |
|---------|----------------------|
| wechat_jsapi | `open_id`（公众号网页授权获取） |
| wechat_h5 | 无必填，可选 `scene_info`（H5 场景信息） |
| wechat_native | 无（后端直接获取 code_url） |
| wechat_miniprogram | `open_id`（小程序 `wx.login` 换取） |
| alipay_wap | `return_url`（支付完成同步跳转地址） |

**响应（含支付凭证）：**
```json
{
  "id": "ch_abcdef123456",
  "object": "charge",
  "status": "pending",
  "paid": false,
  "amount": 9900,
  "currency": "cny",
  "credential": {
    "wechat_jsapi": {
      "appId": "wxabc",
      "timeStamp": "1715700000",
      "nonceStr": "xxx",
      "package": "prepay_id=wx...",
      "signType": "RSA",
      "paySign": "xxx"
    }
  }
}
```

各渠道 `credential` 返回格式：

| channel | credential 内容 |
|---------|----------------|
| wechat_jsapi | `{ appId, timeStamp, nonceStr, package, signType, paySign }` — 直接传给 `wx.config` + `wx.chooseWXPay()` |
| wechat_h5 | `{ h5_url }` — 前端直接跳转此 URL 唤起微信 |
| wechat_native | `{ code_url }` — 前端用此 URL 渲染二维码（如 qrcode.js） |
| wechat_miniprogram | `{ appId, timeStamp, nonceStr, package, signType, paySign }` — 直接传给小程序 `wx.requestPayment()` |
| alipay_wap | `{ form }` — HTML form 字符串，前端 document.write 后自动提交跳转支付宝 |

### 4.2 管理后台 API（pay-admin-api）

**认证：** JWT Bearer Token（`Authorization: Bearer <token>`）  
**Base URL：** `/admin/v1`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/v1/auth/login` | 操作员登录 |
| POST | `/admin/v1/auth/refresh` | 刷新 Token |
| GET/POST | `/admin/v1/merchants` | 商户列表 / 创建商户 |
| GET/PUT | `/admin/v1/merchants/:id` | 商户详情 / 更新 |
| GET/POST | `/admin/v1/merchants/:id/apps` | App 列表 / 创建 App |
| GET/PUT | `/admin/v1/apps/:id` | App 详情 / 更新 |
| GET/PUT | `/admin/v1/apps/:id/channels/:ch` | 渠道配置查询 / 更新 |
| GET | `/admin/v1/charges` | 订单列表（多条件筛选+分页） |
| GET | `/admin/v1/charges/:id` | 订单详情 |
| GET | `/admin/v1/refunds` | 退款列表 |
| GET/POST | `/admin/v1/operators` | 操作员列表 / 创建 |
| PUT | `/admin/v1/operators/:id/permissions` | 更新操作员权限 |
| GET | `/admin/v1/reconcile` | 对账记录列表 |
| POST | `/admin/v1/reconcile/trigger` | 手动触发对账 |

### 4.3 OpenSpec 文档

通过 `springdoc-openapi-starter-webmvc-ui` 自动生成：
- JSON：`/v3/api-docs`
- Swagger UI：`/swagger-ui.html`（生产环境通过配置禁用）
- 所有 Controller 用 `@Tag`、`@Operation`、`@Schema` 注解补充语义

---

## 5. 核心业务流程

### 5.1 统一下单流程

1. 验证 API Key（SHA-256 哈希匹配）→ 解析关联 App
2. 验证 App 已配置目标渠道
3. 生成 `charge_id`（`ch_` + UUID 去横线取前 24 位），写库 status=`created`
4. 调用 `PayChannel.createOrder()`：
   - wechat_jsapi → 微信 `/v3/pay/transactions/jsapi`（传 openid）→ prepay_id → 组装 JSAPI 签名参数（appId/timeStamp/nonceStr/package/signType/paySign）
   - wechat_h5 → 微信 `/v3/pay/transactions/h5` → h5_url（前端直接跳转唤起微信）
   - wechat_native → 微信 `/v3/pay/transactions/native` → code_url（前端用此 URL 生成二维码展示）
   - wechat_miniprogram → 微信 `/v3/pay/transactions/jsapi`（传小程序 appId + openid）→ prepay_id → 组装与 JSAPI 相同的签名参数（小程序调 `wx.requestPayment()` 使用）
   - alipay_wap → 支付宝 `alipay.trade.wap.pay` → 获取跳转 form
5. 将凭证写入 `charge.credential`，更新 status=`pending`
6. 返回完整 charge 对象

### 5.2 异步回调处理（幂等保障）

1. 接收 POST 请求，先执行渠道验签（失败直接返回 HTTP 400）
2. Redis `SETNX notify:{channel}:{transaction_no} "processing" EX 300`
   - 返回 0（已存在）→ 直接返回成功，跳过处理
3. 写 `notify_record`（status=processing）
4. 查 charge（by transaction_no 或解析通知体中的 out_trade_no）
5. 校验金额一致性
6. `UPDATE charge SET status='paid', paid=1, paid_at=now() WHERE charge_id=? AND status='pending'`
7. 更新 `notify_record.status=success`，process_count+1
8. 返回渠道要求的成功响应（微信返回 `{code:"SUCCESS"}`，支付宝返回 `"success"`）
9. 异常时：`notify_record.status=failed`，删除 Redis key（允许渠道重试）

### 5.3 退款流程

1. 验证 charge 存在且 `paid=true`
2. 校验 `charge.amount_refunded + refund.amount ≤ charge.amount`
3. 幂等 key：`SETNX refund:{out_refund_no}`
4. 生成 `refund_id`（`re_` 前缀），写库 status=`pending`
5. 调用 `PayChannel.refund()`
6. 微信退款异步（监听退款回调更新状态），支付宝退款同步（直接更新）
7. 更新 `charge.amount_refunded`；若全额退款则 `charge.status=refunded`

---

## 6. 安全设计

### 6.1 API Key 管理

- 格式：`sk_live_{32位随机}` / `sk_test_{32位随机}`
- 存储：SHA-256 哈希入库，明文仅在创建时展示一次（不可再次查看）
- 认证：取请求 Key → SHA-256 → 查库，O(1) 匹配

### 6.2 渠道密钥加密

- 算法：AES-256-GCM
- 加密 Key 通过环境变量 `APP_ENCRYPT_KEY` 注入，不进代码库和 DB
- `channel_config.config_json` 存加密后的密文
- 存储内容示例（微信）：`mch_id`、`app_id`、`api_v3_key`、`serial_no`、`private_key`

### 6.3 管理后台认证

- Access Token：HS256 JWT，8h 有效期，Payload 含 `operator_id`、`merchant_id`、`is_admin`、`permissions`
- Refresh Token：随机串，7d 有效期，存 Redis，支持主动吊销
- 操作员密码：BCrypt 哈希（strength=12）

### 6.4 渠道回调验签

- 微信 V3：使用微信平台公钥验证 RSA-SHA256 签名
- 支付宝：使用支付宝公钥验证 RSA2（SHA256WithRSA）签名
- 验签失败立即返回 HTTP 400，不执行任何业务逻辑

---

## 7. 渠道抽象接口

```java
public interface PayChannel {
    String channelCode();               // "wechat_jsapi" 等
    CreateOrderResult createOrder(CreateOrderRequest req);
    NotifyResult parseNotify(NotifyRequest req);  // 含验签
    RefundResult refund(RefundRequest req);
    QueryResult query(String outTradeNo);
}
```

注册到 Spring 容器后，`ChannelRouter` 通过 `channelCode()` 动态路由，新增渠道只需实现接口并注册，核心代码零修改。

---

## 8. 后续子项目

| # | 子项目 | 依赖 |
|---|--------|------|
| 2 | Admin-Portal（React + Ant Design 商户管理后台） | 依赖本服务 API |
| 3 | Cashier-H5（H5 收银台） | 依赖本服务 API |
| 4 | Infra（Docker Compose + CI/CD） | 依赖 1-3 完成 |
