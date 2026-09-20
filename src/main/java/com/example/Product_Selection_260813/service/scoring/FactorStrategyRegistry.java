package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;

/**
 * 依 FactorDefinition.strategyCode 把計分工作分派到對應的
 * {@link FactorCalculationStrategy} 實作。
 *
 * 用 Spring 自動收集所有 FactorCalculationStrategy Bean 組表，新增一種策略
 * 只要新增一個 @Component 類別實作這個介面，這裡完全不用改。
 */
@Component
public class FactorStrategyRegistry {

	private final Map<FactorStrategyCode, FactorCalculationStrategy> strategiesByCode;

	@Autowired
	public FactorStrategyRegistry(List<FactorCalculationStrategy> strategies) {
		this.strategiesByCode = strategies.stream()
				.collect(Collectors.toMap(FactorCalculationStrategy::getCode, Function.identity()));
	}

	/**
	 * @return 計算結果；strategyCode 沒有對應實作時回傳 null 而非拋例外——
	 *         理論上不會發生（建立 FactorDefinition 時已驗證過 strategyCode
	 *         必須是已實作的策略），但計分流程不該因為一筆設定異常的自訂因子
	 *         而整個中斷，寧可讓這一項從分母排除。
	 */
	public BigDecimal calculate(Product product, FactorDefinition definition) {
		FactorCalculationStrategy strategy = strategiesByCode.get(definition.getStrategyCode());
		return strategy == null ? null : strategy.calculate(product, definition);
	}

	/** 供 SettingsService 驗證用：建立自訂因子時，strategyCode 必須是這裡真的有實作的。 */
	public boolean isImplemented(FactorStrategyCode strategyCode) {
		return strategiesByCode.containsKey(strategyCode);
	}
}
