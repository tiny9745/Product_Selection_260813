package com.example.Product_Selection_260813.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.DiscoveryProcessedTitle;

public interface DiscoveryProcessedTitleRepository extends JpaRepository<DiscoveryProcessedTitle, String> {

	/** 這批標題裡哪些已經處理過。呼叫端請分批傳入，避免 IN 清單過長。 */
	List<DiscoveryProcessedTitle> findByTitleKeyIn(Collection<String> titleKeys);

	/** 清掉太舊的紀錄（保留期必須大於 discovery.recent-days，否則視窗內的標題會被重送）。 */
	@Modifying
	@Transactional
	@Query("delete from DiscoveryProcessedTitle t where t.processedAt < :before")
	int deleteProcessedBefore(@Param("before") LocalDateTime before);
}
