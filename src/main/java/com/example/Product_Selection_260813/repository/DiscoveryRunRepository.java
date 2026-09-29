package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.DiscoveryRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;

public interface DiscoveryRunRepository extends JpaRepository<DiscoveryRun, Long> {

	List<DiscoveryRun> findTop10ByOrderByStartedAtDesc();

	/** 2026-09-29：排程作業面板的執行紀錄分頁（新到舊），見 RunHistoryPaging。 */
	Page<DiscoveryRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

	List<DiscoveryRun> findByStatus(TrendSyncRunStatus status);
}
