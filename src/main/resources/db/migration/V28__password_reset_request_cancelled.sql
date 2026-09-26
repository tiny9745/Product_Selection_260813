-- =====================================================================
-- V28：重設密碼申請新增 CANCELLED（2026-09-26 決議）
--
-- 使用者送出重設密碼申請後，若在管理者處理之前就用原本的密碼成功登入（想起密碼了），
-- 代表已不需要重設：登入成功時自動把該帳號的待處理申請結案為 CANCELLED，
-- 管理者的帳號管理表格不再顯示「申請重設密碼」，也無法再對它執行重設（沒有待處理申請＝409）。
--
-- CANCELLED 的 handled_by 為 NULL（不是管理者結案，是使用者本人登入觸發）。
-- 另開一支 migration 而不改 V27：V27 可能已在其他環境套用並記錄 checksum。
-- =====================================================================

ALTER TABLE `password_reset_requests`
  MODIFY COLUMN `status` enum('PENDING','COMPLETED','REJECTED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING=待處理／COMPLETED=管理者已重設密碼／REJECTED=管理者駁回／CANCELLED=使用者本人已成功登入，申請自動取消',
  MODIFY COLUMN `handled_by` bigint DEFAULT NULL
      COMMENT '結案的管理者；CANCELLED（使用者本人登入自動取消）為 NULL';
