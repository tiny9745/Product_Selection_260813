-- =====================================================================
-- V17：修復 V16 對 product_types 9 筆系統預設大類「表面成功、實際沒生效」
-- 的問題
--
-- 【問題還原】
-- V16__seed_product_type_and_risk_option_defaults.sql 對這 9 筆大類用
-- 「INSERT ... SELECT ... WHERE NOT EXISTS (SELECT 1 FROM product_types
-- WHERE id = X)」寫法。這個寫法只檢查「這個 id 存不存在」，不檢查
-- 「這個 id 現有的 is_system_default 是不是已經是 1」。V16 實際套用時，
-- 這 9 個 id 剛好已被前一輪 seed_data_full_v4.sql（一般 INSERT，未帶
-- is_system_default 欄位、隱性落成 0）佔用，因此 WHERE NOT EXISTS 判斷
-- 為真、直接跳過，flyway 仍回報 success=1，但這 9 筆列的
-- is_system_default 從頭到尾沒有被改成 1。後續 clear_seed_data_v6.sql
-- 執行 `DELETE FROM product_types WHERE is_system_default = 0` 時，
-- 把這 9 筆大類當成一般假資料一併刪除，導致重新匯入
-- seed_data_full_v5.sql 時，30 筆小類的 parent_id 外鍵全數失敗
-- （Error Code 1452）。
--
-- 【修復方式：用 ON DUPLICATE KEY UPDATE 取代 WHERE NOT EXISTS】
-- 不管這 9 個 id 當下是「不存在」還是「存在但欄位跟預期不符」，都用
-- 同一條語句處理到底：不存在就 INSERT，存在就把 is_system_default／
-- 其餘欄位強制覆寫回系統預設應有的值。這樣即使未來又發生「先有假資料
-- 佔用這些 id、migration 才套用」的情境，也不會再重演 V16 那種靜默
-- 跳過的問題。
--
-- 【冪等性】ON DUPLICATE KEY UPDATE 依定義就是冪等：重複執行只會把這
-- 9 筆列的欄位再覆寫成同樣的值，不會產生新資料或報錯。
--
-- 【為什麼不直接修改 V16 檔案重跑】
-- flyway 對每支已套用過的 migration 會記錄 checksum，修改 V16 內容後
-- 重跑會被 flyway 判定 checksum 不符而拒絕啟動（除非手動 repair
-- 或清 flyway_schema_history，這對正式環境風險較高）。用新增一支
-- V17 migration 修正既有資料，是 flyway 慣例上正確且風險較低的做法。
--
-- 【created_at 為什麼沒有覆寫】
-- 若這 9 筆列本來就存在（不論是被 V16 誤判跳過、還是本來就正確），
-- 保留它們原本的 created_at 時間比較符合事實；這裡用 UPDATE 只覆寫
-- 語意欄位（is_system_default 及其餘應與系統預設一致的欄位），不動
-- created_at／created_by。若該 id 目前不存在（例如這次事故後被
-- clear_seed_data_v6.sql 一併清空的情況），才會走 INSERT 分支，此時
-- created_at 才會寫入 NOW()。
-- =====================================================================

INSERT INTO `product_types`
  (`id`, `name`, `description`, `is_active`, `is_system_default`, `parent_id`, `level`, `sort_order`,
   `default_temperature_zone`, `has_shelf_life`, `default_shelf_life_tier`,
   `return_policy`, `shelf_life_threshold_days`, `default_moq`,
   `required_certification`, `default_evaluation_mode_id`, `created_at`)
VALUES
  (1, '生鮮食品', '生鮮食品大類', 1, 1, NULL, 1, 1, 'CHILLED', 1, 'D8_30', NULL, 5, NULL, NULL, 2, NOW()),
  (5, '農產品', '農產品大類', 1, 1, NULL, 1, 2, 'NORMAL', 1, 'D8_30', NULL, 7, NULL, NULL, 2, NOW()),
  (9, '冷凍食品', '冷凍食品大類', 1, 1, NULL, 1, 3, 'FROZEN', 1, 'D90_PLUS', NULL, 14, NULL, NULL, 2, NOW()),
  (13, '地方名產', '地方名產大類', 1, 1, NULL, 1, 4, 'NORMAL', 1, 'D31_90', NULL, 30, NULL, NULL, 1, NOW()),
  (17, '常溫食品', '常溫食品大類', 1, 1, NULL, 1, 5, 'NORMAL', 1, 'D90_PLUS', NULL, 21, NULL, NULL, 1, NOW()),
  (22, '日用品', '日用品大類', 1, 1, NULL, 1, 6, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()),
  (26, '家庭用品', '家庭用品大類', 1, 1, NULL, 1, 7, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()),
  (31, '3C產品', '3C產品大類', 1, 1, NULL, 1, 8, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 3, NOW()),
  (36, '生活休閒', '生活休閒大類', 1, 1, NULL, 1, 9, 'NORMAL', 0, 'NA', NULL, NULL, NULL, NULL, 1, NOW())
AS `new`
ON DUPLICATE KEY UPDATE
  `name` = `new`.`name`,
  `description` = `new`.`description`,
  `is_active` = `new`.`is_active`,
  `is_system_default` = `new`.`is_system_default`,
  `parent_id` = `new`.`parent_id`,
  `level` = `new`.`level`,
  `sort_order` = `new`.`sort_order`,
  `default_temperature_zone` = `new`.`default_temperature_zone`,
  `has_shelf_life` = `new`.`has_shelf_life`,
  `default_shelf_life_tier` = `new`.`default_shelf_life_tier`,
  `return_policy` = `new`.`return_policy`,
  `shelf_life_threshold_days` = `new`.`shelf_life_threshold_days`,
  `default_moq` = `new`.`default_moq`,
  `required_certification` = `new`.`required_certification`,
  `default_evaluation_mode_id` = `new`.`default_evaluation_mode_id`;
