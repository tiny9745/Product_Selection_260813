-- =====================================================================
-- V33：PTT 新品探索（第一階段）
--
-- 【用途】現有 PTT 熱度同步是「先有商品、再用商品名稱去 PTT 搜尋」，找不到系統裡
-- 還沒有的商品。這一版反過來：每天掃現有 7 個看板近 7 天的文章標題，交給 Gemini
-- 抽出商品名稱，Java 端驗證（名稱必須真的出現在引用的標題裡）、彙總、排除既有商品後，
-- 存進 discovered_items，由操作人員決定「建立商品」或「略過」。
--
-- 【刻意不寫進 products】商品需要供應商、成本、售價等資料；自動建立的空殼會卡在
-- 資料完整度門檻、混進評分與統計。探索結果只是線索，轉成商品一律由人工完成
-- （Human-in-the-loop），所以獨立成三張表：
--   discovered_items          一個被發現的商品（依正規化名稱去重）
--   discovered_item_evidence  佐證文章（PTT 連結、標題、推文數、發文時間）
--   discovery_runs            每次探索的執行紀錄（比照 V23 trend_sync_runs）
--
-- 【狀態】NEW＝待處理；DISMISSED＝操作人員略過（之後再被發現也不會重新冒出來）；
-- CONVERTED＝已建立成商品（converted_product_id 指向該商品）。
-- =====================================================================

CREATE TABLE `discovered_items` (
  `id`                   bigint NOT NULL AUTO_INCREMENT,
  `normalized_name`      varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '去重用：NFKC、小寫、去除空白與標點後的標準名稱',
  `display_name`         varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'AI 給的標準名稱（第一次發現時的值）',
  `search_keyword`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '標題裡最常出現的寫法，查 PTT 熱度時使用的關鍵字',
  `category_hint`        varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 猜測的品類（大類/小類文字），僅供參考',
  `product_type_id`      bigint DEFAULT NULL COMMENT 'category_hint 對得上的小類；對不上為 NULL',
  `status`               enum('NEW','DISMISSED','CONVERTED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `mention_count`        int NOT NULL DEFAULT '0' COMMENT '最近一次探索時，近 7 天提及的文章數',
  `push_volume`          int NOT NULL DEFAULT '0' COMMENT '最近一次探索時，上述文章的淨推文量合計',
  `popularity_score`     decimal(5,2) DEFAULT NULL COMMENT 'PTT 熱度分數（與商品熱度同一套換算）；未查過為 NULL',
  `trend_score`          decimal(5,2) DEFAULT NULL,
  `trend_direction`      enum('UP','DOWN','STABLE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `window_volume`        int DEFAULT NULL COMMENT '熱度查詢時 90 天的討論量（貼文＋推文）',
  `buzz_checked_at`      datetime DEFAULT NULL,
  `first_seen_at`        datetime NOT NULL,
  `last_seen_at`         datetime NOT NULL,
  `model_name`           varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '抽取時使用的 Gemini 模型',
  `converted_product_id` bigint DEFAULT NULL,
  `dismiss_reason`       varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handled_by`           bigint DEFAULT NULL COMMENT '略過／建立商品的操作人員',
  `handled_at`           datetime DEFAULT NULL,
  `created_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_discovered_items_normalized_name` (`normalized_name`),
  KEY `idx_discovered_items_status_last_seen` (`status`, `last_seen_at`),
  KEY `fk_discovered_items_product_type` (`product_type_id`),
  KEY `fk_discovered_items_converted_product` (`converted_product_id`),
  KEY `fk_discovered_items_handled_by` (`handled_by`),
  CONSTRAINT `fk_discovered_items_product_type` FOREIGN KEY (`product_type_id`) REFERENCES `product_types` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_discovered_items_converted_product` FOREIGN KEY (`converted_product_id`) REFERENCES `products` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_discovered_items_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PTT 新品探索：被發現、尚未（或已）轉成商品的候選';

CREATE TABLE `discovered_item_evidence` (
  `id`           bigint NOT NULL AUTO_INCREMENT,
  `item_id`      bigint NOT NULL,
  `board`        varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `post_path`    varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PTT 文章路徑，例 /bbs/Lifeismoney/M.1750310171.A.4BE.html',
  `title`        varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `push_volume`  int NOT NULL DEFAULT '0',
  `posted_at`    datetime NOT NULL,
  `collected_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_discovered_item_evidence_item_post` (`item_id`, `post_path`),
  KEY `idx_discovered_item_evidence_posted_at` (`item_id`, `posted_at`),
  CONSTRAINT `fk_discovered_item_evidence_item` FOREIGN KEY (`item_id`) REFERENCES `discovered_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PTT 新品探索的佐證文章';

CREATE TABLE `discovery_runs` (
  `id`                     bigint NOT NULL AUTO_INCREMENT,
  `trigger_type`           enum('SCHEDULED','MANUAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status`                 enum('RUNNING','COMPLETED','FAILED','SKIPPED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at`             datetime NOT NULL,
  `finished_at`            datetime DEFAULT NULL,
  `post_count`             int NOT NULL DEFAULT '0' COMMENT '掃到的近 7 天文章數',
  `title_count`            int NOT NULL DEFAULT '0' COMMENT '過濾、去重後送給 AI 的標題數',
  `ai_call_count`          int NOT NULL DEFAULT '0',
  `extracted_count`        int NOT NULL DEFAULT '0' COMMENT 'AI 回傳的商品筆數（未驗證）',
  `rejected_count`         int NOT NULL DEFAULT '0' COMMENT '驗證不通過而丟棄的筆數（名稱不在引用標題內等）',
  `matched_existing_count` int NOT NULL DEFAULT '0' COMMENT '與既有商品名稱相符而略過的筆數',
  `new_count`              int NOT NULL DEFAULT '0' COMMENT '新增的 discovered_items',
  `updated_count`          int NOT NULL DEFAULT '0' COMMENT '已存在、本次再次被提及而更新的 discovered_items',
  `message`                varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `triggered_by`           bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_discovery_runs_started_at` (`started_at`),
  KEY `fk_discovery_runs_triggered_by` (`triggered_by`),
  CONSTRAINT `fk_discovery_runs_triggered_by` FOREIGN KEY (`triggered_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PTT 新品探索的執行紀錄';
