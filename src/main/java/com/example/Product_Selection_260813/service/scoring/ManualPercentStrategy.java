package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

/**
 * 人工估值（0~1 小數）× 固定倍率，clamp 到 0~100。
 *
 * 邏輯比照既有 ProductFactorScorer.scorePurchaseRate()：預設倍率 100，
 * 可透過 strategyParams 的 "scale" 覆寫。目前系統沒有第二個「0~1 小數人工估值」
 * 欄位可以綁定這個策略（唯一現成的 estimated_purchase_rate 已經被 PURCHASE_RATE
 * 用掉），FactorDataSource 裡也還沒有對應的候選值——這個策略先實作好、
 * 等真的新增這種欄位時直接掛上去即可，不需要再動這個類別。
 */
@Component
public class ManualPercentStrategy implements FactorCalculationStrategy {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	@Override
	public FactorStrategyCode getCode() {
		return FactorStrategyCode.MANUAL_PERCENT;
	}

	@Override
	public BigDecimal calculate(Product product, FactorDefinition definition) {
		BigDecimal raw = definition.getDataSourceCode().extractRawValue(product);
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
