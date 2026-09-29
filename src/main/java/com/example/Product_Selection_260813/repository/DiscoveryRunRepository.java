package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.DiscoveryRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;

public interface DiscoveryRunRepository extends JpaRepository<DiscoveryRun, Long> {

	List<DiscoveryRun> findTop10ByOrderByStartedAtDesc();

	List<DiscoveryRun> findByStatus(TrendSyncRunStatus status);
}
