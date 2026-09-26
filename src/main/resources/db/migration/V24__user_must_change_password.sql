-- =====================================================================
-- V24：帳號密碼生命週期 — 強制下次登入修改密碼
--
-- 「建立帳號」與「管理者代重設密碼」共用同一套規則：密碼由管理者設定後，
-- must_change_password = 1，使用者下次登入時被強制導去修改密碼，
-- 管理者事後不會知道使用者「正在使用」的正式密碼。
--
-- 強制力不只在前端 Route Guard：JWT 內帶 mustChangePassword claim，
-- JwtAuthenticationFilter 看到這個 claim 時只放行 /api/auth/**，其餘 API
-- 一律回 403（見該類別說明）。使用者透過既有的 PATCH /api/auth/me/password
-- 修改成功後，此欄位回到 0，並重新簽發不帶此 claim 的 token。
--
-- 既有帳號（含 DevUserSeeder 建立的測試帳號）預設 0，不受影響。
-- =====================================================================

ALTER TABLE `app_users`
  ADD COLUMN `must_change_password` tinyint(1) NOT NULL DEFAULT '0'
      COMMENT '1=下次登入必須先修改密碼（帳號建立／管理者代重設密碼後設為1，使用者自行修改密碼成功後回到0）'
      AFTER `enabled`;
