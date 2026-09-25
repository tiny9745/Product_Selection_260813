package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.TrendSyncRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;

public interface TrendSyncRunRepository extends JpaRepository<TrendSyncRun, Long> {

	// 系統設定「爬蟲排程控制」區塊：最近幾次執行紀錄（新到舊）
	List<TrendSyncRun> findTop10ByOrderByStartedAtDesc();

	// 啟動時清理：上次應用程式在同步途中被關掉，留下停在 RUNNING 的紀錄
	List<TrendSyncRun> findByStatus(TrendSyncRunStatus status);
}
