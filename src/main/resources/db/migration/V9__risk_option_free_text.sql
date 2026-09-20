-- =====================================================================
-- V9：人工風險評估支援「其他」選項自由輸入文字
--
-- 【為什麼用一個新欄位標記，而不是前端原本的名稱比對】
-- 前端原本用 option.name === '其他' 判斷要不要顯示補充說明欄位，這在畫面
-- 展示邏輯上還可以接受，但後端如果也用中文字串比對來決定要不要寫入
-- manual_note，會很脆弱——只要有人在設定頁把這個選項改名，邏輯就跟著壞掉，
-- 且不會有任何錯誤訊息提示。改用明確欄位 is_free_text_option，語意固定、
-- 不受畫面顯示名稱影響，比照 risk_options 既有的 auto_trigger_code
-- （用欄位而非猜測名稱字串找出「這是 Gate 自動帶入的選項」）同一套設計原則。
--
-- 【is_free_text_option 為什麼不開放透過 API 指定】
-- 比照 is_system_default 的既有原則（見 RiskOptionCreateRequest 類別註解：
-- 「isSystemDefault／isActive不開放外部指定」）：如果任何人可以透過
-- POST/PUT /api/settings/risk-options 把任意選項標成可自由輸入文字，
-- ReviewService 寫入 manual_note 時要處理「這次審核勾了兩個可自由輸入的
-- 選項，文字要寫到哪一個」這種原本不該存在的情境。固定只有這裡建立的
-- 「其他」一筆是 true，新增/修改風險選項的 DTO 刻意不開放這個欄位。
--
-- 【manual_note 為什麼放在 review_risks 而不是 review_records】
-- 這段文字語意上是「勾選這個風險選項當下的補充說明」，跟既有的
-- trigger_reason（系統帶入原因）是同一層級的概念，只是來源相反
-- （主管手動填 vs 系統自動填）。放在 review_risks 完全不影響
-- review_records／Snapshot 的既有結構，也不影響任何過往審核紀錄——
-- review_records.weight_snapshot 等快照欄位是 JSON blob，跟這張表沒有
-- 外鍵關聯，這次異動不會讓任何歷史資料「被動」改變。
-- =====================================================================

ALTER TABLE `risk_options`
  ADD COLUMN `is_free_text_option` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '此選項是否允許主管勾選時自由輸入補充文字（見review_risks.manual_note）。目前僅系統預設的「其他」選項為1，不開放透過API建立/修改為1';

ALTER TABLE `review_risks`
  ADD COLUMN `manual_note` VARCHAR(500) NULL
    COMMENT '主管勾選自由輸入型風險選項（目前僅「其他」）時填寫的補充說明；其餘一般風險選項此欄位恆為NULL。與trigger_reason語意相對：trigger_reason是系統帶入的原因，這欄是主管自己寫的內容';

-- 若先前已有人手動在設定頁建立過同名「其他」選項，只補上兩個標記欄位，
-- 不新增重複列。
UPDATE `risk_options`
  SET `is_free_text_option` = 1, `is_system_default` = 1
  WHERE `name` = '其他';

-- 尚未存在時才新增，避免重複部署／重跑腳本時產生兩筆同名「其他」。
-- category刻意留NULL：這個選項不歸屬五大風險面向的任何一項，是自由文字的
-- 兜底選項，畫面上應獨立顯示，不塞進某一個分類群組裡。
INSERT INTO `risk_options` (`name`, `description`, `is_system_default`, `is_active`, `is_free_text_option`, `created_at`)
SELECT '其他', '未列於既有風險類型的其他考量，需自行輸入具體說明。', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `risk_options` WHERE `name` = '其他');
