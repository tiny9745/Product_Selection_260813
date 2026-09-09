package com.example.Product_Selection_260813.service.resolver;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.ProductTypeScoreBand;
import com.example.Product_Selection_260813.repository.ProductTypeScoreBandRepository;

/**
 * 固定目標區間解析：大類專屬區間 → 全域預設區間。
 *
 * 區間查的是<b>大類</b>而非小類——小類的商品數往往不足以支撐一組穩定的區間，
 * 而且區間本來就是為了「跨品類可比」而存在，切太細會失去意義。
 */
@Component
public class ScoreBandResolver {

	/** 因子代碼：毛利率（已扣運費）。 */
	public static final String FACTOR_MARGIN_RATE = "MARGIN_RATE";
	/** 因子代碼：折扣深度。 */
	public static final String FACTOR_DISCOUNT_DEPTH = "DISCOUNT_DEPTH";

	private final ProductTypeScoreBandRepository bandRepository;
	private final ProductTypeAttributeResolver attributeResolver;

	@Autowired
	public ScoreBandResolver(ProductTypeScoreBandRepository bandRepository,
			ProductTypeAttributeResolver attributeResolver) {
		this.bandRepository = bandRepository;
		this.attributeResolver = attributeResolver;
	}

	/**
	 * 取得指定商品品類在該因子的生效區間。
	 *
	 * @return 找不到任何區間時回傳 empty；呼叫端應把該因子視為無法計分並從
	 *         加權分母排除，而不是自行編一組區間
	 */
	public Optional<ProductTypeScoreBand> resolve(Long productTypeId, String factorCode) {
		Long rootTypeId = attributeResolver.resolveRootTypeId(productTypeId);
		if (rootTypeId != null) {
			Optional<ProductTypeScoreBand> typeBand =
					bandRepository.findActiveByTypeAndFactor(rootTypeId, factorCode);
			if (typeBand.isPresent()) {
				return typeBand;
			}
		}
		return bandRepository.findActiveGlobalByFactor(factorCode);
	}
}
