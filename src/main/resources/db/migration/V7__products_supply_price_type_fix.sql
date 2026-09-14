-- =====================================================================
-- V7：修正 supply_stability／price_competitiveness 的實際型別為 INT
--
-- 【為什麼需要這支，不能直接改 V6】
-- V6 把這兩個欄位改成 TINYINT，但 Java 端的 Entity（Product.java）宣告
-- 的型別是 Integer——Hibernate 對 Integer 的預設 SQL 對應是 INTEGER，
-- 不是 TINYINT，兩者在 Hibernate 的 schema 驗證（ddl-auto=validate）
-- 下被視為不同型別，應用程式啟動時直接被擋下：
--
--   Schema validation: wrong column type encountered in column
--   [price_competitiveness] in table [products];
--   found [tinyint (Types#TINYINT)], but expecting [integer (Types#INTEGER)]
--
-- V6 已經被執行過、Flyway 已經記錄它的 checksum，直接修改 V6 檔案內容
-- 會導致下次啟動時 Flyway 自己先報「migration checksum 不符」而失敗
-- ——已套用的 migration 視為不可變更，要修正只能新增下一版，這是
-- Flyway 的基本規則，不是這次才決定的。
--
-- 【為什麼不維持 TINYINT、改讓 Java 端配合它】
-- 讓 Java 端遷就 TINYINT 需要在 Entity 加上
-- @Column(columnDefinition = "TINYINT")，這種寫法讓 Hibernate 完全放棄
-- 自動判斷該欄位的 SQL 型別、變成整段交由這個手動指定的字串決定，
-- 一來跟專案裡其他 Integer 欄位（如 moq）的宣告方式不一致，二來
-- TINYINT 對 1~5 這種資料省下的儲存空間，在商品這種筆數的資料表上
-- 完全可以忽略不計。改資料庫遷就 Java 端的預設慣例，比讓 Java 端遷就
-- 資料庫的省空間考量更省事、更不容易在日後又踩到同一種型別落差。
-- =====================================================================

ALTER TABLE `products`
  MODIFY COLUMN `supply_stability` INT NULL
    COMMENT '供應穩定性等級：1=嚴重缺貨／2=暫時缺貨／3=供應普通／4=供應穩定／5=供應充足',
  MODIFY COLUMN `price_competitiveness` INT NULL
    COMMENT '價格競爭力等級：1=價格缺乏競爭力／2=價格偏高／3=價格普通／4=具價格競爭力／5=高度具競爭力';
