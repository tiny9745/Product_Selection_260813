package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.enums.WeatherSignalType;

public interface WeatherSignalTagMappingRepository extends JpaRepository<WeatherSignalTagMapping, Long> {

	// WeatherCampaignSyncService 每次同步一次查全部啟用中的列，在記憶體依
	// weatherSignalType 分組（見該服務），不對每個訊號類型各自查一次，
	// 避免 N+1（天氣訊號類型最多同時有好幾種在跑一次同步）。
	List<WeatherSignalTagMapping> findByIsActiveTrue();

	boolean existsByWeatherSignalTypeAndTagAndIsActiveTrue(WeatherSignalType weatherSignalType, String tag);
}
