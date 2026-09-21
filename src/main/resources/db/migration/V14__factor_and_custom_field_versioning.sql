-- =====================================================================
-- V14：自訂計分因子／自訂商品屬性 支援「編輯」（新增+舊版軟刪除的版本鏈），
-- 並讓自訂商品屬性的 SCALE_1_5 型態可以定義 1~5 各代表什麼意思。
--
-- 【為什麼「編輯」不是 UPDATE 既有列，而是新增一列＋把舊列停用】
-- 兩張表都已經被別的資料參照舊列的 id（product_custom_field_values.
-- field_definition_id、review_records.weight_snapshot 凍結的
-- strategyCode/strategyParams/dataSourceCode 等）。如果直接 UPDATE 既有列，
-- 等於讓「已經審核完成、已經凍結」的歷史紀錄背後的定義內容被動跟著改變，
-- 違反企劃書「審核 Snapshot 不可被後續編輯覆蓋」的原則。因此編輯統一走
-- 「新增一列（新 id，內容是修改後的版本）＋把被取代的舊列 is_active 設為
-- false」，舊列的 id 與內容原封不動保留，供既有外鍵與歷史快照追溯。
--
-- 【factor_code／field_code 為什麼不能在編輯時修改】
-- 這兩個代碼是 evaluation_factors.factor_code、product_type_score_bands.
-- factor_code 等既有表用來對照因子的穩定文字鍵（不是查FK id）。只要代碼
-- 維持不變，編輯產生的新版本會自動被既有模式權重配置、目標區間設定認得，
-- 不需要额外搬移任何資料。因此本次不開放修改代碼本身，應用層驗證即可，
-- 不需要資料庫額外約束。
--
-- 【previous_version_id 為什麼用來擋「重新啟用已被取代的舊版本」】
-- 停用（disable/停用）與「被編輯取代」是兩件不同的事：前者可以隨時
-- enable 復原（既有既定模式）；後者的舊列已經有新版本接手同一個代碼，
-- 一旦被誤 enable，會讓同一個代碼同時存在兩個 is_active=true 的列，
-- 版本鏈語意矛盾。用「有沒有其他列的 previous_version_id 指向我」
-- 這個查詢就能分辨兩種情境，不需要再多一個容易跟 is_active 打架的旗標。
--
-- 【factor_code／field_code 唯一性為什麼改用「生成欄位＋唯一索引」】
-- 版本鏈設計下，同一個代碼會同時存在多列（舊版 is_active=false ＋新版
-- is_active=true），原本「全欄位唯一」的約束會直接擋下編輯動作插入的新列。
-- 但「目前生效中最多只能有一個同代碼」這個不變量仍然要在資料庫層守住，
-- 不能只靠應用層檢查（避免併發下兩個管理員同時編輯出現競態）。MySQL 8
-- 沒有原生的 partial unique index，改用「只在 is_active=true 時才有值的
-- 生成欄位」＋一般唯一索引達到同樣效果：is_active=false 時生成欄位為
-- NULL，MySQL 的唯一索引允許多個 NULL 並存，不會誤擋歷史列。
-- =====================================================================

ALTER TABLE `factor_definitions`
  ADD COLUMN `previous_version_id` bigint NULL
    COMMENT '編輯產生新版本時，指向被取代的舊版本 id；null代表這是最初版本或非因編輯而停用'
    AFTER `is_system_default`;

ALTER TABLE `factor_definitions`
  ADD CONSTRAINT `fk_factor_definitions_previous_version` FOREIGN KEY (`previous_version_id`)
    REFERENCES `factor_definitions` (`id`);

ALTER TABLE `factor_definitions` DROP INDEX `uk_factor_definitions_factor_code`;

ALTER TABLE `factor_definitions`
  ADD COLUMN `active_factor_code` varchar(50) COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CASE WHEN `is_active` THEN `factor_code` ELSE NULL END) STORED
    COMMENT '僅供唯一索引使用：is_active=false時恆為NULL，讓同代碼的歷史版本不受唯一索引限制';

ALTER TABLE `factor_definitions`
  ADD UNIQUE KEY `uk_factor_definitions_active_code` (`active_factor_code`);

-- ---------------------------------------------------------------------

ALTER TABLE `custom_field_definitions`
  ADD COLUMN `previous_version_id` bigint NULL
    COMMENT '編輯產生新版本時，指向被取代的舊版本 id；null代表這是最初版本或非因編輯而停用'
    AFTER `is_active`,
  ADD COLUMN `scale_labels` json NULL
    COMMENT '僅field_type=SCALE_1_5時可能有值：1~5每個分數代表的文字說明，key為分數(1~5)、value為說明文字'
    AFTER `previous_version_id`;

ALTER TABLE `custom_field_definitions`
  ADD CONSTRAINT `fk_custom_field_definitions_previous_version` FOREIGN KEY (`previous_version_id`)
    REFERENCES `custom_field_definitions` (`id`);

ALTER TABLE `custom_field_definitions` DROP INDEX `uk_custom_field_definitions_field_code`;

ALTER TABLE `custom_field_definitions`
  ADD COLUMN `active_field_code` varchar(50) COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CASE WHEN `is_active` THEN `field_code` ELSE NULL END) STORED
    COMMENT '僅供唯一索引使用，理由同 factor_definitions.active_factor_code';

ALTER TABLE `custom_field_definitions`
  ADD UNIQUE KEY `uk_custom_field_definitions_active_code` (`active_field_code`);
