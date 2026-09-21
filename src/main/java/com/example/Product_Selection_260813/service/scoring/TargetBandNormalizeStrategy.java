package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.enums.FactorStrategyCode;
import com.example.Product_Selection_260813.service.resolver.ScoreBandResolver;

/**
 * 依品類的固定目標區間正規化，邏輯比照既有 ProductFactorScorer.normalizeWithBand()。
 *
 * 完全重用既有的 {@link ScoreBandResolver}——它本來就是依任意 factorCode 字串查詢，
 * 不是寫死 MARGIN_RATE／DISCOUNT_DEPTH，所以這個策略不需要新增任何查詢邏輯：
 * 自訂因子的目標區間直接透過既有「設定 > 目標區間」頁面新增資料即可生效。
 *
 * 查無區間時回傳 null（從加權分母排除），不自行編一組區間——理由與既有
 * normalizeWithBand() 完全相同：編出來的區間會讓分數看起來正常但實際無依據。
 */
@Component
public class TargetBandNormalizeStrategy implements FactorCalculationStrategy {

	private final ScoreBandResolver scoreBandResolver;

	@Autowired
	public TargetBandNormalizeStrategy(ScoreBandResolver scoreBandResolver) {
		this.scoreBandResolver = scoreBandResolver;
	}

	@Override
	public FactorStrategyCode getCode() {
		return FactorStrategyCode.TARGET_BAND_NORMALIZE;
	}

	@Override
	public BigDecimal calculate(Product product, FactorDefinition definition, Map<Long, BigDecimal> customFieldValues) {
		BigDecimal raw = FactorRawValueResolver.resolve(product, definition, customFieldValues);
		if (raw == null) {
			return null;
		}
		Optional<ProductTypeScoreBand> band = scoreBandResolver.resolve(product.getProductTypeId(),
				definition.getFactorCode());
		if (band.isEmpty()) {
			return null;
		}
		return ScoringAlgorithms.normalizeByBand(raw, band.get().getLowerBound(), band.get().getUpperBound());
	}
}
