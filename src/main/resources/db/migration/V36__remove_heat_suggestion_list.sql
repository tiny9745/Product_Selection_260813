-- =====================================================================
-- V36：移除熱度建議清單與 03:00 熱度規則選品批次（2026-09-29 決議 B）
--
-- 原本的批次會把操作人員已建立、待審中（review_status=PENDING）的商品，從 CANDIDATE 改成 AI_SUGGESTED，
-- 使商品從品項管理清單、選品審核待審清單、推薦 Top 10 消失；轉回候選後隔天又可能被改回去。
-- 程式已移除所有產生與讀取 AI_SUGGESTED 的路徑，這裡把既有資料轉回正式候選：
--   * 商品重新出現在品項管理清單、待審清單（review_status 與 submission_count 不變，不算重新送審）。
--   * updated_at = updated_at：明確指定原值，避免 ON UPDATE CURRENT_TIMESTAMP 把「最後更新時間」
--     改成 migration 執行時間（這些商品的資料內容並沒有被使用者編輯）。
--
-- 刻意不修改 candidate_status 欄位的 ENUM 定義：ENUM 值變動牽涉 Hibernate schema validate
-- （ddl-auto=validate），Java 端 ProductCandidateStatus.AI_SUGGESTED 改標 @Deprecated 保留，
-- 兩邊定義維持一致；欄位目前只會有 CANDIDATE，日後要整個移除 candidate_status 再一併處理。
-- 本 migration 可重複執行而不產生副作用（沒有 AI_SUGGESTED 時更新 0 筆）。
-- =====================================================================

UPDATE `products`
   SET `candidate_status` = 'CANDIDATE',
       `updated_at` = `updated_at`
 WHERE `candidate_status` = 'AI_SUGGESTED';
