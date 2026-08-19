package com.example.Product_Selection_260813.enums;

public enum TrendSignalTrendDirection {
	UP("上升"), //
	DOWN("下滑"), //
	STABLE("持平");
	
	private final String trendSignalTrendDirection;

	private TrendSignalTrendDirection(String trendSignalTrendDirection) {
		this.trendSignalTrendDirection = trendSignalTrendDirection;
	}

	public String getTrendSignalTrendDirection() {
		return trendSignalTrendDirection;
	}
}
