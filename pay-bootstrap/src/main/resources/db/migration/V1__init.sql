-- =====================================================
-- Account hierarchy
-- =====================================================

CREATE TABLE IF NOT EXISTS merchant (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no   VARCHAR(32) NOT NULL UNIQUE COMMENT 'MCH202405001',
  name          VARCHAR(128) NOT NULL,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '0=disabled 1=enabled',
  contact_email VARCHAR(128),
  contact_phone VARCHAR(32),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Merchants';

CREATE TABLE IF NOT EXISTS app (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id       VARCHAR(32) NOT NULL UNIQUE COMMENT 'app_ prefix + random',
  merchant_id  BIGINT NOT NULL,
  name         VARCHAR(128) NOT NULL,
  live_key     VARCHAR(128) NOT NULL COMMENT 'SHA-256 hash; plaintext shown once',
  test_key     VARCHAR(128) NOT NULL COMMENT 'SHA-256 hash',
  status       TINYINT NOT NULL DEFAULT 1,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Applications under a merchant';

CREATE TABLE IF NOT EXISTS channel_config (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id      BIGINT NOT NULL,
  channel     VARCHAR(32) NOT NULL
                COMMENT 'wechat_jsapi|wechat_h5|wechat_native|wechat_miniprogram|alipay_wap',
  config_json TEXT NOT NULL COMMENT 'AES-256-GCM encrypted JSON with mch credentials',
  status      TINYINT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel (app_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-app channel credentials (encrypted)';

-- =====================================================
-- Operator / RBAC
-- =====================================================

CREATE TABLE IF NOT EXISTS operator (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id   BIGINT NOT NULL,
  username      VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt strength=12',
  real_name     VARCHAR(64),
  is_admin      TINYINT NOT NULL DEFAULT 0 COMMENT '1=merchant admin',
  status        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin portal operators';

CREATE TABLE IF NOT EXISTS operator_permission (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  module      VARCHAR(32) NOT NULL
                COMMENT 'orders|refunds|channels|reconcile|apps|operators',
  can_view    TINYINT NOT NULL DEFAULT 0,
  can_operate TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_op_module (operator_id, module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Operator permission matrix';

-- =====================================================
-- Transaction records
-- =====================================================

CREATE TABLE IF NOT EXISTS charge (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  charge_id        VARCHAR(32) NOT NULL UNIQUE COMMENT 'ch_ prefix',
  app_id           BIGINT NOT NULL,
  merchant_id      BIGINT NOT NULL,
  out_trade_no     VARCHAR(64) NOT NULL COMMENT 'Merchant order number',
  channel          VARCHAR(32) NOT NULL,
  amount           INT NOT NULL COMMENT 'Amount in fen',
  currency         VARCHAR(8) NOT NULL DEFAULT 'cny',
  subject          VARCHAR(256) NOT NULL,
  body             VARCHAR(512),
  client_ip        VARCHAR(64),
  status           VARCHAR(16) NOT NULL DEFAULT 'created'
                     COMMENT 'created|pending|paid|refunded|expired|closed',
  paid             TINYINT NOT NULL DEFAULT 0,
  paid_at          DATETIME,
  time_expire      DATETIME,
  transaction_no   VARCHAR(64) COMMENT 'Channel transaction number',
  channel_extra    JSON COMMENT 'e.g. open_id',
  credential       JSON COMMENT 'Payment credential returned to client',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payment orders';

CREATE TABLE IF NOT EXISTS refund (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_id      VARCHAR(32) NOT NULL UNIQUE COMMENT 're_ prefix',
  charge_id      VARCHAR(32) NOT NULL,
  app_id         BIGINT NOT NULL,
  merchant_id    BIGINT NOT NULL,
  out_refund_no  VARCHAR(64) NOT NULL,
  amount         INT NOT NULL COMMENT 'Refund amount in fen',
  description    VARCHAR(256),
  status         VARCHAR(16) NOT NULL DEFAULT 'pending'
                   COMMENT 'pending|success|failed',
  transaction_no VARCHAR(64) COMMENT 'Channel refund transaction number',
  failure_code   VARCHAR(32),
  failure_msg    VARCHAR(256),
  succeed_at     DATETIME,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_charge_id (charge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refund orders';

CREATE TABLE IF NOT EXISTS notify_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  notify_key    VARCHAR(128) NOT NULL UNIQUE COMMENT 'channel:transaction_no',
  charge_id     VARCHAR(32),
  channel       VARCHAR(32) NOT NULL,
  raw_body      TEXT NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'processing'
                  COMMENT 'processing|success|failed',
  process_count INT NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotency record for channel notifications';

-- =====================================================
-- Reconciliation
-- =====================================================

CREATE TABLE IF NOT EXISTS reconcile_record (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id          BIGINT NOT NULL,
  merchant_id     BIGINT NOT NULL,
  channel         VARCHAR(32) NOT NULL,
  reconcile_date  DATE NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'pending'
                    COMMENT 'pending|reconciling|matched|unmatched',
  total_count     INT NOT NULL DEFAULT 0,
  total_amount    BIGINT NOT NULL DEFAULT 0,
  matched_count   INT NOT NULL DEFAULT 0,
  unmatched_count INT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_channel_date (app_id, channel, reconcile_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reconciliation records';
