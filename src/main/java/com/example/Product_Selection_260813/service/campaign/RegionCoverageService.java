package com.example.Product_Selection_260813.service.campaign;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.entity.RegionWeight;
import com.example.Product_Selection_260813.repository.RegionWeightRepository;

/**
 * 節慶／季節檔期的地域覆蓋率（決議 D9）：選取區域的業務占比加總 ÷ 100，上限 1；未選＝全國 1.0。
 *
 * 節慶／季節型在「計分當下」即時計算並寫進快照（已審核商品的重現性由快照保證）；
 * 天氣型維持 V20 行為，讀同步當下凍結的 region_coverage_ratio，不經過這裡。
 * WeatherCampaignSyncService 自己的覆蓋率計算本次不改（行為相同，改動無收益）。
 */
@Service
public class RegionCoverageService {

	private static final Logger log = LoggerFactory.getLogger(RegionCoverageService.class);

	private final RegionWeightRepository regionWeightRepository;

	public RegionCoverageService(RegionWeightRepository regionWeightRepository) {
		this.regionWeightRepository = regionWeightRepository;
	}

	/** 目前的四區占比；批次計分時先取一次，再對每筆檔期呼叫 {@link #coverageOf(Collection, Map)}。 */
	public Map<String, BigDecimal> currentWeights() {
		return regionWeightRepository.findAll().stream()
				.collect(Collectors.toMap(RegionWeight::getRegion, RegionWeight::getWeightPercentage));
	}

	public BigDecimal coverageOf(Collection<String> regions) {
		return coverageOf(regions, currentWeights());
	}

	public BigDecimal coverageOf(Collection<String> regions, Map<String, BigDecimal> weights) {
		return RegionCoverageCalculator.coverageOf(regions, weights,
				missing -> log.warn("region_weights 缺少區域 {} 的占比資料，覆蓋率以 0 計算", missing));
	}
}
