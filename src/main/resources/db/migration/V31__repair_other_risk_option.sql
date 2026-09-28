-- =====================================================================
-- V31（草案，需確認後才放進 db/migration）：修復全新資料庫上「其他」風險選項被覆寫的問題
--
-- 【問題還原】部署流程是 DROP DATABASE → CREATE DATABASE → Flyway 從 V1 跑起：
--   1. V9 建立「其他」時 risk_options 是空表，AUTO_INCREMENT 給它 id=1
--      （is_system_default=1、is_free_text_option=1）。
--   2. V16 以「WHERE NOT EXISTS (id=1)」判斷，id=1 已存在 → 靜默跳過「實際供貨風險」。
--   3. V18 以 ON DUPLICATE KEY UPDATE 強制寫入 id=1 → 把「其他」這一列的 name／
--      description／alert_keywords／category 改寫成「實際供貨風險」，但 V18 的
--      UPDATE 清單沒有 is_free_text_option，旗標仍是 1。
--   結果：「其他」整筆消失；「實際供貨風險」在審核頁會顯示自由輸入框，勾選時
--   ReviewService.saveReviewRisks() 會把補充說明寫進它的 manual_note。
--   已於 MySQL 8.0.46 以 V1~V30 全新套用實測重現。
--
-- 【修復】冪等，可重複執行；不動 id=1~3 的系統預設內容（V18 已保證正確）。
--   舊環境（「其他」原本就存在、id 不是 1）執行時第 1、2 句影響 0 筆，第 3 句只補旗標。
-- 【已知限制】全新資料庫在修復前若已有人審核時勾選「實際供貨風險」並填了補充說明，
--   那些 review_risks.manual_note 會留在 risk_option_id=1 上——審核紀錄不可覆寫，
--   不做搬移；以 DROP→CREATE 流程重建的環境不會有這種資料。
-- =====================================================================

UPDATE `risk_options`
   SET `is_free_text_option` = 0
 WHERE `is_free_text_option` = 1
   AND `name` <> '其他';

INSERT INTO `risk_options` (`name`, `description`, `is_system_default`, `is_active`, `is_free_text_option`, `created_at`)
SELECT '其他', '未列於既有風險類型的其他考量，需自行輸入具體說明。', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `risk_options` WHERE `name` = '其他');

UPDATE `risk_options`
   SET `is_free_text_option` = 1, `is_system_default` = 1
 WHERE `name` = '其他';
