-- =====================================================================
-- V8：festive_campaigns 支援 WEATHER 類別（天氣型檔期）
--
-- 【為什麼要動這支，不能只改 Java 的 FestiveCategory enum】
-- category 欄位在 DB 是 MySQL ENUM('FESTIVAL','SEASON')，只改 Java 端
-- 的 enum 加上 WEATHER，Hibernate 在 ddl-auto=validate 模式下不會主動
-- 改 DB schema；真的寫入 category=WEATHER 時會直接被 DB 擋下
-- （Data truncated for column 'category'），不是應用程式層級的例外，
-- 是資料庫層級直接拒絕，錯誤訊息也不會明確指向「enum 需要擴充」，
-- 排查成本高，所以這支 migration 要跟 Java 端的 enum 修改一起進、
-- 不能省略。
--
-- 【weather_confidence 為什麼獨立一欄，不是複用 preparation_lead_days】
-- preparation_lead_days 驅動的是 ScoringService.calculateUrgencyFactor()
-- 既有的線性遞增邏輯（距開始日越近、urgency 越高），WEATHER 類別一樣
-- 需要這個機制，所以繼續共用 preparation_lead_days，不重複造欄位。
-- 但 WEATHER 類別多一個 FESTIVAL／SEASON 結構上不存在的維度：
-- 這筆天氣判斷本身有多可信（見 WeatherForecastConfidence）。這是
-- 「這筆檔期的屬性」，跟 urgency 計算的另一個獨立因子，因此獨立成欄，
-- 而不是想辦法塞進既有欄位、混淆兩種不同性質的數值。
-- 只有 category=WEATHER 的列才會填值，其餘維持 NULL。
-- =====================================================================

ALTER TABLE `festive_campaigns`
  MODIFY COLUMN `category` enum('FESTIVAL','SEASON','WEATHER') COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT 'FESTIVAL=節慶(固定日期型，如中秋節/雙11)／SEASON=季節(區間型，如夏季換季)／WEATHER=天氣(系統依天氣預報自動產生，見WeatherCampaignSyncService)',
  ADD COLUMN `weather_confidence` enum('HIGH','MEDIUM','LOW') COLLATE utf8mb4_unicode_ci NULL
    COMMENT '僅category=WEATHER時有值：這筆天氣訊號的預報可信度，隨預測距離現在的天數遞減，由WeatherCampaignSyncService寫入'
    AFTER `preparation_lead_days`;
