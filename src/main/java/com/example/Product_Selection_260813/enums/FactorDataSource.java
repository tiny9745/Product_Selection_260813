package com.example.Product_Selection_260813.enums;

import java.math.BigDecimal;

import com.example.Product_Selection_260813.entity.Product;

/**
 * 自訂計分因子可以綁定的「既有欄位」清單。
 *
 * 2026-09-20 與 Gary 確認：新因子的資料輸入只能是既有欄位，不開放新增資料庫欄位
 * 或新的人工填寫介面。這個 enum 就是「目前有哪些既有欄位可以拿來當新因子的
 * 資料源」的清單——每新增一個可綁定的欄位，就是多一個 enum 常數（幾行程式碼，
 * 不需要 migration、不需要動 Product 表結構），這正是方案 B 相對於通用組合引擎
 * （方案 A）成本低很多的地方。
 *
 * 目前只有 {@link #PRICE_COMPETITIVENESS} 一個候選：盤點 Product 既有欄位後
 * 發現「價格競爭力」（1~5 人工評分，商品建立時就會填、dataCompleteness 也已經
 * 在採計）目前完全沒有被任何因子使用，是唯一現成、不需要額外開發就能拿來驗證
 * 整套自訂因子機制的欄位，因此選它當第一個示範因子的資料源。
 */
public enum FactorDataSource {

	/**
	 * 價格競爭力（products.price_competitiveness，1~5 人工評分）。
	 * 目前沒有任何既有因子在用這個欄位，適合搭配 {@link FactorStrategyCode#MANUAL_SCALE}。
	 */
	PRICE_COMPETITIVENESS(FactorStrategyCode.MANUAL_SCALE) {
		@Override
		public BigDecimal extractRawValue(Product product) {
			Integer raw = product.getPriceCompetitiveness();
			return raw == null ? null : BigDecimal.valueOf(raw);
		}
	};

	private final FactorStrategyCode compatibleStrategy;

	FactorDataSource(FactorStrategyCode compatibleStrategy) {
		this.compatibleStrategy = compatibleStrategy;
	}

	/**
	 * 這個資料源設計時就是為哪一種運算邏輯準備的。建立自訂因子時，
	 * SettingsService 會檢查 dataSourceCode 與 strategyCode 是否對得上，
	 * 對不上直接拒絕——例如硬要把「1~5 人工評分」拿去套「目標區間正規化」，
	 * 數值範圍不合理，寧可在建立當下就擋下來，不要留到計分時才發現數字怪怪的。
	 */
	public FactorStrategyCode getCompatibleStrategy() {
		return compatibleStrategy;
	}

	/**
	 * 從商品讀出這個資料源的原始數值；商品該欄位未填時回傳 null，
	 * 由呼叫端（各 FactorCalculationStrategy）決定 null 要怎麼處理——
	 * 目前三個已實作的策略都遵循既有慣例：null 直接回傳 null，讓這個因子
	 * 從加權分母排除，不給中性值。
	 */
	public abstract BigDecimal extractRawValue(Product product);
}
