package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.GoogleTrendRun;
import com.example.Product_Selection_260813.enums.TrendSyncRunStatus;

public interface GoogleTrendRunRepository extends JpaRepository<GoogleTrendRun, Long> {

	List<GoogleTrendRun> findTop10ByOrderByStartedAtDesc();

	List<GoogleTrendRun> findByStatus(TrendSyncRunStatus status);
}
