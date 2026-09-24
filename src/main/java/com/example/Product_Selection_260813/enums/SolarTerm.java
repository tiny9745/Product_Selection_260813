package com.example.Product_Selection_260813.enums;

/**
 * 日期規則可使用的節氣（V21）。
 *
 * 只收錄目前有業務需求的兩個節氣：清明（掃墓、踏青）、冬至（湯圓）。
 * 日期由 SolarTermCalculator 從內建節氣表（2000–2099，UTC+8）查出。
 */
public enum SolarTerm {
	QINGMING("清明"),
	DONGZHI("冬至");

	private final String displayName;

	SolarTerm(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
