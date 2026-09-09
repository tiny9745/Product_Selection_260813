package com.example.Product_Selection_260813.enums;

/** 分裝方式。Signal 顯示用，不進計分。 */
public enum PackingType {
	WHOLE_CARTON("原箱直出"), //
	REPACK("需拆箱分裝");

	private final String label;

	private PackingType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
