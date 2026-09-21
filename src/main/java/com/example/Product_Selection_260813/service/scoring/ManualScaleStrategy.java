package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

/**
 * 人工評分（例如 1~5）× 固定倍率，clamp 到 0~100。
 *
 * 邏輯完全比照既有 ProductFactorScorer.scoreSupplyStability()：預設倍率 20
 * （對應 1~5 分制），可透過 FactorDefinition.strategyParams 的 "scale" 覆寫，
 * 供之後如果新因子是別種分制（例如 1~10）時不用改程式碼、只要改設定值。
 */
@Component
public class ManualScaleStrategy implements FactorCalculationStrategy {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal DEFAULT_SCALE = BigDecimal.valueOf(20);

	@Override
	public FactorStrategyCode getCode() {
		return FactorStrategyCode.MANUAL_SCALE;
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
			return DEFAULT_SCALE;
		}
		return definition.getStrategyParams().getOrDefault("scale", DEFAULT_SCALE);
	}
}
