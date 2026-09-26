-- =====================================================================
-- V26：天氣加成（2026-09 規格第 1 節，2026-09-25 決議）
--
-- 決議：系統不再有「天氣檔期」，檔期只有節慶與季節；天氣改為依天氣數據直接計算的
-- 獨立加成，與節慶加成並列，不進七因子權重池：
--
--   最終分數 = 加權總分 + 節慶加成 + 天氣加成
--   天氣加成 = 天氣分(0~100) ÷ 100 × 加成上限（預設 5 分）
--   天氣分   = 歷史天氣分 × 歷史比重(預設60%) + 預測天氣分 × 預測比重(預設40%)
--   歷史／預測天氣分 = 各區「逐日命中分數」在時間窗口內的平均 × 100，
--                     再依 region_weights 四區業務占比加權平均
--   逐日命中分數 = 當天該區天氣訊號（WeatherNormalizer.classifyDay）對應到商品標籤的
--                  weather_signal_tag_mappings 最高 match_tier 權重（未命中＝0，v1 不疊加）
--
-- 1. daily_weather_metrics：四區每日氣象數值（過去 30 天＋未來預報）
--    規格原本命名 daily_weather_history；因為同一張表也存未來預報（加成計算需要，
--    且不能讓每次評分都去打外部 API），改名為中性的 daily_weather_metrics。
--    欄位形狀沿用 DailyWeatherMetrics record。同一區同一天只有一列，每次同步 upsert
--    （預報會隨日期逼近而修正；過去日期也可能因模型版本更新而微調，以最新一次為準）。
--
-- 2. weather_boost_settings：歷史／預測比重與加成上限（單列設定表）
--    比照 region_weights 的模式（獨立小表＋Service 層驗證比重加總=100），
--    不放進 system_settings／七因子權重，避免誤被當成第八個計分因子。
--
-- 3. product_evaluations.weather_boost、review_records.weather_boost_snapshot／
--    weather_boost_detail_snapshot：與節慶加成相同的「即時值／審核快照」雙軌讀取。
--
-- 4. 移除天氣檔期：刪除 category=WEATHER 的檔期（含其標籤；區域與逐年覆寫為
--    ON DELETE CASCADE），category 收斂為 FESTIVAL／SEASON，並移除只有天氣檔期
--    使用的欄位（start_date／end_date 自 V21 起只給天氣檔期用）。
--    已凍結在 review_records.matched_campaign_snapshot（JSON）裡的歷史天氣檔期
--    不回改——審核快照不可覆寫。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 每日氣象數值
-- ---------------------------------------------------------------------
CREATE TABLE `daily_weather_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '唯一識別碼',
  `region` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '區域代碼 NORTH/CENTRAL/SOUTH/EAST，對照 region_weights.region',
  `weather_date` date NOT NULL COMMENT '氣象日期（台灣時間）；早於今天＝歷史，今天起＝預報',
  `apparent_temperature_max` decimal(5,2) DEFAULT NULL COMMENT '日最高體感溫度（°C，區域代表城市平均）',
  `apparent_temperature_min` decimal(5,2) DEFAULT NULL COMMENT '日最低體感溫度（°C）',
  `humidity_mean` decimal(5,2) DEFAULT NULL COMMENT '日平均相對濕度（%）',
  `precipitation_sum` decimal(6,2) DEFAULT NULL COMMENT '日累積雨量（mm）',
  `precipitation_probability_max` decimal(5,2) DEFAULT NULL COMMENT '日最高降雨機率（%）；過去日期可能為 NULL',
  `wind_speed_max` decimal(5,2) DEFAULT NULL COMMENT '日最大風速（km/h）',
  `fetched_at` datetime NOT NULL COMMENT '最近一次從 Open-Meteo 取得此列的時間（畫面顯示「資料更新時間」）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_weather_metrics_region_date` (`region`, `weather_date`),
  KEY `idx_daily_weather_metrics_date` (`weather_date`),
  CONSTRAINT `fk_daily_weather_metrics_region` FOREIGN KEY (`region`) REFERENCES `region_weights` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='四區每日氣象數值（過去約30天＋未來預報），天氣加成的資料來源；每日 05:00 同步 upsert';

-- ---------------------------------------------------------------------
-- 2. 天氣加成設定（單列）
-- ---------------------------------------------------------------------
CREATE TABLE `weather_boost_settings` (
  `id` tinyint NOT NULL COMMENT '固定為 1（單列設定表）',
  `history_weight_percentage` decimal(5,2) NOT NULL DEFAULT '60.00' COMMENT '歷史天氣分比重（%）；與預測比重加總須為100，由Service層驗證',
  `forecast_weight_percentage` decimal(5,2) NOT NULL DEFAULT '40.00' COMMENT '預測天氣分比重（%）',
  `boost_cap` decimal(4,2) NOT NULL DEFAULT '5.00' COMMENT '天氣加成上限（分）：天氣分100時的加成分數，範圍0~10',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_weather_boost_settings_updated_by` (`updated_by`),
  CONSTRAINT `fk_weather_boost_settings_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='天氣加成設定：歷史/預測比重與加成上限；不屬於七因子權重';

INSERT INTO `weather_boost_settings` (`id`, `history_weight_percentage`, `forecast_weight_percentage`, `boost_cap`)
VALUES (1, 60.00, 40.00, 5.00);

-- ---------------------------------------------------------------------
-- 3. 評估結果與審核快照
-- ---------------------------------------------------------------------
ALTER TABLE `product_evaluations`
  ADD COLUMN `weather_boost` decimal(5,2) NOT NULL DEFAULT '0.00'
      COMMENT '天氣加成分數（V26），無資料或未命中時為0' AFTER `festival_boost`,
  MODIFY COLUMN `final_score` decimal(5,2) DEFAULT NULL
      COMMENT '最終分數＝total_score＋festival_boost＋weather_boost，已審核通過商品的此值改讀review_records的Snapshot凍結值';

ALTER TABLE `review_records`
  ADD COLUMN `weather_boost_snapshot` decimal(5,2) DEFAULT NULL
      COMMENT '審核當時的天氣加成分數快照（V26）；V26 前的紀錄為 NULL' AFTER `festival_boost_snapshot`,
  ADD COLUMN `weather_boost_detail_snapshot` json DEFAULT NULL
      COMMENT '審核當時的天氣加成明細快照（歷史/預測分、比重、上限、命中標籤、資料期間）' AFTER `weather_boost_snapshot`;

-- ---------------------------------------------------------------------
-- 4. 移除天氣檔期
-- ---------------------------------------------------------------------
-- 先解除評估結果對天氣檔期的參照（FK），加成於應用程式啟動時的重算中重新計算。
UPDATE `product_evaluations` pe
  JOIN `festive_campaigns` fc ON fc.`id` = pe.`matched_campaign_id`
   SET pe.`matched_campaign_id` = NULL,
       pe.`festival_boost` = 0.00,
       pe.`final_score` = pe.`total_score`
 WHERE fc.`category` = 'WEATHER';

DELETE fct FROM `festive_campaign_tags` fct
  JOIN `festive_campaigns` fc ON fc.`id` = fct.`campaign_id`
 WHERE fc.`category` = 'WEATHER';

DELETE FROM `festive_campaigns` WHERE `category` = 'WEATHER';

ALTER TABLE `festive_campaigns` DROP CHECK `chk_festive_campaigns_date_range`;

ALTER TABLE `festive_campaigns`
  MODIFY COLUMN `category` enum('FESTIVAL','SEASON') COLLATE utf8mb4_unicode_ci NOT NULL
      COMMENT 'FESTIVAL=節慶／SEASON=季節（V26 起天氣不再是檔期，改為獨立的天氣加成）',
  DROP COLUMN `start_date`,
  DROP COLUMN `end_date`,
  DROP COLUMN `weather_confidence`,
  DROP COLUMN `region`,
  DROP COLUMN `region_coverage_ratio`;
