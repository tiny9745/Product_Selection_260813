package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.GoogleTrendRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;

public interface GoogleTrendRunRepository extends JpaRepository<GoogleTrendRun, Long> {

	List<GoogleTrendRun> findTop10ByOrderByStartedAtDesc();

	/** 2026-09-29：排程作業面板的執行紀錄分頁（新到舊），見 RunHistoryPaging。 */
	Page<GoogleTrendRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

	List<GoogleTrendRun> findByStatus(TrendSyncRunStatus status);
}
