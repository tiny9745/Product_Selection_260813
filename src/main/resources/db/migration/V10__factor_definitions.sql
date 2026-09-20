-- =====================================================================
-- V10：自訂計分因子（factor_definitions）
--
-- 【為什麼獨立一張表，不擴充 evaluation_factors】
-- evaluation_factors 回答「哪個評估模式要不要用這個因子、權重多少」，是
-- 每個模式各自的選擇；這張新表回答「這個因子怎麼算」，是跟模式無關的
-- 全域商業規則。混在同一張表會讓「模式A把某因子權重設0」跟「這個因子
-- 本身停用」這兩件語意完全不同的事情糾纏在一起，見
-- FactorDefinition.java 類別註解。
--
-- 【既有七個固定因子為什麼不遷移進這張表】
-- HISTORY_FULFILLMENT（巢狀貝氏收縮）、TREND_HEAT（指數衰減）目前的運算
-- 邏輯綁定特定形狀的既有查詢（成團次數/總次數、時間序列訊號），無法通用化
-- 成這裡定義的 strategy_code／data_source_code 兩個欄位；勉強遷移會讓
-- 資料模型出現「有些固定因子在這張表，有些不在」的不一致。因此維持
-- ProductFactorScorer 裡既有的七個寫死方法，這張表只承接新增的自訂因子，
-- 兩者在 ProductFactorScorer.scoreAll() 裡並列計分，互不影響、互不遷就。
--
-- 【strategy_params 為什麼是 JSON 不是固定欄位】
-- 不同 strategy_code 需要的參數名稱不同（MANUAL_SCALE／MANUAL_PERCENT 是
-- "scale"，TARGET_BAND_NORMALIZE 目前不需要任何參數），且策略清單之後
-- 還會增加，比照 review_records.weight_snapshot 等既有 JSON 欄位的做法，
-- 不為每種可能的參數各開一欄。
-- =====================================================================

CREATE TABLE `factor_definitions` (
  `id`                bigint NOT NULL AUTO_INCREMENT,
  `factor_code`       varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '因子代碼，全域唯一（含既有七個固定因子，建立時會一併檢查）',
  `factor_name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '畫面顯示名稱',
  `category`          varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '畫面分組顯示用，不參與計算，可為NULL',
  `strategy_code`     varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '運算邏輯代碼，見FactorStrategyCode',
  `data_source_code`  varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '綁定的既有欄位代碼，見FactorDataSource',
  `strategy_params`   json DEFAULT NULL COMMENT '該策略自己的參數，例如MANUAL_SCALE的scale倍率；省略時各策略使用自己的預設值',
  `is_active`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '停用後ProductFactorScorer不再計算，但既有evaluation_factors權重列不會被刪除（見SettingsService.disableFactorDefinition()）',
  `is_system_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '目前恆為0：既有七個固定因子刻意不遷移進這張表，這裡每一筆都是自訂因子',
  `created_by`        bigint DEFAULT NULL,
  `created_at`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        datetime DEFAULT NULL,
  `updated_by`        bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_factor_definitions_factor_code` (`factor_code`),
  KEY `fk_factor_definitions_created_by` (`created_by`),
  KEY `fk_factor_definitions_updated_by` (`updated_by`),
  CONSTRAINT `fk_factor_definitions_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_factor_definitions_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自訂計分因子的全域定義：這個因子怎麼算，跟哪個評估模式用不用它、用多少無關（那是evaluation_factors的職責）';
