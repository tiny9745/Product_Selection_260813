package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

/**
 * 人工估值（0~1 小數）× 固定倍率，clamp 到 0~100。
 *
 * 邏輯比照既有 ProductFactorScorer.scorePurchaseRate()：預設倍率 100，
 * 可透過 strategyParams 的 "scale" 覆寫。
 *
 * 2026-09-20更新：固定的 Product 欄位裡仍然沒有第二個「0~1 小數人工估值」
 * 可以綁定（唯一現成的 estimated_purchase_rate 已經被 PURCHASE_RATE 用掉），
 * 但「開新計分因子資料源」最後一階段上線後，管理層可以在設定頁新增一個
 * `PERCENT_0_1` 型態的自訂商品屬性題目，這個策略就能綁定它——見
 * FactorRawValueResolver，資料源不再限定 FactorDataSource 這個固定 enum。
 */
@Component
public class ManualPercentStrategy implements FactorCalculationStrategy {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	@Override
	public FactorStrategyCode getCode() {
		return FactorStrategyCode.MANUAL_PERCENT;
	}

	@Override
	public BigDecimal calculate(Product product, FactorDefinition definition, Map<Long, BigDecimal> customFieldValues) {
		BigDecimal raw = FactorRawValueResolver.resolve(product, definition, customFieldValues);
		if (raw == null) {
			return null;
		}
		BigDecimal scale = resolveScale(definition);
		return ScoringAlgorithms.clamp(raw.multiply(scale), BigDecimal.ZERO, HUNDRED);
	}

	private BigDecimal resolveScale(FactorDefinition definition) {
		if (definition.getStrategyParams() == null) {
			return HUNDRED;
		}
		return definition.getStrategyParams().getOrDefault("scale", HUNDRED);
	}
}
