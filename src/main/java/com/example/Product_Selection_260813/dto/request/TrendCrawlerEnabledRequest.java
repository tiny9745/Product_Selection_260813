package com.example.Product_Selection_260813.dto.request;

import jakarta.validation.constraints.NotNull;

/** PUT /api/settings/trend-crawler/enabled：啟用或停用 PTT 熱度來源。 */
public class TrendCrawlerEnabledRequest {

	@NotNull(message = "必須指定啟用或停用")
	private Boolean enabled;

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
}
