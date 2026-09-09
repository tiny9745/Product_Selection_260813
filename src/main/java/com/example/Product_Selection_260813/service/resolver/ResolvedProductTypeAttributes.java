package com.example.Product_Selection_260813.service.resolver;

/**
 * 品類屬性繼承解析的結果（小類 → 大類 → 未設定）。
 *
 * 每個屬性都帶自己的來源層級，因為同一件商品可能部分屬性來自小類、
 * 部分來自大類——前端要顯示「繼承自品類」提示時需要知道是哪一層。
 */
public record ResolvedProductTypeAttributes(
		Long leafTypeId,
		Long rootTypeId,
		ResolvedValue<String> temperatureZone,
		ResolvedValue<Boolean> hasShelfLife,
		ResolvedValue<String> shelfLifeTier,
		ResolvedValue<String> returnPolicy,
		ResolvedValue<Integer> shelfLifeThresholdDays,
		ResolvedValue<Integer> defaultMoq,
		ResolvedValue<String> requiredCertification,
		ResolvedValue<Long> evaluationModeId) {
}
