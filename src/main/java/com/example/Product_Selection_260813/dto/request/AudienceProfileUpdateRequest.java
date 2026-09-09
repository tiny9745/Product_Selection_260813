package com.example.Product_Selection_260813.dto.request;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.enums.PriceSensitivityStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/settings/audience-profile 的 Request Body。
 *
 * 整份覆蓋既有「使用中」核心客群設定（不含version/is_active——這兩欄本階段
 * 僅預留、不開放外部指定，見AudienceProfileResponse類別註解）。
 */
public class AudienceProfileUpdateRequest {

	@NotBlank(message = "核心客群名稱不可為空")
	@Size(max = 100, message = ValidationMessage.AUDIENCE_NAME_TOO_LONG)
	private String name;

	// 年齡上下界只做單欄位的合理值域驗證；「ageMin不可大於ageMax」是跨欄位的
	// 商業邏輯，放在SettingsService.updateAudienceProfile()攔截。
	@Min(value = 0, message = ValidationMessage.AUDIENCE_AGE_MIN_RANGE)
	@Max(value = 150, message = ValidationMessage.AUDIENCE_AGE_MIN_RANGE)
	private Integer ageMin;

	@Min(value = 0, message = ValidationMessage.AUDIENCE_AGE_MAX_RANGE)
	@Max(value = 150, message = ValidationMessage.AUDIENCE_AGE_MAX_RANGE)
	private Integer ageMax;

	private PriceSensitivityStatus priceSensitivity;

	// preferenceDescription對應TEXT欄位，不設長度上限
	private String preferenceDescription;

	@Size(max = 500, message = ValidationMessage.AUDIENCE_KEYWORDS_TOO_LONG)
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
