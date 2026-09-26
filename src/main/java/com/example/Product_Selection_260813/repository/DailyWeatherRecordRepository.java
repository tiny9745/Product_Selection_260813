package com.example.Product_Selection_260813.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.DailyWeatherRecord;

public interface DailyWeatherRecordRepository extends JpaRepository<DailyWeatherRecord, Long> {

	/** 天氣加成計算：一次取出時間窗口（過去 N 天～預報最後一天）內四區的所有列。 */
	List<DailyWeatherRecord> findByWeatherDateBetween(LocalDate from, LocalDate to);

	/** 同步 upsert：取出某區這次回應涵蓋日期範圍內的既有列，在記憶體比對後更新或新增。 */
	List<DailyWeatherRecord> findByRegionAndWeatherDateBetween(String region, LocalDate from, LocalDate to);

	/**
	 * 冷啟動判斷：某區在 [from, today) 已累積的歷史天數。
	 * 少於門檻時同步改抓完整的過去 30 天，達標後只補最近兩天（見 WeatherDataSyncService）。
	 */
	@Query("SELECT COUNT(DISTINCT d.weatherDate) FROM DailyWeatherRecord d"
			+ " WHERE d.region = :region AND d.weatherDate >= :from AND d.weatherDate < :today")
	long countHistoryDays(@Param("region") String region, @Param("from") LocalDate from,
			@Param("today") LocalDate today);

	/** 保留期限：早於指定日期的列已不在任何計算窗口內，同步時一併清除，避免資料表無限成長。 */
	@Modifying
	@Query("DELETE FROM DailyWeatherRecord d WHERE d.weatherDate < :before")
	int deleteOlderThan(@Param("before") LocalDate before);
}
