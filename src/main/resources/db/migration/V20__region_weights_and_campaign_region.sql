-- =====================================================================
-- V20：地域性影響評分——區域業務占比設定表 ＋ festive_campaigns 補上
-- 結構化的 region／region_coverage_ratio 欄位
--
-- 【背景】
-- WeatherRegionConfig 把台灣分成 NORTH／CENTRAL／SOUTH／EAST 四區個別
-- 監控天氣，WeatherSignal.region 也帶著這個資訊，但 WeatherCampaignSyncService
-- .syncOne() 只把 region 拿去組 campaign_code／campaign_name 這兩個字串，
-- festive_campaigns 本身沒有結構化欄位保存，ScoringService.buildMatchedCampaignSnapshot()
-- 也完全沒用到地域，導致「只要某一區出現符合的天氣訊號，全國商品一視同仁
-- 拿到一樣的加成」。這支 migration 補齊這塊落差（方案B＋D，2026-09-23決議）。
--
-- 【region_weights：各區域業務占比，可由管理端調整】
-- 四個區域固定（不開放新增/刪除列，比照 WeatherRegionConfig.REGION_CITIES
-- 的既有慣例，不做成可自由增刪的一般設定表），只開放調整 weight_percentage。
-- 種子資料先給等權重 25.00/25.00/25.00/25.00——目前沒有任何資料佐證實際
-- 業務占比該怎麼分配，不虛構一組數字，交由管理端在設定頁依實際營運調整
-- （例如南部：60%）。四筆加總必須為100，由SettingsService.updateRegionWeights()
-- 驗證，比照 updateEvaluationModeFactors() 「加總須為100」的既有作法。
--
-- 【festive_campaigns.region／region_coverage_ratio：僅 category=WEATHER 有值】
-- - region：這筆天氣檔期實際命中的區域代碼，結構化保存（先前只存在字串
--   campaign_code／campaign_name 裡，無法查詢比對）。FESTIVAL／SEASON類
--   檔期維持NULL，語意上代表「全國性、不限地域」。
-- - region_coverage_ratio：這次同步當下，「命中同一種天氣訊號類型的所有
--   區域」依 region_weights 加總出的業務占比（0~1，四區全中＝1.0）。
--   在WeatherCampaignSyncService同步當下計算並凍結寫入，不在計分當下
--   即時查表重算——比照WeightSnapshot「同一批資料，分數永遠重算得出
--   同一個結果」的既有再現性原則：日後即使管理端改了region_weights的
--   占比設定，已經算過的歷史檔期／已核准商品的節慶加成快照都不會被
--   追溯性地改變，只有下一次05:00同步之後才會採用新占比。
-- =====================================================================

CREATE TABLE `region_weights` (
  `region` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '區域代碼，對應 WeatherRegionConfig.REGION_CITIES 的 key（NORTH/CENTRAL/SOUTH/EAST）',
  `weight_percentage` decimal(5,2) NOT NULL COMMENT '此區域的業務占比（%），四區加總須為100，由Service層驗證',
  `updated_at` datetime DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL COMMENT '最後一次調整占比的使用者',
  PRIMARY KEY (`region`),
  KEY `fk_region_weights_updated_by` (`updated_by`),
  CONSTRAINT `fk_region_weights_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='四大天氣監控區域的業務占比設定，WeatherCampaignSyncService計算region_coverage_ratio時的資料來源';

INSERT INTO `region_weights` (`region`, `weight_percentage`)
SELECT 'NORTH', 25.00 WHERE NOT EXISTS (SELECT 1 FROM `region_weights` WHERE `region` = 'NORTH');

INSERT INTO `region_weights` (`region`, `weight_percentage`)
SELECT 'CENTRAL', 25.00 WHERE NOT EXISTS (SELECT 1 FROM `region_weights` WHERE `region` = 'CENTRAL');

INSERT INTO `region_weights` (`region`, `weight_percentage`)
SELECT 'SOUTH', 25.00 WHERE NOT EXISTS (SELECT 1 FROM `region_weights` WHERE `region` = 'SOUTH');

INSERT INTO `region_weights` (`region`, `weight_percentage`)
SELECT 'EAST', 25.00 WHERE NOT EXISTS (SELECT 1 FROM `region_weights` WHERE `region` = 'EAST');

-- ---------------------------------------------------------------------
-- festive_campaigns 補欄位
-- ---------------------------------------------------------------------
ALTER TABLE `festive_campaigns`
  ADD COLUMN `region` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '僅category=WEATHER時有值；命中的區域代碼，FESTIVAL/SEASON維持NULL代表全國性' AFTER `weather_confidence`,
  ADD COLUMN `region_coverage_ratio` decimal(5,4) DEFAULT NULL COMMENT '僅category=WEATHER時有值；同步當下依region_weights算出的區域業務占比覆蓋率(0~1)，於同步當下凍結寫入，不即時重算' AFTER `region`;
