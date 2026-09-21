package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.Map;

import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

/**
 * 自訂計分因子的運算邏輯介面。每個實作對應 {@link FactorStrategyCode} 的一個值，
 * 由 {@link FactorStrategyRegistry} 依 FactorDefinition.strategyCode 分派。
 *
 * 缺漏值一律回傳 null，不給中性值——沿用既有七個因子（ProductFactorScorer）
 * 的既定原則：「資料填齊但條件普通」和「什麼都沒填」不該拿到一樣的分數，
 * null 讓 weightedAverage() 把該因子從分母排除並重新正規化。
 */
public interface FactorCalculationStrategy {

	FactorStrategyCode getCode();

	/**
	 * @param customFieldValues 這個商品的自訂屬性答案（fieldDefinitionId →
	 *                           數值），供 {@link FactorRawValueResolver}
	 *                           取值用；見該類別註解。
	 * @return 0~100 的分數；商品該因子沒有可用資料時回傳 null
	 */
	BigDecimal calculate(Product product, FactorDefinition definition, Map<Long, BigDecimal> customFieldValues);
}
