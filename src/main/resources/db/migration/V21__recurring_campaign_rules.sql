-- =====================================================================
-- V21：節慶／季節檔期改為「每年固定」的日期規則（2026-09-24 檔期規則改版）
--
-- 【背景】
-- 1. 節慶／季節檔期原本存具體年度的 start_date／end_date，每年都要重建一筆，
--    而且 campaign_status 從不依日期自動推進（沒有排程），建立後停在 UPCOMING，
--    計分只取 PREPARING／ACTIVE，節慶加成實際上幾乎不會生效。
-- 2. 改存「日期規則」（固定國曆日、第 N 個星期幾、農曆日期、節氣），每年實際
--    的起訖日（occurrence）與狀態由 CampaignOccurrenceResolver 即時推算，
--    不再依賴資料表裡存的狀態，也不需要排程（決議 D2／D10）。
-- 3. 天氣型（WEATHER）仍由 WeatherCampaignSyncService 產生並寫入具體日期，
--    規則欄位對天氣型維持 NULL，不可手動建立或修改（決議 D1）。
--
-- 【農曆與節氣】
-- 不存在資料庫：農曆換算與節氣日期由內建對照表（classpath:campaign/）提供，
-- 範圍 2000–2099。原規劃的 ICU4J 實測會把 2027、2030 年春節算錯一天，已改用
-- 內建表（見 LunarCalendarService 類別註解）。
--
-- 【不修改舊 migration】部署流程為 DROP DATABASE＋CREATE DATABASE，現行 migration
-- 沒有任何檔期種子資料，所以不需要把舊的具體日期轉成規則。
-- =====================================================================

ALTER TABLE `festive_campaigns`
  MODIFY COLUMN `start_date` date NULL COMMENT '僅category=WEATHER使用；FESTIVAL/SEASON改由日期規則推算，維持NULL',
  MODIFY COLUMN `end_date` date NULL COMMENT '僅category=WEATHER使用；FESTIVAL/SEASON改由日期規則推算，維持NULL',
  ADD COLUMN `date_rule_type` enum('FIXED_DATE','NTH_WEEKDAY','LUNAR_DATE','SOLAR_TERM') COLLATE utf8mb4_unicode_ci NULL COMMENT '日期規則類型：固定國曆日/第N個星期幾/農曆日期/節氣；WEATHER維持NULL；SEASON只允許FIXED_DATE' AFTER `category`,
  ADD COLUMN `rule_month` tinyint NULL COMMENT 'FIXED_DATE/NTH_WEEKDAY為國曆月(1-12)；LUNAR_DATE為農曆月(1-12，一律指非閏月)' AFTER `date_rule_type`,
  ADD COLUMN `rule_day` tinyint NULL COMMENT 'FIXED_DATE為國曆日(1-31)；LUNAR_DATE為農曆日(1-30，超過該月天數取月末)' AFTER `rule_month`,
  ADD COLUMN `rule_week_ordinal` tinyint NULL COMMENT 'NTH_WEEKDAY：第幾個(1-4)，-1=最後一個' AFTER `rule_day`,
  ADD COLUMN `rule_weekday` tinyint NULL COMMENT 'NTH_WEEKDAY：ISO星期(1=週一...7=週日)' AFTER `rule_week_ordinal`,
  ADD COLUMN `rule_solar_term` enum('QINGMING','DONGZHI') COLLATE utf8mb4_unicode_ci NULL COMMENT 'SOLAR_TERM：節氣代碼(清明/冬至)' AFTER `rule_weekday`,
  ADD COLUMN `rule_offset_days` smallint NOT NULL DEFAULT 0 COMMENT '基準日偏移天數(-30~30)，例：除夕=農曆1/1偏移-1' AFTER `rule_solar_term`,
  ADD COLUMN `duration_days` smallint NULL COMMENT 'FESTIVAL必填：檔期持續天數(1-60)，含開始日' AFTER `rule_offset_days`,
  ADD COLUMN `end_month` tinyint NULL COMMENT 'SEASON必填：結束月(1-12)；小於開始月日時代表跨年' AFTER `duration_days`,
  ADD COLUMN `end_day` tinyint NULL COMMENT 'SEASON必填：結束日(1-31)，超過該月天數取月末(31=月底)' AFTER `end_month`,
  ADD COLUMN `observed_holiday_rule` enum('NONE','TW_STATUTORY') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '補假規則：TW_STATUTORY=逢週六前一上班日、逢週日次一上班日補假' AFTER `end_day`,
  ADD COLUMN `expand_long_weekend` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否把相鄰週末與補假日併入檔期期間(連假展開)，預設否' AFTER `observed_holiday_rule`,
  ADD COLUMN `manual_override_cycle` smallint NULL COMMENT '手動覆蓋生效的週期年(見occurrence cycle)，進入下一週期自動失效；WEATHER不使用' AFTER `is_manual_override`;

-- 既有 CHECK chk_festive_campaigns_date_range (end_date >= start_date) 保留：
-- 兩欄皆為 NULL 時 CHECK 結果為 UNKNOWN，MySQL 不視為違反（已以 MySQL 8.0 實測可寫入）。

CREATE TABLE `festive_campaign_regions` (
  `campaign_id` bigint NOT NULL COMMENT '對應festive_campaigns.id',
  `region` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NORTH/CENTRAL/SOUTH/EAST，對照region_weights.region',
  PRIMARY KEY (`campaign_id`,`region`),
  KEY `fk_festive_campaign_regions_region` (`region`),
  CONSTRAINT `fk_festive_campaign_regions_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `festive_campaigns` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_festive_campaign_regions_region` FOREIGN KEY (`region`) REFERENCES `region_weights` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='節慶/季節檔期的受影響區域；沒有任何列＝全國性（覆蓋率1.0）。WEATHER檔期不使用此表';

CREATE TABLE `festive_campaign_occurrence_overrides` (
  `campaign_id` bigint NOT NULL COMMENT '對應festive_campaigns.id',
  `cycle_year` smallint NOT NULL COMMENT '週期年（國曆規則＝國曆年；農曆規則＝農曆年，以該年正月初一所在的國曆年表示）',
  `start_date` date NOT NULL COMMENT '覆寫後的開始日',
  `end_date` date NOT NULL COMMENT '覆寫後的結束日',
  `note` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '覆寫原因，例：依人事行政總處公告春節補假改至2/13',
  `updated_by` bigint DEFAULT NULL COMMENT '最後一次調整覆寫的使用者',
  `updated_at` datetime DEFAULT NULL COMMENT '最後更新時間',
  PRIMARY KEY (`campaign_id`,`cycle_year`),
  KEY `fk_campaign_occ_override_user` (`updated_by`),
  CONSTRAINT `fk_campaign_occ_override_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `festive_campaigns` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_campaign_occ_override_user` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `chk_campaign_occ_override_range` CHECK (`end_date` >= `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='規則推算不出的年度例外（人事行政總處彈性放假、春節補假「得」前得後、節氣誤差等），由管理端人工覆寫';
