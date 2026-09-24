package com.example.Product_Selection_260813.entity;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** festive_campaign_occurrence_overrides 的複合主鍵（campaign_id, cycle_year）。 */
@Embeddable
public class FestiveCampaignOccurrenceOverrideId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "campaign_id", nullable = false)
	private Long campaignId;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "cycle_year", nullable = false)
	private Integer cycleYear;

	public FestiveCampaignOccurrenceOverrideId() {
	}

	public FestiveCampaignOccurrenceOverrideId(Long campaignId, Integer cycleYear) {
		this.campaignId = campaignId;
		this.cycleYear = cycleYear;
	}

	public Long getCampaignId() {
		return campaignId;
	}

	public void setCampaignId(Long campaignId) {
		this.campaignId = campaignId;
	}

	public Integer getCycleYear() {
		return cycleYear;
	}

	public void setCycleYear(Integer cycleYear) {
		this.cycleYear = cycleYear;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof FestiveCampaignOccurrenceOverrideId)) {
			return false;
		}
		FestiveCampaignOccurrenceOverrideId that = (FestiveCampaignOccurrenceOverrideId) o;
		return Objects.equals(campaignId, that.campaignId) && Objects.equals(cycleYear, that.cycleYear);
	}

	@Override
	public int hashCode() {
		return Objects.hash(campaignId, cycleYear);
	}
}
