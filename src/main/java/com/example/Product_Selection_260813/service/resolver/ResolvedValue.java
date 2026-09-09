package com.example.Product_Selection_260813.service.resolver;

/**
 * 三層解析的結果：值 + 來源層級。
 *
 * <b>為什麼要一併回傳來源</b>：品類預設值之後可能被改動。審核快照若只存數值
 * 不存來源，稽核時無法判斷這個 MOQ 當初是商品自己指定的、還是繼承自品類的。
 * 兩者在追查「當初為什麼這樣判斷」時意義完全不同。
 */
public record ResolvedValue<T>(T value, Source source) {

	public enum Source {
		/** 商品層明確指定 */
		PRODUCT,
		/** 繼承自品類（小類或大類） */
		PRODUCT_TYPE,
		/** 系統全域預設 */
		GLOBAL_DEFAULT,
		/** 三層皆無值 */
		NONE
	}

	public static <T> ResolvedValue<T> ofProduct(T value) {
		return new ResolvedValue<>(value, Source.PRODUCT);
	}

	public static <T> ResolvedValue<T> ofProductType(T value) {
		return new ResolvedValue<>(value, Source.PRODUCT_TYPE);
	}

	public static <T> ResolvedValue<T> ofGlobalDefault(T value) {
		return new ResolvedValue<>(value, Source.GLOBAL_DEFAULT);
	}

	public static <T> ResolvedValue<T> none() {
		return new ResolvedValue<>(null, Source.NONE);
	}

	public boolean hasValue() {
		return value != null;
	}
}
