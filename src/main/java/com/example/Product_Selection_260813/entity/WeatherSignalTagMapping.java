package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 天氣訊號類型 → 商品標籤（＋命中權重）的可調整對照表，取代原本寫死在
 * WeatherCampaignSyncService.WEATHER_TAG_MAPPING 裡的靜態常數。
 *
 * <b>為什麼要從靜態常數改成資料表：</b>節慶/季節檔期的標籤（festive_campaign_tags）
 * 管理層本來就能在設定頁自由調整，但天氣訊號要對應到哪些商品標籤、命中等級
 * 多高，卻只能改程式碼重新部署才能調整——同一套「Festival Boost」機制，
 * 節慶那端能自助設定，天氣這端不能，這是不一致、也是這次要補的落差。
 *
 * <b>與 festive_campaign_tags 的分工：</b>festive_campaign_tags 記錄的是
 * 「某一筆已存在的檔期」命中哪些標籤；這張表記錄的是「某一種天氣訊號類型」
 * 該對應哪些標籤，是 WeatherCampaignSyncService 每次 upsert 天氣檔期時的
 * 資料來源（見 syncOne()），檔期本身的 festive_campaign_tags 仍然是由
 * 這張表的內容複製產生，不是直接拿這張表取代 festive_campaign_tags。
 *
 * <b>NORMAL 為什麼不會出現在這張表：</b>一般天氣不該命中任何商品（見
 * WeatherCampaignSyncService 既有註解），這條業務規則在 SettingsService
 * 建立/編輯時擋下，不寫死在資料庫欄位型別層級——比照 risk_options.
 * is_free_text_option「限定其中一筆」的既有做法是用 Service 層驗證、
 * 不是用更窄的 DB 型別限制，日後如果這條規則要調整，不需要動 migration。
 *
 * 不使用 @ManyToOne 關聯：沿用本專案既有慣例（見 FestiveCampaignTag 類別
 * 註解），Entity 層維持 plain 欄位，不引入 JPA 關聯物件圖。
 */
@Entity
@Table(
		name = "weather_signal_tag_mappings",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_weather_signal_tag_mappings_type_tag",
				columnNames = {"weather_signal_type", "tag"}
		)
)
public class WeatherSignalTagMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "weather_signal_type", nullable = false, length = 20)
	private WeatherSignalType weatherSignalType;

	@Column(name = "tag", nullable = false, length = 50)
	private String tag;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_tier", nullable = false, length = 20)
	private FestiveCampaignTagMatchTier matchTier;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	/** 系統出廠內建的 9 筆預設對照（見 V19 migration），is_system_default=1 者不可刪除，僅可停用。 */
	@Column(name = "is_system_default", nullable = false)
	private Boolean isSystemDefault = false;

	@Column(name = "created_by")
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "updated_by")
	private Long updatedBy;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public WeatherSignalType getWeatherSignalType() {
		return weatherSignalType;
	}

	public void setWeatherSignalType(WeatherSignalType weatherSignalType) {
		this.weatherSignalType = weatherSignalType;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public FestiveCampaignTagMatchTier getMatchTier() {
		return matchTier;
	}

	public void setMatchTier(FestiveCampaignTagMatchTier matchTier) {
		this.matchTier = matchTier;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Boolean getIsSystemDefault() {
		return isSystemDefault;
	}

	public void setIsSystemDefault(Boolean isSystemDefault) {
		this.isSystemDefault = isSystemDefault;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Long createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(Long updatedBy) {
		this.updatedBy = updatedBy;
	}
}
