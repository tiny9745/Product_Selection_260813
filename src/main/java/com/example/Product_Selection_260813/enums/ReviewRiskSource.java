package com.example.Product_Selection_260813.enums;

/**
 * 審核風險項目的來源。
 *
 * 必須能區分系統帶入與主管手動勾選，否則審核快照會把 Gate 的自動判定
 * 偽裝成「主管確認的風險評估」，違反 human-in-the-loop 原則。
 *
 * 特別是 SYSTEM_AUTO + is_selected=false（系統判定但主管推翻）是稽核價值
 * 最高的一筆紀錄，不可用「不寫入」代替。
 */
public enum ReviewRiskSource {
	MANUAL("主管勾選"), //
	SYSTEM_AUTO("系統判定");

	private final String label;

	private ReviewRiskSource(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
