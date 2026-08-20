package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;

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
 * 檔期標籤命中權重明細表，取代原festive_campaigns.target_tags純文字欄位。
 *
 * 對應資料庫討論結論：target_tags只是逗號分隔字串，無法記錄「這個標籤屬於
 * 核心/一般/弱命中」的分級依據，Match Weight的三層計分規則因此無資料可用；
 * 拆成獨立表後，一個檔期可以對多個標籤，每個標籤各自帶一個match_tier，
 * ScoringService.buildMatchedCampaignSnapshot()才有真實資料可以依循規則計算，
 * 不需要再用「有交集就一律當核心命中」的暫定簡化值。
 *
 * 不使用@ManyToOne關聯festive_campaigns：沿用本專案既有慣例（見Product.productTypeId／
 * ReviewRecord.productId等），FK只在DB層以約束落實，Entity層維持plain Long id欄位，
 * 不引入JPA關聯物件圖，避免N+1與lazy-loading相關的額外複雜度。
 */
@Entity
@Table(
		name = "festive_campaign_tags",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_festive_campaign_tags_campaign_tag",
				columnNames = {"campaign_id", "tag"}
		)
)
public class FestiveCampaignTag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "campaign_id", nullable = false)
	private Long campaignId;

	@Column(name = "tag", nullable = false, length = 50)
	private String tag;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_tier", nullable = false)
	private FestiveCampaignTagMatchTier matchTier;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCampaignId() {
		return campaignId;
	}

	public void setCampaignId(Long campaignId) {
		this.campaignId = campaignId;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
