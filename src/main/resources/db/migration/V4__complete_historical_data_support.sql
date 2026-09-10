-- =====================================================================
-- V4：完善三處資料缺口
--
-- 這支 migration 合併三個彼此相關的擴充，理由是它們是同一件事的三個環節：
-- 「讓目標區間可以從歷史資料算出來，而且改動要留下稽核紀錄」。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. group_buy_records 補成本價與市價
--
-- 原始設計只存 sale_price_at_time，是因為這張表最初只是為了算「成團率」
-- （貝氏收縮用的分子分母）。但要從歷史資料算出毛利率、折扣深度的合理區間，
-- 需要成本價與市價——沒有這兩個欄位，「參照歷史數據」這個模式完全無法
-- 從這張表算出任何東西。
--
-- 兩者皆為 nullable：既有已匯入的舊資料沒有這兩欄，不能讓既有紀錄失效；
-- 之後的歷史區間計算會自動跳過缺這兩欄的舊紀錄（見下方 sample_size 的
-- 計算邏輯只計入有值的列），新匯入的資料建議填齊。
-- ---------------------------------------------------------------------
ALTER TABLE `group_buy_records`
  ADD COLUMN `cost_price_at_time` DECIMAL(10,2) NULL
      COMMENT '開團當下的成本價（快照，不 JOIN 現值）；為 null 代表匯入時未提供，不納入毛利率歷史區間計算',
  ADD COLUMN `market_price_at_time` DECIMAL(10,2) NULL
      COMMENT '開團當下的市價（快照）；為 null 代表匯入時未提供，不納入折扣深度歷史區間計算';


-- ---------------------------------------------------------------------
-- 2. evaluation_factors 補稽核欄位
--
-- 原始 schema 註解寫「固定權重（唯讀展示，不提供調整介面）」，當時沒有
-- 稽核欄位是合理的——沒有寫入路徑，不需要記錄是誰改的。但自訂模式的
-- 權重編輯 API 上線後，這張表多了一條寫入路徑，稽核欄位卻從未補上：
-- 權重被改了，除了應用程式日誌（不可查詢、會輪替清除），完全查不到
-- 是誰、什麼時候改的。
-- ---------------------------------------------------------------------
ALTER TABLE `evaluation_factors`
  ADD COLUMN `updated_at` DATETIME NULL
      COMMENT '最後一次權重變更時間；三套固定模式維持 NULL（從未被寫入過）',
  ADD COLUMN `updated_by` BIGINT NULL
      COMMENT '最後一次權重變更的操作者；三套固定模式維持 NULL';


-- ---------------------------------------------------------------------
-- 3. product_type_score_bands 新增「參照歷史數據 / 手動設定」切換模式
--
-- 兩種模式並存、可切換，不是「先建議後確認」的兩階段流程：
--   HISTORICAL：由系統從歷史開團紀錄算出建議區間並直接寫入（凍結）
--   MANUAL：由主管直接輸入固定數字
--
-- 切換到 HISTORICAL 時，系統「當下計算一次並凍結寫入」，不是每次評分
-- 都重新算——這是為了維持整套系統的可重現性硬約束：目標區間若隨資料庫
-- 累積而浮動，會讓分數在使用者沒有任何操作的情況下自己改變，且已審核
-- 商品的快照會對不上重新計算的結果。因此 HISTORICAL 只是「數字的來源」，
-- 不是「即時運算的公式」；兩種模式在「評分時怎麼被讀取」這件事上完全一樣，
-- 差別只在畫面上顯示這個數字是算出來的還是手動填的。
--
-- 資料來源是 group_buy_records（見上方第 1 點的擴充），不是
-- review_records——開團紀錄代表「實際發生過的交易結果」，比審核當下的
-- 快照更適合作為「歷史上這個品類真實達到過的毛利率／折扣深度」的依據。
-- ---------------------------------------------------------------------
ALTER TABLE `product_type_score_bands`
  ADD COLUMN `source_mode` VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
      COMMENT 'HISTORICAL=由歷史開團紀錄算出並凍結 / MANUAL=主管直接輸入',
  ADD COLUMN `sample_size` INT NULL
      COMMENT 'HISTORICAL 模式下，算出這組區間時實際用了多少筆有效開團紀錄；MANUAL 模式為 NULL',
  ADD COLUMN `includes_simulated` BOOLEAN NULL
      COMMENT 'HISTORICAL 模式下，用來計算的樣本是否含模擬資料（group_buy_records.is_simulated）；MANUAL 模式為 NULL',
  ADD COLUMN `computed_at` DATETIME NULL
      COMMENT 'HISTORICAL 模式下，這組區間最後一次被計算並凍結的時間；MANUAL 模式為 NULL',
  ADD COLUMN `updated_at` DATETIME NULL
      COMMENT '最後一次異動時間，不論是切到 HISTORICAL 觸發重算、還是 MANUAL 手動輸入',
  ADD COLUMN `updated_by` BIGINT NULL
      COMMENT '最後一次異動的操作者';

-- 既有種子資料（V2 建立的全域保底區間）語意上是手動填入的固定數字，
-- source_mode 的 DEFAULT 'MANUAL' 已經處理，不需要額外 UPDATE。
