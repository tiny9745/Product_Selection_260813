package com.example.Product_Selection_260813.dto.response;

import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.enums.PriceSensitivityStatus;

/**
 * GET／PUT /api/settings/audience-profile 共用。
 *
 * 對應單一「使用中」核心客群設定——version／is_active欄位本階段僅預留、
 * 不實作版本切換邏輯（企劃書四-6備註），故本端點固定操作is_active=true的那一筆，
 * 不提供依id查詢/切換版本的操作。
 */
public class AudienceProfileResponse {

	private Long id;
	private String name;
	private Integer ageMin;
	private Integer ageMax;
	private PriceSensitivityStatus priceSensitivity;
	private String preferenceDescription;
	private String keywords;

	public static AudienceProfileResponse from(AudienceProfile profile) {
		AudienceProfileResponse dto = new AudienceProfileResponse();
		dto.id = profile.getId();
		dto.name = profile.getName();
		dto.ageMin = profile.getAgeMin();
		dto.ageMax = profile.getAgeMax();
		dto.priceSensitivity = profile.getPriceSensitivity();
		dto.preferenceDescription = profile.getPreferenceDescription();
		dto.keywords = profile.getKeywords();
		return dto;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

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
