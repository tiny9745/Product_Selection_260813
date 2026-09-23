package com.example.Product_Selection_260813.service.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.response.WeatherSyncResponse;
import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.RegionWeight;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.WeatherSignalType;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.RegionWeightRepository;
import com.example.Product_Selection_260813.entity.WeatherSignalTagMapping;
import com.example.Product_Selection_260813.repository.WeatherSignalTagMappingRepository;

/**
 * 把 WeatherSignalProvider 提供的天氣訊號，upsert 成 category=WEATHER 的
 * festive_campaigns（＋festive_campaign_tags），讓天氣完全沿用既有的
 * Festival Boost 計分主幹（ScoringService.buildMatchedCampaignSnapshot()／
 * finalScore 加總），不需要另外寫一套天氣專屬的評分邏輯。
 *
 * 這支服務扮演的角色，等同節慶/季節原本由「主管在設定頁手動建檔期」做的事，
 * 只是天氣要每天自動做。
 *
 * ⚠️ 沿用既有慣例：目前程式庫裡 festive_campaigns 的 campaign_status
 * 自動轉換（UPCOMING→PREPARING→ACTIVE→EXPIRED）雖然在 DB 欄位註解與
 * FestiveCampaignRepository.findByIsManualOverrideFalse() 都看得到設計
 * 意圖（「Daily Cron」），但實際上目前沒有任何呼叫端——節慶/季節檔期
 * 現在全靠 SettingsService.switchManualStatus() 手動切換。這支服務只負責
 * WEATHER 類別自己的狀態轉換，不會、也不應該去處理 FESTIVAL／SEASON
 * 檔期缺的那段自動化——那是獨立的技術債，不在這次天氣功能的範圍內。
 */
@Service
public class WeatherCampaignSyncService {

	/**
	 * 天氣訊號類型 → 商品標籤（＋命中權重）對照，<b>2026-09-22 改為資料庫驅動
	 * （weather_signal_tag_mappings，見 V19 migration）</b>，不再是寫死的
	 * 靜態常數——節慶/季節檔期的標籤主管本來就能在設定頁自行調整
	 * （festive_campaign_tags），天氣這端原本只能改程式碼重新部署才能調整，
	 * 是不一致的落差，這次補齊。
	 *
	 * 每次同步只查一次全部生效中的對照、在記憶體依 weatherSignalType 分組
	 * （見 syncWeatherCampaigns()），不對每個訊號類型各自查一次資料庫。
	 * NORMAL 不會出現在查詢結果裡：SettingsService 建立/編輯對照時就擋下
	 * NORMAL，資料庫裡本來就不會有這個類型的列，不需要在這裡再過濾一次。
	 */
	@Autowired
	private WeatherSignalTagMappingRepository weatherSignalTagMappingRepository;

	/**
	 * 地域性影響評分（方案B+D，2026-09-23新增）：四區各自的業務占比設定，
	 * 用來把「這次天氣訊號命中了幾個、哪幾個區域」換算成 region_coverage_ratio
	 * （見 buildRegionCoverageRatios() 與 FestiveCampaign.regionCoverageRatio
	 * 欄位註解）。每次同步只查一次，不對每個訊號類型各自查一次資料庫。
	 */
	@Autowired
	private RegionWeightRepository regionWeightRepository;

	private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Autowired
	private WeatherSignalProvider weatherSignalProvider;

	@Autowired
	private FestiveCampaignRepository festiveCampaignRepository;

	@Autowired
	private FestiveCampaignTagRepository festiveCampaignTagRepository;

	/**
	 * 每天清晨執行，在天氣資料來源當天更新之後（實際時間依
	 * WeatherSignalProvider 的資料來源更新頻率調整，這裡先抓一個
	 * 保守的時間點）。
	 *
	 * 回傳值刻意從void改成WeatherSyncResponse：{@code @Scheduled}的呼叫端
	 * （Spring排程執行緒）本來就不理會回傳值，改成非void不影響每天05:00
	 * 的自動排程行為；這裡改的目的是讓WeatherController的手動觸發端點
	 * （POST /api/settings/weather/sync）能把這次同步做了什麼回報給呼叫端，
	 * 不用另外再查一次festive_campaigns才知道結果。
	 */
	@Scheduled(cron = "0 0 5 * * *")
	@Transactional
	public WeatherSyncResponse syncWeatherCampaigns() {
		List<WeatherSignal> signals = weatherSignalProvider.getActiveSignals();

		Map<WeatherSignalType, Map<String, FestiveCampaignTagMatchTier>> tagMappingsByType =
				weatherSignalTagMappingRepository.findByIsActiveTrue().stream()
						.collect(Collectors.groupingBy(WeatherSignalTagMapping::getWeatherSignalType,
								Collectors.toMap(WeatherSignalTagMapping::getTag, WeatherSignalTagMapping::getMatchTier)));

		Map<WeatherSignalType, BigDecimal> coverageRatioByType = buildRegionCoverageRatios(signals);

		Set<String> syncedCodes = signals.stream()
				.filter(signal -> tagMappingsByType.containsKey(signal.getType()))
				.map(signal -> syncOne(signal, tagMappingsByType.get(signal.getType()),
						coverageRatioByType.get(signal.getType())))
				.collect(Collectors.toSet());

		int expiredCount = expireStaleWeatherCampaigns(syncedCodes);

		return new WeatherSyncResponse(signals.size(), syncedCodes.size(), expiredCount);
	}

	/**
	 * 地域性影響評分（方案D）：同一種天氣訊號類型，這次同步命中的區域越多、
	 * 且命中的區域業務占比越高，region_coverage_ratio 越接近 1；只有單一區域
	 * 命中、且該區占比低時，比重就低——不是精確的「這個商品是否屬於這個
	 * 地區」比對（那是方案C，需要 Product/AudienceProfile 補地域欄位，這次
	 * 決議不做），而是讓「全國代表性不足」的局部訊號，加成力道自然打折。
	 *
	 * 每個訊號類型的比重，在整批 signals 範圍內只算一次（依 type 分組找出
	 * 命中的 region 集合），不對每一筆訊號各自查一次 region_weights，避免
	 * N+1。四區占比理論上加總為100（SettingsService.updateRegionWeights()
	 * 保證），但仍用 min(1, ...) 夾住比例，防止資料異常時 boost 超出既有的
	 * BOOST_CAP 設計上限。
	 */
	private Map<WeatherSignalType, BigDecimal> buildRegionCoverageRatios(List<WeatherSignal> signals) {
		Map<String, BigDecimal> weightByRegion = regionWeightRepository.findAll().stream()
				.collect(Collectors.toMap(RegionWeight::getRegion, RegionWeight::getWeightPercentage));

		Map<WeatherSignalType, Set<String>> regionsByType = signals.stream()
				.collect(Collectors.groupingBy(WeatherSignal::getType,
						Collectors.mapping(WeatherSignal::getRegion, Collectors.toSet())));

		return regionsByType.entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey,
				entry -> {
					BigDecimal sum = entry.getValue().stream()
							.map(region -> weightByRegion.getOrDefault(region, BigDecimal.ZERO))
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					BigDecimal ratio = sum.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
					return ratio.min(BigDecimal.ONE);
				}));
	}

	/** upsert 單一天氣訊號對應的檔期，回傳這筆檔期的 campaign_code。 */
	private String syncOne(WeatherSignal signal, Map<String, FestiveCampaignTagMatchTier> tagMappings,
			BigDecimal regionCoverageRatio) {
		String code = buildCampaignCode(signal);

		FestiveCampaign campaign = festiveCampaignRepository.findByCampaignCode(code).orElseGet(FestiveCampaign::new);

		// 人工蓋過的天氣檔期，這次同步完全不動——理由跟既有的
		// findByIsManualOverrideFalse() 一致：主管判斷系統誤判時的復原路徑。
		if (Boolean.TRUE.equals(campaign.getIsManualOverride())) {
			return code;
		}

		campaign.setCampaignCode(code);
		campaign.setCampaignName(buildCampaignName(signal));
		campaign.setCategory(FestiveCategory.WEATHER);
		campaign.setStartDate(signal.getWindowStart());
		campaign.setEndDate(signal.getWindowEnd());
		// 天氣訊號的「備戰前置天數」＝窗口開始前，商品應該提前準備的天數，
		// 沿用既有欄位語意，這裡先用固定值，之後可視商品 Timing Profile
		// （規劃文件第4節）個別調整，不影響這支服務的其餘邏輯。
		campaign.setPreparationLeadDays(3);
		campaign.setCampaignStatus(resolveWeatherStatus(signal));
		campaign.setWeatherConfidence(signal.getConfidence());
		campaign.setRegion(signal.getRegion());
		// regionCoverageRatio 理論上一定有值（buildRegionCoverageRatios()對
		// 這次signals裡出現的每個type都會算一筆），null時保守給0——不讓資料
		// 異常的天氣檔期意外拿到滿額地域加成，比照weatherConfidence為null時
		// 保守處理成LOW的既有原則（見ScoringService.calculateWeatherUrgencyFactor()）。
		campaign.setRegionCoverageRatio(regionCoverageRatio != null ? regionCoverageRatio : BigDecimal.ZERO);
		if (campaign.getIsManualOverride() == null) {
			campaign.setIsManualOverride(false);
		}

		FestiveCampaign saved = festiveCampaignRepository.save(campaign);

		festiveCampaignTagRepository.deleteByCampaignId(saved.getId());
		saveTags(saved.getId(), tagMappings);

		return code;
	}

	private FestiveCampaignStatus resolveWeatherStatus(WeatherSignal signal) {
		LocalDate today = LocalDate.now();
		boolean withinWindow = !today.isBefore(signal.getWindowStart()) && !today.isAfter(signal.getWindowEnd());
		return withinWindow ? FestiveCampaignStatus.ACTIVE : FestiveCampaignStatus.PREPARING;
	}

	private void saveTags(Long campaignId, Map<String, FestiveCampaignTagMatchTier> tags) {
		for (Map.Entry<String, FestiveCampaignTagMatchTier> entry : tags.entrySet()) {
			FestiveCampaignTag tag = new FestiveCampaignTag();
			tag.setCampaignId(campaignId);
			tag.setTag(entry.getKey());
			tag.setMatchTier(entry.getValue());
			festiveCampaignTagRepository.save(tag);
		}
	}

	/**
	 * 這次同步沒有再被回報的既有天氣檔期（例如原本預報有雨、最新一次
	 * 預報改成放晴），代表訊號已經不成立，設回 EXPIRED，不留在
	 * PREPARING/ACTIVE 繼續影響 Festival Boost 計算。
	 */
	private int expireStaleWeatherCampaigns(Set<String> syncedCodes) {
		List<FestiveCampaign> activeWeatherCampaigns = festiveCampaignRepository
				.findByCategoryAndCampaignStatusInAndIsManualOverrideFalse(
						FestiveCategory.WEATHER,
						List.of(FestiveCampaignStatus.PREPARING, FestiveCampaignStatus.ACTIVE));

		int expiredCount = 0;
		for (FestiveCampaign campaign : activeWeatherCampaigns) {
			if (!syncedCodes.contains(campaign.getCampaignCode())) {
				campaign.setCampaignStatus(FestiveCampaignStatus.EXPIRED);
				festiveCampaignRepository.save(campaign);
				expiredCount++;
			}
		}
		return expiredCount;
	}

	private String buildCampaignCode(WeatherSignal signal) {
		// 區域+訊號類型+窗口起始日 作為冪等 key：同一個區域同一種天氣類型，
		// 只要窗口起始日相同就視為同一筆檔期，重複同步時是更新而不是新增。
		return "WEATHER_%s_%s_%s".formatted(
				signal.getType().name(),
				signal.getRegion(),
				signal.getWindowStart().format(CODE_DATE_FORMAT));
	}

	private String buildCampaignName(WeatherSignal signal) {
		return "%s%s（系統自動）".formatted(signal.getRegion(), signal.getType().getLabel());
	}
}
