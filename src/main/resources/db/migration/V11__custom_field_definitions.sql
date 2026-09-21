-- =====================================================================
-- V11：自訂商品屬性（動態問卷）——「開新計分因子資料源」需求的第一階段
--
-- 2026-09-20 與 Gary 確認的整體方向：與其每次新增計分因子的資料源都要
-- 工程師盤點 Product 既有欄位、改 enum，改成讓管理層自己在設定頁定義
-- 新的商品屬性題目，不需要改資料庫欄位、不需要新增 migration。這裡先做
-- 題目定義本身（custom_field_definitions）與品類範圍
-- （custom_field_applicable_types）；商品表單動態渲染、答案儲存
-- （product_custom_field_values）、以及計分系統接回這裡的題目，都是
-- 後續階段，這次migration不建這些表。
--
-- 【為什麼品類範圍另開一張表，不是 custom_field_definitions 加一個
-- product_type_id 欄位】
-- 一個題目可能適用多個大類（例如「保存期限相關」可能同時適用生鮮跟冷凍），
-- 是多對多關係，不是單一外鍵能表達的。完全沒有列＝適用全部品類，不需要
-- 另外的 is_global 旗標——這樣設計不會出現「is_global=true 但底下卻掛著
-- 限制列」這種自相矛盾、卻可能發生的資料狀態。
--
-- 【root_product_type_id 為什麼限定大類，不是商品實際掛的小類】
-- 沿用既有 ScoreBandResolver.resolveRootTypeId() 的既定作法：依品類設定的
-- 資料一律以大類為準。大類數量少，適合做成勾選清單；商品掛的小類數量可能
-- 多很多，硬要逐一勾選小類會很難操作，管理層也已經在「目標區間」頁面
-- 熟悉「依大類設定」這套心智模型，沿用同一套不用另外學。
-- =====================================================================

CREATE TABLE `custom_field_definitions` (
  `id`           bigint NOT NULL AUTO_INCREMENT,
  `field_code`   varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '欄位代碼，全域唯一，商品表單渲染與（未來）計分資料源用來對照題目的唯一鍵',
  `field_name`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品表單上這一題的標籤文字',
  `help_text`    varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品表單上的補充說明，可為NULL',
  `field_type`   varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '見CustomFieldType：SCALE_1_5/PERCENT_0_1/RAW_NUMBER為數值類，可作為計分資料源；TEXT為純文字，不參與計分',
  `is_required`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '商品表單是否強制填寫這一題',
  `is_active`    tinyint(1) NOT NULL DEFAULT '1' COMMENT '停用後商品表單不再顯示這一題，但已填過的答案（product_custom_field_values）完全不受影響',
  `created_by`   bigint DEFAULT NULL,
  `created_at`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   datetime DEFAULT NULL,
  `updated_by`   bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_custom_field_definitions_field_code` (`field_code`),
  KEY `fk_custom_field_definitions_created_by` (`created_by`),
  KEY `fk_custom_field_definitions_updated_by` (`updated_by`),
  CONSTRAINT `fk_custom_field_definitions_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_custom_field_definitions_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自訂商品屬性（動態問卷）題目定義，讓管理層自己新增商品屬性，不需要改資料庫欄位';

CREATE TABLE `custom_field_applicable_types` (
  `id`                    bigint NOT NULL AUTO_INCREMENT,
  `field_definition_id`   bigint NOT NULL,
  `root_product_type_id`  bigint NOT NULL COMMENT '必須是大類（product_types.level=1），Service層建立時會驗證',
  `created_at`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_custom_field_applicable_types_field_type` (`field_definition_id`, `root_product_type_id`),
  KEY `fk_custom_field_applicable_types_field` (`field_definition_id`),
  KEY `fk_custom_field_applicable_types_type` (`root_product_type_id`),
  CONSTRAINT `fk_custom_field_applicable_types_field` FOREIGN KEY (`field_definition_id`)
    REFERENCES `custom_field_definitions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_custom_field_applicable_types_type` FOREIGN KEY (`root_product_type_id`)
    REFERENCES `product_types` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自訂商品屬性題目適用的大類；一個題目完全沒有列＝適用全部品類';
