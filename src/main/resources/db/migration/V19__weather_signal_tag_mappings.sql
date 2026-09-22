-- =====================================================================
-- V19：天氣訊號類型 → 商品標籤對照表，取代寫死在
-- WeatherCampaignSyncService.WEATHER_TAG_MAPPING 裡的靜態常數
--
-- 【為什麼要新增這張表】
-- 節慶/季節檔期的標籤命中規則（festive_campaign_tags）管理層本來就能在
-- 設定頁自行調整，但天氣訊號要對應到哪些商品標籤、命中等級多高，原本
-- 只能改 Java 程式碼（WEATHER_TAG_MAPPING）重新部署才能調整。這張表把
-- 這份對照表資料庫化，讓 WeatherCampaignSyncService 從查表變成查資料庫，
-- 管理層可以透過新的 /api/settings/weather-signal-tags 端點自行增刪。
--
-- 【is_system_default 種子資料：直接沿用 WEATHER_TAG_MAPPING 原本 9 筆內容】
-- 這 9 筆是系統原本就依賴、拿掉會讓天氣同步功能整個失效的預設資料（跟
-- V16 migration 處理 product_types／risk_options 的判斷原則一致：這是
-- 系統本身「長什麼樣子」的一部分，不是可被清空重灌的示範假資料），因此
-- 直接寫在這支 migration，不放進 seed_data 腳本，並標記
-- is_system_default=1（不可刪除，僅可停用，比照 risk_options 既有慣例）。
-- 使用 INSERT ... WHERE NOT EXISTS 維持冪等，比照 V9／V15／V16 既有寫法。
--
-- NORMAL 刻意不建立任何列：一般天氣不該命中任何商品，這條業務規則由
-- SettingsService 在建立/編輯時擋下（見 WeatherSignalTagMapping 類別
-- 註解），這裡的 DB 型別本身仍完整鏡射 WeatherSignalType 全部 10 個值，
-- 不用更窄的 ENUM 清單在 schema 層級重複這條規則。
-- =====================================================================

CREATE TABLE `weather_signal_tag_mappings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '對照唯一識別碼',
  `weather_signal_type` enum('HOT','HUMID_HOT','HUMID','RAINY','HEAVY_RAIN','STRONG_WIND','COOL','COLD','DRY_COOL','NORMAL')
    COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '天氣訊號類型，對應 enums/WeatherSignalType.java；NORMAL 由 Service 層擋下，實際不會有資料列',
  `tag` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '對應的商品標籤，比對 products.campaign_tags',
  `match_tier` enum('CORE','GENERAL','WEAK') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '命中權重層級，語意同 festive_campaign_tags.match_tier',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否生效中；WeatherCampaignSyncService 同步時僅讀取生效中的列',
  `is_system_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否為系統預設對照（9筆，本支migration寫入），僅可停用不可刪除',
  `created_by` bigint DEFAULT NULL COMMENT '建立這筆對照的使用者，系統預設列固定為NULL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_weather_signal_tag_mappings_type_tag` (`weather_signal_type`, `tag`),
  KEY `idx_weather_signal_tag_mappings_type` (`weather_signal_type`),
  KEY `fk_weather_signal_tag_mappings_created_by` (`created_by`),
  KEY `fk_weather_signal_tag_mappings_updated_by` (`updated_by`),
  CONSTRAINT `fk_weather_signal_tag_mappings_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_weather_signal_tag_mappings_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='天氣訊號類型對應的商品標籤與命中權重，WeatherCampaignSyncService同步天氣檔期的資料來源，取代原本的WEATHER_TAG_MAPPING靜態常數';

-- ---------------------------------------------------------------------
-- 系統預設對照：原 WEATHER_TAG_MAPPING 的 9 筆內容
-- ---------------------------------------------------------------------
INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'RAINY', '雨具', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'RAINY' AND `tag` = '雨具');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'RAINY', '防水', 'GENERAL', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'RAINY' AND `tag` = '防水');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HEAVY_RAIN', '雨具', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HEAVY_RAIN' AND `tag` = '雨具');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HEAVY_RAIN', '防水', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HEAVY_RAIN' AND `tag` = '防水');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HOT', '涼感', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HOT' AND `tag` = '涼感');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HOT', '消暑', 'GENERAL', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HOT' AND `tag` = '消暑');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HUMID_HOT', '涼感', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HUMID_HOT' AND `tag` = '涼感');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HUMID_HOT', '除濕', 'GENERAL', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HUMID_HOT' AND `tag` = '除濕');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'HUMID', '除濕', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'HUMID' AND `tag` = '除濕');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'STRONG_WIND', '防風', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'STRONG_WIND' AND `tag` = '防風');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'COLD', '保暖', 'CORE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'COLD' AND `tag` = '保暖');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'COOL', '保暖', 'WEAK', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'COOL' AND `tag` = '保暖');

INSERT INTO `weather_signal_tag_mappings` (`weather_signal_type`, `tag`, `match_tier`, `is_active`, `is_system_default`)
SELECT 'DRY_COOL', '保暖', 'GENERAL', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `weather_signal_tag_mappings` WHERE `weather_signal_type` = 'DRY_COOL' AND `tag` = '保暖');
