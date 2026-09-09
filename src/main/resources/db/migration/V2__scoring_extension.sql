-- =====================================================================
-- V2：選品評分擴充
--
-- 對應設計文件「四、資料表與欄位」。分四段：
--   1. product_types 兩層階層 + 品類預設屬性
--   2. products Gate 屬性欄位
--   3. 新開資料表（group_buy_records / product_type_score_bands）
--   4. 其他表欄位 + system_settings 參數
--
-- 所有新增欄位一律 nullable，null 代表「繼承上層」而非「未設定」，
-- 這個語意在 ProductTypeAttributeResolver 與 MoqResolver 內實作。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. product_types：兩層階層 + 品類預設屬性
-- ---------------------------------------------------------------------
ALTER TABLE `product_types`
  ADD COLUMN `parent_id` BIGINT NULL COMMENT '父層品類；null 代表本身是大類'                     AFTER `id`,
  ADD COLUMN `level` TINYINT NOT NULL DEFAULT 1 COMMENT '1=大類 2=小類。刻意反正規化：兩層固定深度，用 level 判斷比每次遞迴查 parent 便宜' AFTER `parent_id`,
  ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同層排序'                              AFTER `level`,
  ADD COLUMN `default_temperature_zone` VARCHAR(20) NULL COMMENT '品類預設溫層 NORMAL/CHILLED/FROZEN',
  ADD COLUMN `has_shelf_life` TINYINT(1) NULL COMMENT '此品類商品是否有效期概念；false 時效期欄位不列入資料完整度分母',
  ADD COLUMN `default_shelf_life_tier` VARCHAR(20) NULL COMMENT '品類預設效期級距',
  ADD COLUMN `return_policy` VARCHAR(30) NULL COMMENT '退換政策（Signal 顯示用）',
  ADD COLUMN `shelf_life_threshold_days` INT NULL COMMENT 'GATE_SHELF_LIFE 的門檻天數；解析順序 小類→大類→system_settings',
  ADD COLUMN `default_moq` INT NULL COMMENT 'MOQ 三層解析的中間層',
  ADD COLUMN `required_certification` VARCHAR(200) NULL COMMENT '認證需求（逗號分隔，Signal 比對用）',
  ADD COLUMN `default_evaluation_mode_id` BIGINT NULL COMMENT '評估模式綁品類；僅在大類（level=1）設定有意義',
  ADD CONSTRAINT `fk_product_type_parent`
      FOREIGN KEY (`parent_id`) REFERENCES `product_types` (`id`),
  ADD CONSTRAINT `fk_product_type_eval_mode`
      FOREIGN KEY (`default_evaluation_mode_id`) REFERENCES `evaluation_modes` (`id`),
  ADD INDEX `idx_product_type_parent` (`parent_id`),
  ADD INDEX `idx_product_type_level` (`level`);

-- 既有品類全部視為大類（level=1）。小類與屬性值由後續 seed 或設定頁建立。
UPDATE `product_types` SET `level` = 1 WHERE `parent_id` IS NULL;


-- ---------------------------------------------------------------------
-- 2. products：Gate 屬性欄位
--    採級距化勾選而非精確數值——Gate 是二元判斷，只需要級距。
-- ---------------------------------------------------------------------
ALTER TABLE `products`
  ADD COLUMN `temperature_zone` VARCHAR(20) NULL COMMENT '溫層 NORMAL/CHILLED/FROZEN；null 繼承品類預設',
  ADD COLUMN `shelf_life_tier` VARCHAR(20) NULL COMMENT '效期級距 D7/D8_30/D31_90/D90_PLUS/NA',
  ADD COLUMN `supplier_lead_time_tier` VARCHAR(20) NULL COMMENT '供應商前置期級距 D3/D4_7/D8_14/D15_PLUS',
  ADD COLUMN `package_size_tier` VARCHAR(20) NULL COMMENT '材積級距 XS/S/M/L；運費估算依此查表',
  ADD COLUMN `packing_type` VARCHAR(20) NULL COMMENT '分裝方式 WHOLE_CARTON/REPACK',
  ADD COLUMN `handling_flags` VARCHAR(200) NULL COMMENT '處理注意事項（逗號分隔）FRAGILE/UPRIGHT/LIGHT_SENSITIVE',
  ADD COLUMN `certification_flags` VARCHAR(200) NULL COMMENT '認證狀態（逗號分隔）',
  ADD COLUMN `supplier_max_capacity` INT NULL COMMENT '供應商產能上限（Signal 顯示用）',
  ADD COLUMN `resale_reference_product_id` BIGINT NULL COMMENT '再販售參考商品；供歷史分數的商品層查詢使用',
  ADD CONSTRAINT `fk_product_resale_reference`
      FOREIGN KEY (`resale_reference_product_id`) REFERENCES `products` (`id`),
  ADD INDEX `idx_product_resale_reference` (`resale_reference_product_id`);


-- ---------------------------------------------------------------------
-- 3-1. group_buy_records：歷史開團紀錄（外部匯入、唯讀）
--
--  product_id 可為空、product_type_id 必填——與一般直覺相反，但這是刻意的：
--  選品評估的對象是新商品，本來就不會有自己的開團歷史；歷史分數的主要
--  來源是品類，不是商品本身。匯入檔案必須指定品類，不做名稱模糊比對。
-- ---------------------------------------------------------------------
CREATE TABLE `group_buy_records` (
  `id`                    BIGINT NOT NULL AUTO_INCREMENT,
  `product_id`            BIGINT NULL         COMMENT '對應系統內商品；匯入資料的商品多半不在系統內，故可為空',
  `product_type_id`       BIGINT NOT NULL     COMMENT '所屬品類；歷史分數的主要聚合維度，必填',
  `external_product_name` VARCHAR(200) NOT NULL COMMENT '匯入來源的原始商品名稱',
  `supplier_name`         VARCHAR(100) NULL,
  `campaign_start_date`   DATE NOT NULL,
  `campaign_end_date`     DATE NOT NULL,
  `moq_at_time`           INT NULL            COMMENT '開團當下的 MOQ（快照，不 JOIN 現值）',
  `sale_price_at_time`    DECIMAL(10,2) NULL  COMMENT '開團當下的售價（快照）',
  `target_quantity`       INT NULL,
  `actual_quantity`       INT NOT NULL        COMMENT '實際集單量；MOQ 可行性判定的分位數基準來源',
  `participant_count`     INT NULL,
  `result`                VARCHAR(20) NOT NULL COMMENT 'FULFILLED/FAILED/CANCELLED',
  `complaint_count`       INT NOT NULL DEFAULT 0,
  `return_count`          INT NOT NULL DEFAULT 0,
  `is_simulated`          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否為模擬資料；從第一天就有，之後補會無法回頭標記已寫入快照的紀錄',
  `import_batch_id`       VARCHAR(50) NULL    COMMENT '匯入批次；供整批回退使用',
  `imported_at`           DATETIME NOT NULL,
  `imported_by`           BIGINT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_gbr_product` FOREIGN KEY (`product_id`)      REFERENCES `products` (`id`),
  CONSTRAINT `fk_gbr_type`    FOREIGN KEY (`product_type_id`) REFERENCES `product_types` (`id`),
  KEY `idx_gbr_type_date` (`product_type_id`, `campaign_start_date`),
  KEY `idx_gbr_product`   (`product_id`),
  KEY `idx_gbr_batch`     (`import_batch_id`),
  KEY `idx_gbr_result`    (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='歷史開團紀錄（外部匯入、唯讀）。系統不負責審核之後的營運事宜，故不提供單筆新增／編輯端點。';


-- ---------------------------------------------------------------------
-- 3-2. product_type_score_bands：固定目標區間
--
--  用即時資料算分位數會違反可重現性（新增商品會讓舊商品分數位移），
--  因此正規化改用凍結成設定值的固定上下界。調整時建新版本、留舊版本。
--
--  product_type_id 可為 null = 全域預設區間（例如折扣深度 0~50% 不分品類）。
-- ---------------------------------------------------------------------
CREATE TABLE `product_type_score_bands` (
  `id`              BIGINT NOT NULL AUTO_INCREMENT,
  `product_type_id` BIGINT NULL         COMMENT '指向大類；null 代表全域預設區間',
  `factor_code`     VARCHAR(50) NOT NULL COMMENT 'MARGIN_RATE / DISCOUNT_DEPTH',
  `lower_bound`     DECIMAL(10,4) NOT NULL COMMENT '對應 0 分',
  `upper_bound`     DECIMAL(10,4) NOT NULL COMMENT '對應 100 分',
  `version`         INT NOT NULL DEFAULT 1,
  `is_active`       TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_bands_type` FOREIGN KEY (`product_type_id`) REFERENCES `product_types` (`id`),
  UNIQUE KEY `uk_bands_type_factor_version` (`product_type_id`, `factor_code`, `version`),
  KEY `idx_bands_lookup` (`factor_code`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='各因子的固定目標區間。凍結成設定值，不隨資料變動，以維持分數可重現性。';

-- 全域預設區間：折扣深度 0~50%（設計文件 2.2 計分因子表）
INSERT INTO `product_type_score_bands`
  (`product_type_id`, `factor_code`, `lower_bound`, `upper_bound`, `version`, `is_active`)
VALUES
  (NULL, 'DISCOUNT_DEPTH', 0.0000, 0.5000, 1, 1),
  -- 毛利率的全域保底區間。各大類應在設定頁另行覆寫（見設計文件 3.1 範例表）
  (NULL, 'MARGIN_RATE',    0.0000, 0.4000, 1, 1);


-- ---------------------------------------------------------------------
-- 4-1. 其他表欄位
-- ---------------------------------------------------------------------
ALTER TABLE `evaluation_modes`
  ADD COLUMN `is_editable` TINYINT(1) NOT NULL DEFAULT 0
      COMMENT '是否允許調整權重。既有三套固定模式維持 0，僅自訂模式為 1';

ALTER TABLE `risk_options`
  ADD COLUMN `auto_trigger_code` VARCHAR(50) NULL
      COMMENT 'Gate 代碼，如 GATE_TEMPERATURE_ZONE；null 代表純人工選項',
  ADD COLUMN `category` VARCHAR(20) NULL
      COMMENT '五大風險面向 BUSINESS/SUPPLY/QUALITY/MARKET/DATA',
  ADD INDEX `idx_risk_option_trigger` (`auto_trigger_code`);

ALTER TABLE `review_risks`
  ADD COLUMN `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
      COMMENT 'MANUAL=主管自行勾選 / SYSTEM_AUTO=Gate 判定帶入',
  ADD COLUMN `is_selected` TINYINT(1) NOT NULL DEFAULT 1
      COMMENT '主管最終是否保留。SYSTEM_AUTO + is_selected=0 代表「系統判定但主管推翻」，是稽核價值最高的一筆，不可用「不寫入」代替',
  ADD COLUMN `trigger_reason` VARCHAR(500) NULL
      COMMENT '系統帶入時的具體原因';

ALTER TABLE `review_records`
  ADD COLUMN `system_gate_summary` TEXT NULL
      COMMENT 'Gate 判定的系統建議文字。與 review_comment 分開存：review_comment 是主管的話，系統文字不可混入，否則事後查核會讀到主管沒寫過的內容';


-- ---------------------------------------------------------------------
-- 4-2. system_settings：演算法參數
--      魔術數字一律放設定表，改設定比改程式碼重新部署容易得多。
-- ---------------------------------------------------------------------
-- system_settings 實際欄位只有 setting_key / setting_value / updated_at / updated_by，
-- 沒有 description 欄位，各參數的用途說明改寫在此註解區塊：
--   supported_temperature_zones  通路支援的溫層。目前三種全支援，GATE_TEMPERATURE_ZONE
--                                現階段不會擋掉任何商品；保留是為了冷鏈條件變動時能即時生效
--   freight_cost_xs/s/m/l        各材積級距的每件運費估算（初值 0，待業務填實際值）
--   moq_benchmark_percentile     集單量基準採用的分位數
--   moq_safety_factor            分位數之上的風險調整係數
--   moq_min_sample_size          低於此樣本數時 GATE_MOQ_FEASIBILITY 回傳資料不足
--   default_moq                  MOQ 三層解析的全域保底
--   shelf_life_threshold_days    效期門檻的全域保底，品類未設定時使用（待業務確認）
--   shrinkage_k_category         品類層貝氏收縮平滑常數
--   shrinkage_k_product          商品層貝氏收縮平滑常數
--   trend_half_life_days         趨勢新鮮度衰減半衰期（天）
--   neutral_baseline_score       中性基準分
INSERT INTO `system_settings` (`setting_key`, `setting_value`) VALUES
  ('supported_temperature_zones', 'NORMAL,CHILLED,FROZEN'),
  ('freight_cost_xs', '0'),
  ('freight_cost_s',  '0'),
  ('freight_cost_m',  '0'),
  ('freight_cost_l',  '0'),
  ('moq_benchmark_percentile', '75'),
  ('moq_safety_factor',        '1.0'),
  ('moq_min_sample_size',      '5'),
  ('default_moq',              '1'),
  ('shelf_life_threshold_days', '21'),
  ('shrinkage_k_category',      '10'),
  ('shrinkage_k_product',       '5'),
  ('trend_half_life_days',      '14'),
  ('neutral_baseline_score',    '50');
