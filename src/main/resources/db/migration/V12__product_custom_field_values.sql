-- =====================================================================
-- V12：商品自訂屬性答案（EAV 值表）——「開新計分因子資料源」Phase 2
--
-- 天生稀疏表：只有商品實際填過的題目才會有列，題目是商品建立之後才新增的
-- 這種情況本來就不會有對應列，查詢自然回傳查無資料，不需要幫舊商品補值。
-- 這正是 2026-09-20 討論「舊資料如何呈現」時說好的效果：不動舊資料，
-- 不用另外寫批次程式回填。
--
-- numeric_value／text_value 只會有一個有值，依 custom_field_definitions.
-- field_type 決定用哪一欄，見 ProductCustomFieldValue 類別註解。
-- =====================================================================

CREATE TABLE `product_custom_field_values` (
  `id`                   bigint NOT NULL AUTO_INCREMENT,
  `product_id`           bigint NOT NULL,
  `field_definition_id`  bigint NOT NULL,
  `numeric_value`        decimal(12,4) DEFAULT NULL COMMENT 'SCALE_1_5/PERCENT_0_1/RAW_NUMBER三種數值類型態用這欄，TEXT型態這欄恆為NULL',
  `text_value`           varchar(500) DEFAULT NULL COMMENT 'TEXT型態用這欄，不參與計分，其餘三種數值類型態這欄恆為NULL',
  `created_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_custom_field_values_product_field` (`product_id`, `field_definition_id`),
  KEY `fk_product_custom_field_values_product` (`product_id`),
  KEY `fk_product_custom_field_values_field` (`field_definition_id`),
  CONSTRAINT `fk_product_custom_field_values_product` FOREIGN KEY (`product_id`)
    REFERENCES `products` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_product_custom_field_values_field` FOREIGN KEY (`field_definition_id`)
    REFERENCES `custom_field_definitions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='商品對自訂商品屬性題目的答案，天生稀疏表，題目後於商品建立時該商品這一題查無資料是預期行為';
