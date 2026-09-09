package com.example.Product_Selection_260813.service.resolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.Product;

/**
 * MOQ 三層解析：商品 → 品類 → 全域。
 *
 * <b>null 與 0 的語意必須分開</b>：
 * <ul>
 * <li>{@code moq = null} 代表「繼承上層」</li>
 * <li>{@code moq = 0} 代表「明確表示無最低量限制」</li>
 * </ul>
 * 兩者不能混為一談。Jakarta Validation 的 @PositiveOrZero 允許 0，
 * 這裡的 null 判斷也必須用 {@code != null} 而非 {@code > 0}，否則填 0 的商品
 * 會被誤判成未填而去繼承品類預設值。
 */
@Component
public class MoqResolver {

	private final ProductTypeAttributeResolver attributeResolver;
	private final AlgorithmSettings algorithmSettings;

	@Autowired
	public MoqResolver(ProductTypeAttributeResolver attributeResolver, AlgorithmSettings algorithmSettings) {
		this.attributeResolver = attributeResolver;
		this.algorithmSettings = algorithmSettings;
	}

	public ResolvedValue<Integer> resolve(Product product) {
		if (product == null) {
			return ResolvedValue.none();
		}
		// 注意這裡是 != null 而非 > 0：moq=0 是合法且有意義的值
		if (product.getMoq() != null) {
			return ResolvedValue.ofProduct(product.getMoq());
		}

		ResolvedProductTypeAttributes attrs = attributeResolver.resolve(product.getProductTypeId());
		if (attrs.defaultMoq().hasValue()) {
			return ResolvedValue.ofProductType(attrs.defaultMoq().value());
		}

		Integer globalDefault = algorithmSettings.getDefaultMoq();
		if (globalDefault != null) {
			return ResolvedValue.ofGlobalDefault(globalDefault);
		}
		// 三層皆無值。呼叫端（GATE_MOQ_FEASIBILITY）應標示「MOQ 未設定，無法評估」，
		// 而不是當成 0 通過判定。
		return ResolvedValue.none();
	}
}
