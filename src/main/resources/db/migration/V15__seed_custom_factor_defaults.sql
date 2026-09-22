-- =====================================================================
-- V15：建立「環保包裝評級」「社群聲量熱度」兩個示範用自訂計分因子
--
-- 【為什麼這是 migration 而不是 seed data script】
-- 這裡建立的東西定義「系統裡存在哪些計分因子、怎麼算」，跟
-- evaluation_modes／evaluation_factors 七個固定因子（V1、V3 migration）
-- 是同一個層級的系統設定，不是可以隨便被清空重灌的展示用假資料：
--   - custom_field_definitions／factor_definitions 本身：定義本體，
--     不綁定任何特定假資料商品，清空種子資料不該連這個定義都清掉。
--   - evaluation_factors：CUSTOM 模式的權重表，跟三套固定模式一樣屬於
--     系統設定表，本來就不在 clear_seed_data.sql 的清空範圍內。
--   - product_type_score_bands：TARGET_BAND_NORMALIZE 策略運算需要的
--     全域區間，同樣是系統設定，不是假資料。
--
-- 商品「實際填答」這兩個自訂屬性的值（product_custom_field_values）
-- 才是真正的展示用假資料，那筆資料留在 seed_data_full_v4.sql，會被
-- clear_seed_data.sql 正常清空重灌；custom_field_applicable_types
-- （這兩個屬性限定哪些商品分類可用）因為外鍵指向 product_types，而
-- product_types 本身也是 seed_data 建立的假資料（不是 migration 建的），
-- 所以同樣留在 seed_data 腳本裡，跟 product_types 一起清空重灌，避免
-- migration 在全新資料庫上因為 product_types 還不存在而外鍵失敗。
--
-- 【冪等性】比照 V3 migration 建立 evaluation_modes／evaluation_factors
-- 的既有寫法，全部使用 INSERT ... SELECT ... WHERE NOT EXISTS，可以在
-- 任何時候重複執行（例如同一支 migration 因故被重跑、或未來要手動
-- re-apply）而不會產生重複資料或主鍵衝突。
--
-- 【created_by / updated_by 為什麼固定寫 NULL】
-- migration 在全新資料庫上可能早於任何 seed data 執行，此時 app_users
-- 表還沒有任何一筆資料——若這裡寫死 created_by=1，在全新資料庫上會直接
-- 因為外鍵找不到 app_users.id=1 而失敗。這點比照 evaluation_factors
-- 表本身「三套固定模式的 updated_at/updated_by 維持 NULL」的既有慣例：
-- 系統內建的預設值不歸屬於任何一個操作者。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. custom_field_definitions：2 筆自訂商品屬性題目定義
-- ---------------------------------------------------------------------
INSERT INTO `custom_field_definitions`
  (`field_code`, `field_name`, `help_text`, `field_type`, `is_required`, `is_active`, `scale_labels`, `created_by`, `created_at`)
SELECT
  'ECO_PACKAGING_SCORE', '環保包裝評級',
  '依包裝材質的可回收／可分解程度評分，1~5分，可參考下拉選單說明', 'SCALE_1_5', 0, 1,
  '{"1": "完全無環保設計，一次性複合材質包裝", "2": "部分材質可回收，但未分類設計", "3": "多數材質可回收，有基本分類標示", "4": "全面採用可回收/可分解包材", "5": "循環包裝或裸裝，近乎零額外包裝"}',
  NULL, '2026-09-20 11:00:00'
WHERE NOT EXISTS (
  SELECT 1 FROM `custom_field_definitions` WHERE `field_code` = 'ECO_PACKAGING_SCORE'
);

INSERT INTO `custom_field_definitions`
  (`field_code`, `field_name`, `help_text`, `field_type`, `is_required`, `is_active`, `scale_labels`, `created_by`, `created_at`)
SELECT
  'SOCIAL_BUZZ_MENTIONS', '社群聲量（近30天提及次數）',
  '近30天內社群平台（含社團、論壇）提及此商品或同類商品的次數估計值', 'RAW_NUMBER', 0, 1,
  NULL, NULL, '2026-09-20 11:05:00'
WHERE NOT EXISTS (
  SELECT 1 FROM `custom_field_definitions` WHERE `field_code` = 'SOCIAL_BUZZ_MENTIONS'
);

-- ---------------------------------------------------------------------
-- 2. factor_definitions：2 筆自訂計分因子，各自綁定上方一個自訂商品屬性
-- 當資料源（custom_field_definition_id，透過 field_code 查回去，不寫死
-- id，避免依賴 custom_field_definitions 的 AUTO_INCREMENT 剛好是多少）。
-- data_source_code 依規則留 NULL（跟 custom_field_definition_id 二選一，
-- 見 V13 migration）。strategy_params 留 NULL，沿用各策略自己的預設值
-- （MANUAL_SCALE 預設 ×20，TARGET_BAND_NORMALIZE 不需要參數）。
-- ---------------------------------------------------------------------
INSERT INTO `factor_definitions`
  (`factor_code`, `factor_name`, `category`, `strategy_code`, `data_source_code`,
   `custom_field_definition_id`, `strategy_params`, `is_active`, `is_system_default`,
   `previous_version_id`, `created_by`, `created_at`)
SELECT
  'ECO_PACKAGING', '環保包裝評級', 'SUSTAINABILITY', 'MANUAL_SCALE', NULL,
  (SELECT id FROM `custom_field_definitions` WHERE `field_code` = 'ECO_PACKAGING_SCORE'),
  NULL, 1, 0, NULL, NULL, '2026-09-20 14:00:00'
WHERE NOT EXISTS (
  SELECT 1 FROM `factor_definitions` WHERE `factor_code` = 'ECO_PACKAGING'
);

INSERT INTO `factor_definitions`
  (`factor_code`, `factor_name`, `category`, `strategy_code`, `data_source_code`,
   `custom_field_definition_id`, `strategy_params`, `is_active`, `is_system_default`,
   `previous_version_id`, `created_by`, `created_at`)
SELECT
  'SOCIAL_BUZZ', '社群聲量熱度', 'FORECAST', 'TARGET_BAND_NORMALIZE', NULL,
  (SELECT id FROM `custom_field_definitions` WHERE `field_code` = 'SOCIAL_BUZZ_MENTIONS'),
  NULL, 1, 0, NULL, NULL, '2026-09-20 14:05:00'
WHERE NOT EXISTS (
  SELECT 1 FROM `factor_definitions` WHERE `factor_code` = 'SOCIAL_BUZZ'
);

-- ---------------------------------------------------------------------
-- 3. product_type_score_bands：SOCIAL_BUZZ 的 TARGET_BAND_NORMALIZE
-- 需要目標區間才能正規化，這裡給一筆全域預設（0次=0分，300次以上=100分）。
-- product_type_id 給 NULL（全域），不依賴任何特定 product_types 列存在，
-- 在全新資料庫（product_types 尚未由 seed data 建立）上也能安全套用。
-- ---------------------------------------------------------------------
INSERT INTO `product_type_score_bands`
  (`product_type_id`, `factor_code`, `lower_bound`, `upper_bound`, `version`, `is_active`, `source_mode`, `updated_at`, `updated_by`)
SELECT NULL, 'SOCIAL_BUZZ', 0.0000, 300.0000, 1, 1, 'MANUAL', '2026-09-20 14:05:00', NULL
WHERE NOT EXISTS (
  SELECT 1 FROM `product_type_score_bands` WHERE `product_type_id` IS NULL AND `factor_code` = 'SOCIAL_BUZZ'
);

-- ---------------------------------------------------------------------
-- 4. evaluation_factors：CUSTOM（自訂）模式納入上述 2 個自訂因子。
--
-- 原本 7 個固定因子在 CUSTOM 模式各佔 14.28~14.29（七等分，加總100）；
-- 加入 2 個自訂因子後改為 9 等分（11.11 × 8 + 11.12 = 100.00，尾數同樣
-- 補在最後一項，沿用 V3 migration「七等分無法整除，尾數補足」的既有慣例）。
-- 只動 CUSTOM 模式，不影響 BALANCED／VOLUME／PROFIT 三套固定模式。
--
-- UPDATE 天生冪等，可重複執行；2 筆 INSERT 比照 V3 migration 既有寫法
-- 使用 WHERE NOT EXISTS。
-- ---------------------------------------------------------------------
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'MARGIN_RATE';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'DISCOUNT_DEPTH';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'SUPPLY_STABILITY';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'AUDIENCE_MATCH';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'HISTORY_FULFILLMENT';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'PURCHASE_RATE';
UPDATE `evaluation_factors` SET `weight` = 11.11
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'TREND_HEAT';

INSERT INTO `evaluation_factors`
  (`evaluation_mode_id`, `factor_code`, `factor_name`, `category`, `weight`, `description`, `sort_order`)
SELECT (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
  'ECO_PACKAGING', '環保包裝評級', 'SUSTAINABILITY', 11.11, '自訂因子：包裝可回收／可分解程度人工評分', 8
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_factors`
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'ECO_PACKAGING'
);

INSERT INTO `evaluation_factors`
  (`evaluation_mode_id`, `factor_code`, `factor_name`, `category`, `weight`, `description`, `sort_order`)
SELECT (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
  'SOCIAL_BUZZ', '社群聲量熱度', 'FORECAST', 11.12, '自訂因子：近30天社群提及次數，依全域目標區間正規化', 9
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_factors`
  WHERE `evaluation_mode_id` = (SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1)
    AND `factor_code` = 'SOCIAL_BUZZ'
);
