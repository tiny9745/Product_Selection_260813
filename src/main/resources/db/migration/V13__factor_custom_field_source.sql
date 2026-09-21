-- =====================================================================
-- V13：自訂計分因子接回自訂商品屬性（動態問卷）——「開新計分因子資料源」
-- 最後一階段
--
-- 【為什麼是新增一個獨立欄位，不是把 data_source_code 的候選值加一個
-- 「動態」選項】
-- FactorDataSource 是寫死的 Java enum，每個值對應一個固定的 Product
-- entity 欄位讀取方法（見 FactorDataSource.extractRawValue()）——這是
-- 一個「無狀態、不需要查資料庫」的設計，因為固定欄位就長在 Product 物件
-- 上，直接讀就好。但自訂商品屬性的答案存在 product_custom_field_values，
-- 需要另外查詢，且要讀「哪一個」自訂屬性是可以變動的（管理層自己在
-- 設定頁挑），enum 常數的形狀完全不適合表達「這是一個資料庫外鍵參照」。
--
-- 因此新增 custom_field_definition_id，跟既有的 data_source_code 二選一：
--   data_source_code 有值           → 讀 Product entity 固定欄位（既有邏輯）
--   custom_field_definition_id 有值 → 讀 product_custom_field_values
-- 兩者不會同時有值，也不會同時是 NULL——這條規則在應用層驗證
-- （SettingsService.createFactorDefinition()），不在資料庫層用 CHECK
-- constraint 硬性約束，理由跟本專案其他「應用層驗證優先於資料庫約束」
-- 的既有慣例一致（例如 review_risks 的複合主鍵搭配應用層邏輯而非額外約束）。
--
-- data_source_code 因此改成可為 NULL（原本 NOT NULL）。
-- =====================================================================

ALTER TABLE `factor_definitions`
  MODIFY COLUMN `data_source_code` varchar(50) COLLATE utf8mb4_unicode_ci NULL
    COMMENT '綁定的既有欄位代碼，見FactorDataSource；跟custom_field_definition_id二選一，兩者不會同時有值';

ALTER TABLE `factor_definitions`
  ADD COLUMN `custom_field_definition_id` bigint NULL
    COMMENT '參照custom_field_definitions.id，讀取動態問卷答案當資料源；跟data_source_code二選一'
    AFTER `data_source_code`;

ALTER TABLE `factor_definitions`
  ADD KEY `fk_factor_definitions_custom_field` (`custom_field_definition_id`),
  ADD CONSTRAINT `fk_factor_definitions_custom_field` FOREIGN KEY (`custom_field_definition_id`)
    REFERENCES `custom_field_definitions` (`id`);
