-- =====================================================================
-- V16：補回兩張表「本來就該是系統預設、卻被誤放進 seed_data 腳本」的資料
--
-- 【觸發原因】
-- 稽核 seed_data_full_v4.sql／clear_seed_data_v4.sql 時發現：
--   1. product_types.is_system_default 欄位註解明寫「是否為系統預設商品
--      類型（9類，seed資料寫入）」——schema 設計時就認定有 9 筆大類是
--      系統預設，但實際上 seed_data_full_v4.sql 用一般 INSERT 建立全部
--      39 筆（9 大類＋30 小類），且完全沒有帶 is_system_default 欄位，
--      全部隱性落成預設值 0。這 9 筆大類因此：
--        (a) 從未真的被標記為系統預設，前端「系統預設分類」的識別邏輯
--            對這 9 筆完全失效；
--        (b) clear_seed_data.sql 用 TRUNCATE 清空 product_types 時，連
--            這 9 筆分類骨架也會被清掉，若清空後不重新匯入 seed data，
--            系統會連最基本的商品分類都不存在。
--   2. risk_options 目前唯一被 migration 正確處理的系統預設只有 V9 建立
--      的「其他」（自由文字選項）。但 seed_data_full_v4.sql 裡另外還有
--      3 筆一般風險選項（id=1~3：實際供貨風險／商品品質與客訴風險／
--      市場不確定性與需求變動風險）也把 is_system_default 寫成 1，
--      created_by 寫 NULL——這是「系統預設」欄位語意上該有的樣子
--      （比照 RiskOptionCreateRequest 類別註解：新增的風險選項一律視為
--      自訂項目 isSystemDefault=false，只有系統既有建立的才會是 true），
--      但實際建立的地方卻是可以被整批清空重灌的 seed script，而不是
--      migration。clear_seed_data.sql 原本只用 is_free_text_option 篩選
--      保留對象，沒有涵蓋這 3 筆，也是同一個問題的另一個徵狀。
--
-- 這張表跟 V15 開頭列的判斷原則是同一件事：「定義系統本身長什麼樣子」
-- 的資料，不是「示範系統怎麼被使用」的假資料，不該被 clear_seed_data.sql
-- 清空重灌，因此比照 V9／V15 的既有寫法搬進 migration。
--
-- 【為什麼兩張表都明確寫死 id，而不是像 V9「其他」選項一樣讓
-- AUTO_INCREMENT 自然產生】
-- seed_data_full_v4.sql 裡有大量既有假資料直接寫死引用這些 id：
--   - product_types：30 筆小類的 `parent_id` 欄位直接寫 1/5/9/13/17/22/
--     26/31/36；products 表也有商品直接掛在大類本身；
--     custom_field_applicable_types 的 `root_product_type_id` 直接寫
--     22、26。
--   - risk_options：review_risks 的 `risk_option_id` 直接寫 1、2、3
--     （例如 (77,1,...)/(79,2,...)/(81,3,...)）。
-- 這些既有假資料短期內不值得為了「不寫死 id」而全部改寫成子查詢
-- （改動範圍會遠超過這次要修的問題本身），所以這裡選擇讓 migration
-- 明確帶入跟 seed data 原本一致的 id，維持向下相容；用
-- INSERT ... SELECT ... WHERE NOT EXISTS（以 id 判斷是否已存在）維持
-- 冪等，比照 V9／V15 既有慣例。
--
-- 【created_by／created_at 為什麼這樣寫】
-- created_by 固定 NULL：系統內建預設值不歸屬於任何操作者，比照 V15
-- 對 custom_field_definitions／factor_definitions 的既有慣例。
-- created_at 使用 NOW()：這批資料在「這個 migration 實際被套用的時間點」
-- 才第一次於這個資料庫上出現是符合事實的寫法，比照 V9 對「其他」選項的
-- 既有做法（同樣用 NOW()），不虛構一個早於 migration 實際執行時間的
-- 歷史時間戳。
--
-- 【這次沒有一併搬進來的東西：custom_field_applicable_types】
-- V15 註解曾提到 custom_field_applicable_types（ECO_PACKAGING_SCORE 限定
-- 日用品(22)／家庭用品(26) 兩大類）留在 seed_data 腳本，原因是「當時
-- product_types 本身也是 seed_data 建立的假資料，搬進 migration 會在全新
-- 資料庫上因為 product_types 還不存在而外鍵失敗」。這次把 9 筆大類搬進
-- migration 後，那個外鍵順序的技術限制確實解除了，但這不代表這筆資料
-- 因此變成「系統預設」：
--   - custom_field_applicable_types 沒有 is_system_default 這類欄位，
--     且 SettingsService 已提供完整的新增／刪除 API，是管理層隨時可調整
--     的一般設定，不是建立當下就固定身分、不可再變動的系統預設。
--   - 這張表「完全沒有列＝適用全部品類」本身就是合法且完整的預設狀態
--     （SOCIAL_BUZZ_MENTIONS 目前就是 0 筆，運作正常）；ECO_PACKAGING_
--     SCORE 限定兩大類，是這份種子資料為了示範「品類範圍限制」這個
--     功能特意做的情境設定，不是系統開箱必須具備的前提。
-- 因此 custom_field_applicable_types 維持留在 seed_data_full_v5.sql，
-- 隨假資料一起清空重灌；詳見該檔案對應段落的更新註解。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. product_types：9 筆大類（level=1），系統預設商品分類骨架
-- ---------------------------------------------------------------------
INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 1, '生鮮食品', '生鮮食品大類', 1, 1, NULL, 1, 1, 'CHILLED', 1, 'D8_30', NULL, 5, NULL, NULL, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 1);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 5, '農產品', '農產品大類', 1, 1, NULL, 1, 2, 'NORMAL', 1, 'D8_30', NULL, 7, NULL, NULL, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 5);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 9, '冷凍食品', '冷凍食品大類', 1, 1, NULL, 1, 3, 'FROZEN', 1, 'D90_PLUS', NULL, 14, NULL, NULL, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 9);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 13, '地方名產', '地方名產大類', 1, 1, NULL, 1, 4, 'NORMAL', 1, 'D31_90', NULL, 30, NULL, NULL, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 13);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 17, '常溫食品', '常溫食品大類', 1, 1, NULL, 1, 5, 'NORMAL', 1, 'D90_PLUS', NULL, 21, NULL, NULL, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 17);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 22, '日用品', '日用品大類', 1, 1, NULL, 1, 6, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 22);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 26, '家庭用品', '家庭用品大類', 1, 1, NULL, 1, 7, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 26);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 31, '3C產品', '3C產品大類', 1, 1, NULL, 1, 8, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 31);

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
SELECT 36, '生活休閒', '生活休閒大類', 1, 1, NULL, 1, 9, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `product_types` WHERE `id` = 36);

-- ---------------------------------------------------------------------
-- 2. risk_options：3 筆一般風險選項（純人工判斷，無對應 Gate），系統預設
-- ---------------------------------------------------------------------
INSERT INTO `risk_options`
  (`id`, `name`, `description`, `alert_keywords`, `is_system_default`, `is_active`,
   `created_by`, `created_at`, `auto_trigger_code`, `category`)
SELECT 1, '實際供貨風險', NULL, '缺貨、斷貨、供應不穩、交期延遲、停產', 1, 1, NULL, NOW(), NULL, 'SUPPLY'
WHERE NOT EXISTS (SELECT 1 FROM `risk_options` WHERE `id` = 1);

INSERT INTO `risk_options`
  (`id`, `name`, `description`, `alert_keywords`, `is_system_default`, `is_active`,
   `created_by`, `created_at`, `auto_trigger_code`, `category`)
SELECT 2, '商品品質與客訴風險', NULL, '客訴、退貨、瑕疵、品質不穩、客訴率高', 1, 1, NULL, NOW(), NULL, 'QUALITY'
WHERE NOT EXISTS (SELECT 1 FROM `risk_options` WHERE `id` = 2);

INSERT INTO `risk_options`
  (`id`, `name`, `description`, `alert_keywords`, `is_system_default`, `is_active`,
   `created_by`, `created_at`, `auto_trigger_code`, `category`)
SELECT 3, '市場不確定性與需求變動風險', NULL, '需求下滑、競品增加、退燒、熱度下降、季節性風險', 1, 1, NULL, NOW(), NULL, 'MARKET'
WHERE NOT EXISTS (SELECT 1 FROM `risk_options` WHERE `id` = 3);
