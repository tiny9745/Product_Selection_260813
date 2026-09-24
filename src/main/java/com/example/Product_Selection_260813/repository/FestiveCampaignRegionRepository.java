package com.example.Product_Selection_260813.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.FestiveCampaignRegion;
import com.example.Product_Selection_260813.entity.FestiveCampaignRegionId;

public interface FestiveCampaignRegionRepository extends JpaRepository<FestiveCampaignRegion, FestiveCampaignRegionId> {

	@Query("select r from FestiveCampaignRegion r where r.id.campaignId = :campaignId")
	List<FestiveCampaignRegion> findByCampaignId(@Param("campaignId") Long campaignId);

	/** 計分與檔期列表的批次查詢，避免每筆檔期各查一次（N+1）。 */
	@Query("select r from FestiveCampaignRegion r where r.id.campaignId in :campaignIds")
	List<FestiveCampaignRegion> findByCampaignIdIn(@Param("campaignIds") Collection<Long> campaignIds);

	/**
	 * 整份覆蓋前先刪除。flushAutomatically：先把同一交易中尚未送出的檔期異動寫入，
	 * 避免 clearAutomatically 清空 Persistence Context 時把它們丟掉（2026-09-24 檔期
	 * 儲存無效的同一個陷阱，見 FestiveCampaignTagRepository.deleteByCampaignId()）。
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FestiveCampaignRegion r where r.id.campaignId = :campaignId")
	void deleteByCampaignId(@Param("campaignId") Long campaignId);
}
