-- =====================================================================
-- V3：評估因子改為扁平七因子
--
-- 原本 evaluation_factors 每個模式只有四筆（BUSINESS/AUDIENCE/HISTORY/FORECAST），
-- 象限內各子因子的比重寫死在 ScoringService 的程式碼裡（毛利 40%、供應 30%、
-- 價格競爭力 30%）。改為扁平之後，七個因子各自在表裡有一筆自己的權重。
--
-- 【為什麼選扁平而非兩層】
-- 主管要能算得出「我把毛利率從 20 調到 30，總分會怎麼變」。兩層權重下這個
-- 心算變成「20% × 40% = 實際 8%」，很難直覺掌握，調權重會變成盲目試誤。
--
-- category 欄位保留，但語意改為「畫面上的分組標題」，不參與任何計算。
--
-- 【三套固定模式的拆分依據】
-- 象限內比例沿用改版前的權重（均衡 25/25/25/25、衝量 15/30/15/40、
-- 高利潤 45/15/15/25），讓既有模式的相對關係不變：
--   BUSINESS  毛利率 40% / 供應穩定性 30% / 折扣深度 30%（接替原價格競爭力的位置）
--   FORECAST  預估購買率 50% / 趨勢熱度 50%
-- 價格競爭力（人工 1~5 分）改列 Signal 不再計分，因為它與客觀的折扣深度描述
-- 同一件事，兩者並存等於把價格因素重複加權。它的輸入欄位保留，供主客觀落差對照。
--
-- 【⚠️ 本版更正：移除所有跨語句 session 狀態依賴】
-- 用 mysql CLI 直接執行前一版檔案完全成功，但同一份檔案在 Flyway 執行環境下
-- 仍然在同一個外鍵約束上失敗。這代表問題不在 SQL 語法本身，而在 Flyway 的
-- SQL 語句解析器切分多語句腳本的方式與 mysql CLI 不完全相同。
--
-- 前一版有兩處可能受影響的寫法：
--   1. SET NAMES utf8mb4;——獨立的 SET 語句
--   2. SET @custom_mode_id = LAST_INSERT_ID(); 之後的語句依賴這個 session
--      變數——這是整份腳本裡唯一「後面的語句依賴前面語句執行結果」的地方，
--      其餘所有 mode_id 查找都是用不依賴任何前置狀態的獨立子查詢。
--
-- 不追究確切是哪一項造成 Flyway 解析異常，直接兩者都移除，讓整份腳本的
-- 每一句都是完全獨立、不依賴任何跨語句 session 狀態的寫法——這樣不論
-- Flyway 內部怎麼切分語句，每一句單獨執行都會得到正確結果。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. 確保三套基礎模式存在
--
--    不論這個資料庫是「V1 已含種子資料」還是「V1 未更新、仍是純 schema」，
--    這段都能正確處理：已存在則 WHERE NOT EXISTS 跳過，不存在則在此建立。
--    內容取自現行資料庫的既有種子資料，不是新編的文字。
-- ---------------------------------------------------------------------
INSERT INTO `evaluation_modes` (`mode_code`, `mode_name`, `version`, `description`, `is_active`)
SELECT 'BALANCED', '均衡模式', 1, '四大分類權重平均分配', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_modes` WHERE `mode_code` = 'BALANCED' AND `version` = 1
);

INSERT INTO `evaluation_modes` (`mode_code`, `mode_name`, `version`, `description`, `is_active`)
SELECT 'VOLUME', '衝量模式', 1, '偏重核心客群匹配與預測人氣，適合衝銷量', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_modes` WHERE `mode_code` = 'VOLUME' AND `version` = 1
);

INSERT INTO `evaluation_modes` (`mode_code`, `mode_name`, `version`, `description`, `is_active`)
SELECT 'PROFIT', '高利潤模式', 1, '偏重商業條件（毛利率），適合追求利潤', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_modes` WHERE `mode_code` = 'PROFIT' AND `version` = 1
);

-- 第四套「自訂模式」也在這裡一併確保存在，與前三套用同一種寫法。
-- 唯一 is_editable = 1 的模式；前三套維持 0，不可透過權重編輯 API 修改。
INSERT INTO `evaluation_modes` (`mode_code`, `mode_name`, `version`, `description`, `is_active`, `is_editable`)
SELECT 'CUSTOM', '自訂模式', 1, '權重可由主管自行調整的模式，前三套固定模式維持唯讀', 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `evaluation_modes` WHERE `mode_code` = 'CUSTOM' AND `version` = 1
);
-- evaluation_modes.version 是 int NOT NULL 且無預設值（見 V1 schema），
-- 這裡明確帶入 1。version 的原始設計語意是「同一 mode_code 可以有多筆歷史
-- 版本，修改權重不覆蓋既有資料而是建新版本」；本次自訂模式改採原地更新
-- （見 SettingsService.updateEvaluationModeFactors()），不使用多版本機制，
-- 因此 CUSTOM 固定只有一筆、version 恆為 1。


-- ---------------------------------------------------------------------
-- 1. 清掉舊的因子列，改寫入七因子列
--
--    一律用 mode_code 查回 evaluation_mode_id，不寫死數字、也不依賴
--    LAST_INSERT_ID() 這種需要跨語句記住前一句結果的機制——每一句都是
--    獨立的子查詢，不管步驟 0 是「跳過」還是「剛建立」，也不管執行環境
--    怎麼切分語句順序，查到的都是當下資料庫裡真正存在的那一筆。
-- ---------------------------------------------------------------------
DELETE FROM `evaluation_factors`
WHERE `evaluation_mode_id` IN (
  SELECT id FROM (
    SELECT `id` FROM `evaluation_modes`
    WHERE (`mode_code`, `version`) IN
          (('BALANCED',1), ('VOLUME',1), ('PROFIT',1), ('CUSTOM',1))
  ) AS existing_modes
);

INSERT INTO `evaluation_factors`
  (`evaluation_mode_id`, `factor_code`, `factor_name`, `category`, `weight`, `description`, `sort_order`)
VALUES
  -- 模式 均衡（原 BUSINESS 25 / AUDIENCE 25 / HISTORY 25 / FORECAST 25）
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'MARGIN_RATE',         '毛利率（已扣運費）', 'BUSINESS', 10.00, '售價扣除成本與運費估算後的毛利率，依大類目標區間正規化', 1),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'DISCOUNT_DEPTH',      '折扣深度',           'BUSINESS',  7.50, '團購價相對市價的折讓幅度', 2),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'SUPPLY_STABILITY',    '供應穩定性',         'BUSINESS',  7.50, '人工量級評估，承載系統拿不到的供應面資訊', 3),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'AUDIENCE_MATCH',      '核心客群匹配度',     'AUDIENCE', 25.00, '商品描述與客群關鍵字的命中率', 4),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'HISTORY_FULFILLMENT', '歷史成團率',         'HISTORY',  25.00, '巢狀貝氏收縮：商品到品類到全域', 5),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'PURCHASE_RATE',       '預估購買率',         'FORECAST', 12.50, '人工預估值', 6),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1),
   'TREND_HEAT',          '市場趨勢熱度',       'FORECAST', 12.50, '依資料距今天數指數衰減後的熱度分', 7),

  -- 模式 衝量（原 15 / 30 / 15 / 40）
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'MARGIN_RATE',         '毛利率（已扣運費）', 'BUSINESS',  6.00, NULL, 1),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'DISCOUNT_DEPTH',      '折扣深度',           'BUSINESS',  4.50, NULL, 2),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'SUPPLY_STABILITY',    '供應穩定性',         'BUSINESS',  4.50, NULL, 3),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'AUDIENCE_MATCH',      '核心客群匹配度',     'AUDIENCE', 30.00, NULL, 4),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'HISTORY_FULFILLMENT', '歷史成團率',         'HISTORY',  15.00, NULL, 5),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'PURCHASE_RATE',       '預估購買率',         'FORECAST', 20.00, NULL, 6),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='VOLUME' AND `version`=1),
   'TREND_HEAT',          '市場趨勢熱度',       'FORECAST', 20.00, NULL, 7),

  -- 模式 高利潤（原 45 / 15 / 15 / 25）
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'MARGIN_RATE',         '毛利率（已扣運費）', 'BUSINESS', 18.00, NULL, 1),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'DISCOUNT_DEPTH',      '折扣深度',           'BUSINESS', 13.50, NULL, 2),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'SUPPLY_STABILITY',    '供應穩定性',         'BUSINESS', 13.50, NULL, 3),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'AUDIENCE_MATCH',      '核心客群匹配度',     'AUDIENCE', 15.00, NULL, 4),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'HISTORY_FULFILLMENT', '歷史成團率',         'HISTORY',  15.00, NULL, 5),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'PURCHASE_RATE',       '預估購買率',         'FORECAST', 12.50, NULL, 6),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='PROFIT' AND `version`=1),
   'TREND_HEAT',          '市場趨勢熱度',       'FORECAST', 12.50, NULL, 7),

  -- 模式 自訂：七項平均分配，加總 100
  -- 七等分無法整除，尾數以 14.28 補足，加總恰為 100.00。
  -- 權重編輯 API 會驗證加總必須等於 100，這裡的初值必須先滿足同一條規則。
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'MARGIN_RATE',         '毛利率（已扣運費）', 'BUSINESS', 14.29, NULL, 1),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'DISCOUNT_DEPTH',      '折扣深度',           'BUSINESS', 14.29, NULL, 2),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'SUPPLY_STABILITY',    '供應穩定性',         'BUSINESS', 14.29, NULL, 3),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'AUDIENCE_MATCH',      '核心客群匹配度',     'AUDIENCE', 14.28, NULL, 4),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'HISTORY_FULFILLMENT', '歷史成團率',         'HISTORY',  14.29, NULL, 5),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'PURCHASE_RATE',       '預估購買率',         'FORECAST', 14.28, NULL, 6),
  ((SELECT id FROM `evaluation_modes` WHERE `mode_code`='CUSTOM' AND `version`=1),
   'TREND_HEAT',          '市場趨勢熱度',       'FORECAST', 14.28, NULL, 7);


-- ---------------------------------------------------------------------
-- 2. system_settings.current_evaluation_mode_id 預設值（若尚未設定）
--
--    ScoringService.resolveEvaluationModeId() 在商品品類未指定專屬模式時，
--    會退回讀取這個全域設定；未設定時商品評分會直接拋出
--    IllegalStateException。用 WHERE NOT EXISTS 保護：若這個 key 已經被
--    設定過（不論是 V1 就有、或先前已手動設定），不會覆蓋既有值。
-- ---------------------------------------------------------------------
INSERT INTO `system_settings` (`setting_key`, `setting_value`)
SELECT 'current_evaluation_mode_id',
       CAST((SELECT id FROM `evaluation_modes` WHERE `mode_code`='BALANCED' AND `version`=1) AS CHAR)
WHERE NOT EXISTS (
  SELECT 1 FROM `system_settings` WHERE `setting_key` = 'current_evaluation_mode_id'
);
