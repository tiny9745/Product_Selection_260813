-- =====================================================================
-- V27：重設密碼申請（2026-09-26 決議）
--
-- 管理者代重設密碼（V24）改為「必須先有使用者本人的申請」才能執行：
--   1. 使用者在登入頁「忘記密碼？申請重設」輸入帳號送出（不需登入）。
--   2. 帳號管理表格顯示「申請重設中」，管理者才能對該帳號按「重設密碼」，或駁回申請。
--   3. 重設完成／駁回後，申請結案（COMPLETED／REJECTED），保留紀錄供稽核。
--
-- 防濫用：申請 API 不論帳號是否存在一律回同一段訊息（避免被拿來試出哪些帳號存在），
-- 且同一帳號同時只保留一筆 PENDING 申請（重複送出不會新增）；已停用帳號的申請直接忽略。
-- 帳號疑似外洩等緊急狀況不走申請流程，改由管理者直接「停用帳號」（停用會讓現有登入立即失效）。
-- =====================================================================

CREATE TABLE `password_reset_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申請唯一識別碼',
  `user_id` bigint NOT NULL COMMENT '申請重設密碼的帳號',
  `status` enum('PENDING','COMPLETED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING=待處理／COMPLETED=管理者已重設密碼／REJECTED=管理者駁回',
  `requested_at` datetime NOT NULL COMMENT '申請時間',
  `handled_at` datetime DEFAULT NULL COMMENT '結案時間（重設或駁回）',
  `handled_by` bigint DEFAULT NULL COMMENT '結案的管理者',
  PRIMARY KEY (`id`),
  KEY `idx_password_reset_requests_user_status` (`user_id`, `status`),
  KEY `idx_password_reset_requests_status` (`status`),
  CONSTRAINT `fk_password_reset_requests_user` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_password_reset_requests_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='重設密碼申請：管理者只能重設有待處理申請的帳號；結案後保留供稽核';
