package com.example.Product_Selection_260813.service.weather;

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

import com.example.Product_Selection_260813.dto.weather.WeatherSignal;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCampaignTagMatchTier;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.enums.WeatherSignalType;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;

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
	 * 天氣訊號類型 → 商品標籤（＋命中權重）對照表。
	 *
	 * 這是「這個天氣訊號跟哪些商品標籤相關」的商業判斷，比照
	 * AiSuggestionBatchService 把門檻常數直接寫在服務類別裡的既有慣例
	 * （該類別的註解也說明了「初期用簡單規則，不需要額外設定表」的立場），
	 * 先用靜態常數，不預先蓋一張設定表。
	 *
	 * 如果之後要開放主管自行調整標籤關聯（見規劃文件的「主管可調控範疇」
	 * 討論），才需要升級成資料庫表，屆時只需要改這裡的查詢來源，
	 * 不影響下游的 upsert 邏輯。
	 */
	private static final Map<WeatherSignalType, Map<String, FestiveCampaignTagMatchTier>> WEATHER_TAG_MAPPING = Map.of(
			WeatherSignalType.RAINY, Map.of(
					"雨具", FestiveCampaignTagMatchTier.CORE,
					"防水", FestiveCampaignTagMatchTier.GENERAL),
			WeatherSignalType.HEAVY_RAIN, Map.of(
					"雨具", FestiveCampaignTagMatchTier.CORE,
					"防水", FestiveCampaignTagMatchTier.CORE),
			WeatherSignalType.HOT, Map.of(
					"涼感", FestiveCampaignTagMatchTier.CORE,
					"消暑", FestiveCampaignTagMatchTier.GENERAL),
			WeatherSignalType.HUMID_HOT, Map.of(
					"涼感", FestiveCampaignTagMatchTier.CORE,
					"除濕", FestiveCampaignTagMatchTier.GENERAL),
			WeatherSignalType.HUMID, Map.of(
					"除濕", FestiveCampaignTagMatchTier.CORE),
			WeatherSignalType.STRONG_WIND, Map.of(
					"防風", FestiveCampaignTagMatchTier.CORE),
			WeatherSignalType.COLD, Map.of(
					"保暖", FestiveCampaignTagMatchTier.CORE),
			WeatherSignalType.COOL, Map.of(
					"保暖", FestiveCampaignTagMatchTier.WEAK),
			WeatherSignalType.DRY_COOL, Map.of(
					"保暖", FestiveCampaignTagMatchTier.GENERAL));
	// NORMAL 刻意不在表裡：一般天氣不該命中任何商品，見 syncOne() 的略過邏輯。

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
	 */
	@Scheduled(cron = "0 0 5 * * *")
	@Transactional
	public void syncWeatherCampaigns() {
		List<WeatherSignal> signals = weatherSignalProvider.getActiveSignals();

		Set<String> syncedCodes = signals.stream()
				.filter(signal -> WEATHER_TAG_MAPPING.containsKey(signal.getType()))
				.map(this::syncOne)
				.collect(Collectors.toSet());

		expireStaleWeatherCampaigns(syncedCodes);
	}

	/** upsert 單一天氣訊號對應的檔期，回傳這筆檔期的 campaign_code。 */
	private String syncOne(WeatherSignal signal) {
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
		if (campaign.getIsManualOverride() == null) {
			campaign.setIsManualOverride(false);
		}

		FestiveCampaign saved = festiveCampaignRepository.save(campaign);

		festiveCampaignTagRepository.deleteByCampaignId(saved.getId());
		saveTags(saved.getId(), WEATHER_TAG_MAPPING.get(signal.getType()));

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
	private void expireStaleWeatherCampaigns(Set<String> syncedCodes) {
		List<FestiveCampaign> activeWeatherCampaigns = festiveCampaignRepository
				.findByCategoryAndCampaignStatusInAndIsManualOverrideFalse(
						FestiveCategory.WEATHER,
						List.of(FestiveCampaignStatus.PREPARING, FestiveCampaignStatus.ACTIVE));

		for (FestiveCampaign campaign : activeWeatherCampaigns) {
			if (!syncedCodes.contains(campaign.getCampaignCode())) {
				campaign.setCampaignStatus(FestiveCampaignStatus.EXPIRED);
				festiveCampaignRepository.save(campaign);
			}
		}
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
