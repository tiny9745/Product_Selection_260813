package com.example.Product_Selection_260813.service.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.dto.request.WeatherBoostSettingsUpdateRequest;
import com.example.Product_Selection_260813.dto.response.WeatherBoostSettingsResponse;
import com.example.Product_Selection_260813.entity.DailyWeatherRecord;
import com.example.Product_Selection_260813.entity.RegionWeight;
import com.example.Product_Selection_260813.entity.WeatherBoostSetting;
import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.enums.WeatherSignalType;
import com.example.Product_Selection_260813.json.WeatherBoostSnapshot;
import com.example.Product_Selection_260813.repository.DailyWeatherRecordRepository;
import com.example.Product_Selection_260813.repository.RegionWeightRepository;
import com.example.Product_Selection_260813.repository.WeatherBoostSettingRepository;
import com.example.Product_Selection_260813.repository.WeatherSignalTagMappingRepository;
import com.example.Product_Selection_260813.service.campaign.FestiveCampaignsChangedEvent;

/**
 * 天氣加成（V26，2026-09-25 決議：天氣不再是檔期，改為依天氣數據直接計算的獨立加成）。
 *
 * <pre>
 * 逐日命中分數(區, 日) = 當天該區所有天氣訊號（WeatherNormalizer.classifyDay）
 *                         對應到商品標籤的 weather_signal_tag_mappings 中，最高的 match_tier 權重
 *                         （CORE 1.0／GENERAL 0.6／WEAK 0.3；未命中＝0；v1 不疊加多個標籤）
 * 區域窗口分(區)       = 該區窗口內「有資料的日子」逐日命中分數的平均 × 100
 * 窗口分               = 各區窗口分依 region_weights 業務占比加權平均（只計入有資料的區）
 * 歷史分 = 過去 historyDays 天的窗口分；預測分 = 今天起 forecastDays 天的窗口分
 * 天氣分 = 歷史分 × 歷史比重% + 預測分 × 預測比重%（只有一邊有資料時直接用那一邊）
 * 天氣加成 = 天氣分 ÷ 100 × 加成上限
 * </pre>
 *
 * 設計取捨：
 * <ul>
 * <li>「比例加權平均」依決議先用單純平均（每天等權），未做近日加權；日後要改只需替換
 * windowScore() 的平均方式，不影響其他部分。</li>
 * <li>沒有資料的日子不算進分母：資料缺漏不等於「天氣普通」，把它當 0 會系統性壓低加成。</li>
 * <li>與節慶加成的關係：兩者並列相加，天氣加成不經過檔期急迫係數，也不取兩者最大值。</li>
 * </ul>
 *
 * 效能：{@link #loadContext(LocalDate)} 一次載入窗口內的資料、對照表與設定並完成逐日分類，
 * 之後對每件商品呼叫 {@link #evaluate} 只做記憶體計算——批次重算（ScoringService.refreshFestivalBoosts）
 * 對整批商品只查一次資料庫。
 */
@Service
public class WeatherBoostService {

	/** 比重加總必須等於 100（與 region_weights 相同的驗證模式）。 */
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
	/** 加成上限範圍：0~10 分（節慶加成上限為 5 分，天氣不宜遠大於節慶）。 */
	private static final BigDecimal MAX_BOOST_CAP = new BigDecimal("10");

	private final DailyWeatherRecordRepository dailyWeatherRecordRepository;
	private final WeatherSignalTagMappingRepository tagMappingRepository;
	private final RegionWeightRepository regionWeightRepository;
	private final WeatherBoostSettingRepository settingRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${weather.history-days:30}")
	private int historyDays = 30;

	@Value("${weather.forecast-days:14}")
	private int forecastDays = 14;

	public WeatherBoostService(DailyWeatherRecordRepository dailyWeatherRecordRepository,
			WeatherSignalTagMappingRepository tagMappingRepository, RegionWeightRepository regionWeightRepository,
			WeatherBoostSettingRepository settingRepository, ApplicationEventPublisher eventPublisher) {
		this.dailyWeatherRecordRepository = dailyWeatherRecordRepository;
		this.tagMappingRepository = tagMappingRepository;
		this.regionWeightRepository = regionWeightRepository;
		this.settingRepository = settingRepository;
		this.eventPublisher = eventPublisher;
	}

	// ==================================================================
	// 計算
	// ==================================================================

	/**
	 * 一次計算所需的全部資料（已完成逐日分類），供同一批商品共用。
	 *
	 * @param dailyTypes 區 → 日期 → 當天的天氣訊號（有資料但沒有任何訊號＝空集合）
	 */
	public record Context(LocalDate today, LocalDate historyFrom, LocalDate forecastTo,
			Map<String, Map<LocalDate, Set<WeatherSignalType>>> dailyTypes,
			Map<WeatherSignalType, Map<String, BigDecimal>> tagWeightsByType, Map<String, BigDecimal> regionWeights,
			BigDecimal historyWeightPercentage, BigDecimal forecastWeightPercentage, BigDecimal boostCap,
			LocalDateTime dataUpdatedAt) {
	}

	@Transactional(readOnly = true)
	public Context loadContext(LocalDate today) {
		LocalDate historyFrom = today.minusDays(historyDays);
		LocalDate forecastTo = today.plusDays(forecastDays - 1L);

		Map<String, Map<LocalDate, Set<WeatherSignalType>>> dailyTypes = new HashMap<>();
		LocalDateTime dataUpdatedAt = null;
		for (DailyWeatherRecord record : dailyWeatherRecordRepository.findByWeatherDateBetween(historyFrom, forecastTo)) {
			boolean observed = record.getWeatherDate().isBefore(today);
			dailyTypes.computeIfAbsent(record.getRegion(), region -> new HashMap<>())
					.put(record.getWeatherDate(), WeatherNormalizer.classifyDay(toMetrics(record), observed));
			if (dataUpdatedAt == null || record.getFetchedAt().isAfter(dataUpdatedAt)) {
				dataUpdatedAt = record.getFetchedAt();
			}
		}

		Map<WeatherSignalType, Map<String, BigDecimal>> tagWeightsByType = new HashMap<>();
		for (WeatherSignalTagMapping mapping : tagMappingRepository.findByIsActiveTrue()) {
			tagWeightsByType.computeIfAbsent(mapping.getWeatherSignalType(), type -> new HashMap<>())
					.merge(mapping.getTag(), mapping.getMatchTier().getMatchWeight(), BigDecimal::max);
		}

		Map<String, BigDecimal> regionWeights = regionWeightRepository.findAll().stream()
				.collect(Collectors.toMap(RegionWeight::getRegion, RegionWeight::getWeightPercentage));

		WeatherBoostSetting setting = currentSetting();
		return new Context(today, historyFrom, forecastTo, dailyTypes, tagWeightsByType, regionWeights,
				setting.getHistoryWeightPercentage(), setting.getForecastWeightPercentage(), setting.getBoostCap(),
				dataUpdatedAt);
	}

	/** 單件商品：以今天（台灣時間）即時載入資料後計算。批次請先 loadContext() 再逐件 evaluate()。 */
	@Transactional(readOnly = true)
	public WeatherBoostSnapshot evaluateNow(Collection<String> productTags) {
		return evaluate(loadContext(LocalDate.now(BusinessTimeZone.TAIPEI)), productTags);
	}

	/** 純記憶體計算（不查資料庫），規則見類別說明。一律回傳明細物件；沒有資料或未命中時加成為 0。 */
	public static WeatherBoostSnapshot evaluate(Context context, Collection<String> productTags) {
		Set<String> tags = productTags == null ? Set.of() : new LinkedHashSet<>(productTags);
		Set<String> matchedTags = new TreeSet<>();

		WindowResult history = windowScore(context, tags, context.historyFrom(), context.today().minusDays(1),
				matchedTags);
		WindowResult forecast = windowScore(context, tags, context.today(), context.forecastTo(), matchedTags);

		BigDecimal combined = combine(history.score(), forecast.score(), context.historyWeightPercentage(),
				context.forecastWeightPercentage());
		BigDecimal boost = combined == null ? BigDecimal.ZERO.setScale(2)
				: combined.multiply(context.boostCap()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

		WeatherBoostSnapshot snapshot = new WeatherBoostSnapshot();
		snapshot.setHistoryScore(history.score());
		snapshot.setForecastScore(forecast.score());
		snapshot.setCombinedScore(combined);
		snapshot.setHistoryWeightPercentage(context.historyWeightPercentage());
		snapshot.setForecastWeightPercentage(context.forecastWeightPercentage());
		snapshot.setBoostCap(context.boostCap());
		snapshot.setWeatherBoost(boost);
		snapshot.setMatchedTags(new ArrayList<>(matchedTags));
		snapshot.setHistoryDays(history.days());
		snapshot.setForecastDays(forecast.days());
		snapshot.setHistoryFrom(context.historyFrom().toString());
		snapshot.setForecastTo(context.forecastTo().toString());
		snapshot.setDataUpdatedAt(context.dataUpdatedAt() == null ? null : context.dataUpdatedAt().toString());
		return snapshot;
	}

	/** @param days 窗口內至少一區有資料的天數；score 為 null 代表整個窗口沒有任何資料 */
	private record WindowResult(BigDecimal score, int days) {
	}

	private static WindowResult windowScore(Context context, Set<String> tags, LocalDate from, LocalDate to,
			Set<String> matchedTags) {
		BigDecimal weightedSum = BigDecimal.ZERO;
		BigDecimal weightTotal = BigDecimal.ZERO;
		Set<LocalDate> daysWithData = new TreeSet<>();

		for (Map.Entry<String, Map<LocalDate, Set<WeatherSignalType>>> regionEntry : context.dailyTypes().entrySet()) {
			BigDecimal daySum = BigDecimal.ZERO;
			int dayCount = 0;
			for (Map.Entry<LocalDate, Set<WeatherSignalType>> day : regionEntry.getValue().entrySet()) {
				if (day.getKey().isBefore(from) || day.getKey().isAfter(to)) {
					continue;
				}
				daySum = daySum.add(dayHitScore(day.getValue(), tags, context.tagWeightsByType(), matchedTags));
				dayCount++;
				daysWithData.add(day.getKey());
			}
			if (dayCount == 0) {
				continue; // 這一區在窗口內完全沒有資料：不計入加權，而不是當成 0 分
			}
			BigDecimal regionScore = daySum.multiply(ONE_HUNDRED).divide(BigDecimal.valueOf(dayCount), 4,
					RoundingMode.HALF_UP);
			BigDecimal weight = context.regionWeights().getOrDefault(regionEntry.getKey(), BigDecimal.ZERO);
			weightedSum = weightedSum.add(regionScore.multiply(weight));
			weightTotal = weightTotal.add(weight);
		}

		if (daysWithData.isEmpty()) {
			return new WindowResult(null, 0);
		}
		if (weightTotal.signum() == 0) {
			// 有資料的區域占比全為 0（設定異常）：視為沒有可用的加權依據，保守不給加成。
			return new WindowResult(BigDecimal.ZERO.setScale(2), daysWithData.size());
		}
		return new WindowResult(weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP), daysWithData.size());
	}

	/** 逐日命中分數：當天所有訊號對應到商品標籤的最高權重（v1 不疊加，規格 1.3 延後）。 */
	private static BigDecimal dayHitScore(Set<WeatherSignalType> types, Set<String> tags,
			Map<WeatherSignalType, Map<String, BigDecimal>> tagWeightsByType, Set<String> matchedTags) {
		BigDecimal best = BigDecimal.ZERO;
		for (WeatherSignalType type : types) {
			for (Map.Entry<String, BigDecimal> mapping : tagWeightsByType.getOrDefault(type, Map.of()).entrySet()) {
				if (!tags.contains(mapping.getKey())) {
					continue;
				}
				matchedTags.add(mapping.getKey());
				best = best.max(mapping.getValue());
			}
		}
		return best;
	}

	static BigDecimal combine(BigDecimal historyScore, BigDecimal forecastScore, BigDecimal historyWeight,
			BigDecimal forecastWeight) {
		if (historyScore == null && forecastScore == null) {
			return null;
		}
		if (historyScore == null) {
			return forecastScore;
		}
		if (forecastScore == null) {
			return historyScore;
		}
		return historyScore.multiply(historyWeight).add(forecastScore.multiply(forecastWeight))
				.divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
	}

	private static DailyWeatherMetrics toMetrics(DailyWeatherRecord record) {
		return new DailyWeatherMetrics(record.getWeatherDate(), toDouble(record.getApparentTemperatureMax()),
				toDouble(record.getApparentTemperatureMin()), toDouble(record.getHumidityMean()),
				toDouble(record.getPrecipitationSum()), toDouble(record.getPrecipitationProbabilityMax()),
				toDouble(record.getWindSpeedMax()));
	}

	private static Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

	// ==================================================================
	// 設定（GET/PUT /api/settings/weather/boost-settings）
	// ==================================================================

	@Transactional(readOnly = true)
	public WeatherBoostSettingsResponse getSettings() {
		return toResponse(currentSetting());
	}

	/**
	 * 更新比重與上限。比重加總必須為 100、上限 0~10。變更後發布加成重算事件，
	 * 讓尚未核准商品的天氣加成立即套用新設定（已核准商品讀審核快照，不受影響）。
	 */
	@Transactional
	public WeatherBoostSettingsResponse updateSettings(WeatherBoostSettingsUpdateRequest request, Long operatorId) {
		BigDecimal history = request.getHistoryWeightPercentage();
		BigDecimal forecast = request.getForecastWeightPercentage();
		BigDecimal cap = request.getBoostCap();
		if (history == null || forecast == null || cap == null) {
			throw new IllegalArgumentException("歷史比重、預測比重與加成上限皆為必填");
		}
		if (history.signum() < 0 || forecast.signum() < 0 || history.add(forecast).compareTo(ONE_HUNDRED) != 0) {
			throw new IllegalArgumentException("歷史比重與預測比重皆不可為負數，且加總必須為 100");
		}
		if (cap.signum() < 0 || cap.compareTo(MAX_BOOST_CAP) > 0) {
			throw new IllegalArgumentException("天氣加成上限必須介於 0 到 10 分");
		}
		WeatherBoostSetting setting = currentSetting();
		setting.setHistoryWeightPercentage(history.setScale(2, RoundingMode.HALF_UP));
		setting.setForecastWeightPercentage(forecast.setScale(2, RoundingMode.HALF_UP));
		setting.setBoostCap(cap.setScale(2, RoundingMode.HALF_UP));
		setting.setUpdatedBy(operatorId);
		WeatherBoostSetting saved = settingRepository.saveAndFlush(setting);
		eventPublisher.publishEvent(new FestiveCampaignsChangedEvent("天氣加成設定變更"));
		return toResponse(saved);
	}

	private WeatherBoostSetting currentSetting() {
		return settingRepository.findById(WeatherBoostSetting.SINGLETON_ID)
				.orElseThrow(() -> new IllegalStateException("缺少天氣加成設定（weather_boost_settings id=1），請確認 V26 migration"));
	}

	private WeatherBoostSettingsResponse toResponse(WeatherBoostSetting setting) {
		return new WeatherBoostSettingsResponse(setting.getHistoryWeightPercentage(),
				setting.getForecastWeightPercentage(), setting.getBoostCap(), historyDays, forecastDays,
				setting.getUpdatedAt());
	}
}
