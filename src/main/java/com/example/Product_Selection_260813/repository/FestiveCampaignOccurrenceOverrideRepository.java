package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.FestiveCampaignOccurrenceOverride;
import com.example.Product_Selection_260813.entity.FestiveCampaignOccurrenceOverrideId;

public interface FestiveCampaignOccurrenceOverrideRepository
		extends JpaRepository<FestiveCampaignOccurrenceOverride, FestiveCampaignOccurrenceOverrideId> {

	@Query("select o from FestiveCampaignOccurrenceOverride o where o.id.campaignId = :campaignId order by o.id.cycleYear")
	List<FestiveCampaignOccurrenceOverride> findByCampaignId(@Param("campaignId") Long campaignId);

	/** 計分與檔期列表的批次查詢，避免 N+1。 */
	@Query("select o from FestiveCampaignOccurrenceOverride o where o.id.campaignId in :campaignIds")
	List<FestiveCampaignOccurrenceOverride> findByCampaignIdIn(@Param("campaignIds") Collection<Long> campaignIds);

	/** 理由同 FestiveCampaignRegionRepository.deleteByCampaignId()。目前只供未來整份覆蓋使用。 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FestiveCampaignOccurrenceOverride o where o.id.campaignId = :campaignId")
	void deleteByCampaignId(@Param("campaignId") Long campaignId);
}
