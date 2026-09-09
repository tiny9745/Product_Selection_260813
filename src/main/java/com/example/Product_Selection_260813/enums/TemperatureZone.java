package com.example.Product_Selection_260813.enums;

/**
 * 商品溫層。供 GATE_TEMPERATURE_ZONE 與 system_settings.supported_temperature_zones 比對。
 *
 * 注意：雞蛋等易碎品不是溫層問題，是處理特性，由 products.handling_flags 的
 * FRAGILE 標記處理，不要混進這個列舉。
 */
public enum TemperatureZone {
	NORMAL("常溫"), //
	CHILLED("冷藏"), //
	FROZEN("冷凍");

	private final String label;

	private TemperatureZone(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
