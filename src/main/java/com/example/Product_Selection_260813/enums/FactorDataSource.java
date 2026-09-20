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
 * 2026-09-20 再次盤點 Product 既有欄位後新增 {@link #MOQ}／
 * {@link #SUPPLIER_MAX_CAPACITY} 兩個候選，兩者都完全沒有被任何既有因子使用，
 * 且都是「原始數字，需要依品類設定合理區間」的形狀，剛好對上原本已經寫好、
 * 但一直沒有資料源可綁的 {@link FactorStrategyCode#TARGET_BAND_NORMALIZE}——
 * 沿用既有的「設定 > 目標區間」頁面即可設定各品類的合理範圍，不需要新增
 * 任何查詢或計算邏輯。
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
	},

	/**
	 * 最低訂購量（products.moq）。目前只餵給 GATE_MOQ_FEASIBILITY（可行性關卡）
	 * 判斷用，沒有任何計分因子在用它。
	 *
	 * ⚠️ 這裡只讀商品自己填的原始值，<b>不</b>走 {@code MoqResolver} 的三層
	 * 解析（商品→品類→全域預設）——跟既有 MARGIN_RATE／DISCOUNT_DEPTH 讀
	 * cost_price／sale_price 一樣是「只看這個商品自己填了什麼」，沒有繼承
	 * 上層預設值的行為，維持跟其他因子一致的簡單語意。這代表：商品自己沒填
	 * MOQ、只靠品類或全域預設值的情況下，這個因子會是 null（從分母排除），
	 * 即使 Gate 那邊靠三層解析判定「可行」也一樣——兩邊語意刻意不同步，
	 * 因為 Gate 要回答「能不能進」，因子要回答「這個商品自己填的數字好不好」。
	 */
	MOQ(FactorStrategyCode.TARGET_BAND_NORMALIZE) {
		@Override
		public BigDecimal extractRawValue(Product product) {
			Integer raw = product.getMoq();
			return raw == null ? null : BigDecimal.valueOf(raw);
		}
	},

	/**
	 * 供應商最大產能（products.supplier_max_capacity）。目前完全沒有被任何
	 * Gate 或計分因子使用，純粹是建立商品時填寫、附進 AI 分析文字與審核
	 * Snapshot 的欄位，是這次盤點裡最乾淨（沒有既有語意要顧慮）的候選。
	 */
	SUPPLIER_MAX_CAPACITY(FactorStrategyCode.TARGET_BAND_NORMALIZE) {
		@Override
		public BigDecimal extractRawValue(Product product) {
			Integer raw = product.getSupplierMaxCapacity();
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
