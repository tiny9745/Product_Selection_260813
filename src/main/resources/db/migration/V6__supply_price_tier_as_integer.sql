-- =====================================================================
-- V6：供應穩定性／價格競爭力改為整數等級
--
-- 原本 products.supply_stability / price_competitiveness 是
-- decimal(5,2)，畫面上直接顯示像「4.50」這種數字給採購人員看，
-- 沒有直覺的業務意義。改成 1~5 的整數等級，前端對應顯示成文字敘述
-- （1=嚴重缺貨/價格缺乏競爭力…5=供應充足/高度具競爭力），數字本身
-- 只是儲存用的代碼，不會直接出現在畫面上。
--
-- 【為什麼在轉型別前先做一次 UPDATE】
-- ALTER TABLE ... MODIFY COLUMN 從 decimal 轉 tinyint，MySQL 預設行為
-- 是無條件捨去小數部分（3.5 會變成 3，不是四捨五入到 4），這樣換算
-- 出來的等級會比實際偏低、失真。因此先用 ROUND() 明確做四捨五入寫回
-- 原本的 decimal 欄位，確認數字正確之後才轉型別，讓型別轉換本身只是
-- 單純的「decimal 3.00 存成 tinyint 3」，不含任何捨去邏輯。
--
-- 【為什麼是 tinyint 不是 int】
-- 合法值只有 1~5，tinyint（1 byte）綽綽有餘，用 int（4 byte）沒有
-- 額外的正確性好處，只是浪費儲存空間。
-- =====================================================================

UPDATE `products` SET `supply_stability` = ROUND(`supply_stability`)
  WHERE `supply_stability` IS NOT NULL;

UPDATE `products` SET `price_competitiveness` = ROUND(`price_competitiveness`)
  WHERE `price_competitiveness` IS NOT NULL;

ALTER TABLE `products`
  MODIFY COLUMN `supply_stability` TINYINT NULL
    COMMENT '供應穩定性等級：1=嚴重缺貨／2=暫時缺貨／3=供應普通／4=供應穩定／5=供應充足',
  MODIFY COLUMN `price_competitiveness` TINYINT NULL
    COMMENT '價格競爭力等級：1=價格缺乏競爭力／2=價格偏高／3=價格普通／4=具價格競爭力／5=高度具競爭力';
