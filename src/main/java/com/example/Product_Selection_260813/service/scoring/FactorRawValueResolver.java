package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;

/**
 * 因子的原始輸入值有兩種可能來源（見 FactorDefinition 類別註解的二選一
 * 說明），三個 FactorCalculationStrategy 實作都要做同一件事：先分辨這個
 * 因子綁的是哪一種，再各自取值。抽成共用方法，不要讓三個策略類別各自
 * 重複寫一次同樣的 if/else 判斷。
 *
 * 沒有做成 Spring Bean：純函式、不需要注入任何依賴（customFieldValues
 * 由呼叫端——ProductFactorScorer.scoreAll()——一次查好整個 Map 傳進來，
 * 這裡只負責查表，見該類別註解說明為什麼要在那一層一次查完，避免每個
 * 因子各自查一次資料庫）。
 */
public final class FactorRawValueResolver {

	private FactorRawValueResolver() {
	}

	/**
	 * @param customFieldValues 這個商品目前所有自訂屬性答案，fieldDefinitionId
	 *                           → 數值（只含數值類，TEXT 型態的答案不會出現在
	 *                           這裡，也不該被拿來計分）。商品完全沒有任何
	 *                           自訂屬性答案時可以是空 Map，不能是 null——
	 *                           呼叫端（ProductFactorScorer）保證一定會傳一個
	 *                           非 null 的 Map，即使是空的。
	 * @return 原始數值；因子綁定的資料源目前查無值（商品沒填、或資料源設定
	 *         本身不完整）一律回傳 null，讓這個因子從加權分母排除，不給
	 *         中性值，沿用整個計分系統一致的既定原則。
	 */
	public static BigDecimal resolve(Product product, FactorDefinition definition,
			Map<Long, BigDecimal> customFieldValues) {
		if (definition.getCustomFieldDefinitionId() != null) {
			return customFieldValues == null ? null : customFieldValues.get(definition.getCustomFieldDefinitionId());
		}
		if (definition.getDataSourceCode() != null) {
			return definition.getDataSourceCode().extractRawValue(product);
		}
		// 理論上不會發生（建立因子時已驗證兩者恰好擇一），防禦性回傳null
		// 而非拋例外——一筆設定異常的自訂因子不該讓整條計分鏈路中斷。
		return null;
	}
}
