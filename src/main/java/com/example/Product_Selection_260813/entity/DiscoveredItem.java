package com.example.Product_Selection_260813.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.enums.GateStatus;
import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** PTT 新品探索發現的一個商品線索，見 V33 migration 說明。 */
@Entity
@Table(name = "discovered_items")
public class DiscoveredItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "normalized_name", nullable = false, length = 120)
	private String normalizedName;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Column(name = "search_keyword", nullable = false, length = 100)
	private String searchKeyword;

	@Column(name = "category_hint", length = 120)
	private String categoryHint;

	@Column(name = "product_type_id")
	private Long productTypeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private DiscoveredItemStatus status = DiscoveredItemStatus.NEW;

	@Column(name = "mention_count", nullable = false)
	private Integer mentionCount = 0;

	@Column(name = "push_volume", nullable = false)
	private Integer pushVolume = 0;

	@Column(name = "popularity_score", precision = 5, scale = 2)
	private BigDecimal popularityScore;

	@Column(name = "trend_score", precision = 5, scale = 2)
	private BigDecimal trendScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "trend_direction")
	private TrendSignalTrendDirection trendDirection;

	@Column(name = "window_volume")
	private Integer windowVolume;

	@Column(name = "buzz_checked_at")
	private LocalDateTime buzzCheckedAt;

	// ---------------- V34：適配評分 ----------------

	@Column(name = "fit_score", precision = 5, scale = 2)
	private BigDecimal fitScore;

	@Column(name = "fit_reason", length = 300)
	private String fitReason;

	@Column(name = "fit_concerns", length = 300)
	private String fitConcerns;

	@Column(name = "fit_audience_sig", length = 100)
	private String fitAudienceSig;

	@Column(name = "fit_model", length = 60)
	private String fitModel;

	@Column(name = "fit_evaluated_at")
	private LocalDateTime fitEvaluatedAt;

	@Column(name = "temperature_zone", length = 20)
	private String temperatureZone;

	@Enumerated(EnumType.STRING)
	@Column(name = "temperature_gate")
	private GateStatus temperatureGate;

	// ---------------- V34：Google 趨勢交叉驗證 ----------------

	@Enumerated(EnumType.STRING)
	@Column(name = "google_status")
	private GoogleTrendStatus googleStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "google_direction")
	private TrendSignalTrendDirection googleDirection;

	@Column(name = "google_growth_rate", precision = 8, scale = 2)
	private BigDecimal googleGrowthRate;

	@Column(name = "google_checked_at")
	private LocalDateTime googleCheckedAt;

	@Column(name = "first_seen_at", nullable = false)
	private LocalDateTime firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private LocalDateTime lastSeenAt;

	@Column(name = "model_name", length = 60)
	private String modelName;

	@Column(name = "converted_product_id")
	private Long convertedProductId;

	/** V34：略過原因代碼；V34 之前的略過紀錄為 null。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "dismiss_reason_code")
	private DiscoveryDismissReason dismissReasonCode;

	@Column(name = "dismiss_reason", length = 255)
	private String dismissReason;

	@Column(name = "handled_by")
	private Long handledBy;

	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getNormalizedName() { return normalizedName; }
	public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }
	public String getSearchKeyword() { return searchKeyword; }
	public void setSearchKeyword(String searchKeyword) { this.searchKeyword = searchKeyword; }
	public String getCategoryHint() { return categoryHint; }
	public void setCategoryHint(String categoryHint) { this.categoryHint = categoryHint; }
	public Long getProductTypeId() { return productTypeId; }
	public void setProductTypeId(Long productTypeId) { this.productTypeId = productTypeId; }
	public DiscoveredItemStatus getStatus() { return status; }
	public void setStatus(DiscoveredItemStatus status) { this.status = status; }
	public Integer getMentionCount() { return mentionCount; }
	public void setMentionCount(Integer mentionCount) { this.mentionCount = mentionCount; }
	public Integer getPushVolume() { return pushVolume; }
	public void setPushVolume(Integer pushVolume) { this.pushVolume = pushVolume; }
	public BigDecimal getPopularityScore() { return popularityScore; }
	public void setPopularityScore(BigDecimal popularityScore) { this.popularityScore = popularityScore; }
	public BigDecimal getTrendScore() { return trendScore; }
	public void setTrendScore(BigDecimal trendScore) { this.trendScore = trendScore; }
	public TrendSignalTrendDirection getTrendDirection() { return trendDirection; }
	public void setTrendDirection(TrendSignalTrendDirection trendDirection) { this.trendDirection = trendDirection; }
	public Integer getWindowVolume() { return windowVolume; }
	public void setWindowVolume(Integer windowVolume) { this.windowVolume = windowVolume; }
	public LocalDateTime getBuzzCheckedAt() { return buzzCheckedAt; }
	public void setBuzzCheckedAt(LocalDateTime buzzCheckedAt) { this.buzzCheckedAt = buzzCheckedAt; }
	public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
	public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
	public LocalDateTime getLastSeenAt() { return lastSeenAt; }
	public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
	public String getModelName() { return modelName; }
	public void setModelName(String modelName) { this.modelName = modelName; }
	public Long getConvertedProductId() { return convertedProductId; }
	public void setConvertedProductId(Long convertedProductId) { this.convertedProductId = convertedProductId; }
	public String getDismissReason() { return dismissReason; }
	public void setDismissReason(String dismissReason) { this.dismissReason = dismissReason; }
	public Long getHandledBy() { return handledBy; }
	public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
	public LocalDateTime getHandledAt() { return handledAt; }
	public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
	public BigDecimal getFitScore() { return fitScore; }
	public void setFitScore(BigDecimal fitScore) { this.fitScore = fitScore; }
	public String getFitReason() { return fitReason; }
	public void setFitReason(String fitReason) { this.fitReason = fitReason; }
	public String getFitConcerns() { return fitConcerns; }
	public void setFitConcerns(String fitConcerns) { this.fitConcerns = fitConcerns; }
	public String getFitAudienceSig() { return fitAudienceSig; }
	public void setFitAudienceSig(String fitAudienceSig) { this.fitAudienceSig = fitAudienceSig; }
	public String getFitModel() { return fitModel; }
	public void setFitModel(String fitModel) { this.fitModel = fitModel; }
	public LocalDateTime getFitEvaluatedAt() { return fitEvaluatedAt; }
	public void setFitEvaluatedAt(LocalDateTime fitEvaluatedAt) { this.fitEvaluatedAt = fitEvaluatedAt; }
	public String getTemperatureZone() { return temperatureZone; }
	public void setTemperatureZone(String temperatureZone) { this.temperatureZone = temperatureZone; }
	public GateStatus getTemperatureGate() { return temperatureGate; }
	public void setTemperatureGate(GateStatus temperatureGate) { this.temperatureGate = temperatureGate; }
	public GoogleTrendStatus getGoogleStatus() { return googleStatus; }
	public void setGoogleStatus(GoogleTrendStatus googleStatus) { this.googleStatus = googleStatus; }
	public TrendSignalTrendDirection getGoogleDirection() { return googleDirection; }
	public void setGoogleDirection(TrendSignalTrendDirection googleDirection) { this.googleDirection = googleDirection; }
	public BigDecimal getGoogleGrowthRate() { return googleGrowthRate; }
	public void setGoogleGrowthRate(BigDecimal googleGrowthRate) { this.googleGrowthRate = googleGrowthRate; }
	public LocalDateTime getGoogleCheckedAt() { return googleCheckedAt; }
	public void setGoogleCheckedAt(LocalDateTime googleCheckedAt) { this.googleCheckedAt = googleCheckedAt; }
	public DiscoveryDismissReason getDismissReasonCode() { return dismissReasonCode; }
	public void setDismissReasonCode(DiscoveryDismissReason dismissReasonCode) { this.dismissReasonCode = dismissReasonCode; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
