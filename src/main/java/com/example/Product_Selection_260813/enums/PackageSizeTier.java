package com.example.Product_Selection_260813.enums;

/**
 * 材積級距。運費估算依此級距查 system_settings 的 freight_cost_xs/s/m/l。
 *
 * 大型商品不做成 Gate 而是讓運費吃掉毛利——「不划算」是經濟性問題不是可行性問題，
 * 做成 Gate 會誤擋那些偶爾真的划算的案子（見設計文件 1.3）。
 */
public enum PackageSizeTier {
	XS("極小（可放信封）", "freight_cost_xs"), //
	S("小（單手可拿）", "freight_cost_s"), //
	M("中（一般紙箱）", "freight_cost_m"), //
	L("大（需兩人搬）", "freight_cost_l");

	private final String label;
	private final String freightSettingKey;

	private PackageSizeTier(String label, String freightSettingKey) {
		this.label = label;
		this.freightSettingKey = freightSettingKey;
	}

	public String getLabel() {
		return label;
	}

	/** 對應的 system_settings 運費設定 key。 */
	public String getFreightSettingKey() {
		return freightSettingKey;
	}
}
