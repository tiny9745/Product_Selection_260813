package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.enums.PriceSensitivityStatus;

import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/settings/audience-profile 的 Request Body。
 *
 * 整份覆蓋既有「使用中」核心客群設定（不含version/is_active——這兩欄本階段
 * 僅預留、不開放外部指定，見AudienceProfileResponse類別註解）。
 */
public class AudienceProfileUpdateRequest {

	@NotBlank(message = "核心客群名稱不可為空")
	private String name;

	private Integer ageMin;
	private Integer ageMax;
	private PriceSensitivityStatus priceSensitivity;
	private String preferenceDescription;
	private String keywords;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getAgeMin() {
		return ageMin;
	}

	public void setAgeMin(Integer ageMin) {
		this.ageMin = ageMin;
	}

	public Integer getAgeMax() {
		return ageMax;
	}

	public void setAgeMax(Integer ageMax) {
		this.ageMax = ageMax;
	}

	public PriceSensitivityStatus getPriceSensitivity() {
		return priceSensitivity;
	}

	public void setPriceSensitivity(PriceSensitivityStatus priceSensitivity) {
		this.priceSensitivity = priceSensitivity;
	}

	public String getPreferenceDescription() {
		return preferenceDescription;
	}

	public void setPreferenceDescription(String preferenceDescription) {
		this.preferenceDescription = preferenceDescription;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}
}
