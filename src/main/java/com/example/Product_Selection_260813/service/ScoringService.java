package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import com.example.Product_Selection_260813.entity.EvaluationFactor;
import com.example.Product_Selection_260813.entity.EvaluationMode;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.entity.FestiveCampaignTag;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.enums.FestiveCampaignStatus;
import com.example.Product_Selection_260813.enums.FestiveCategory;
import com.example.Product_Selection_260813.json.MatchedCampaignSnapshot;
import com.example.Product_Selection_260813.json.TrendSnapshot;
import com.example.Product_Selection_260813.json.WeightFactorSnapshot;
import com.example.Product_Selection_260813.json.WeightSnapshot;
import com.example.Product_Selection_260813.repository.EvaluationFactorRepository;
import com.example.Product_Selection_260813.repository.EvaluationModeRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignRepository;
import com.example.Product_Selection_260813.repository.FestiveCampaignTagRepository;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * 對應企劃書十二-13分層決議：本類別目前僅實作「快照組裝所需的讀取」與
 * 「Festival Boost可解釋性明細計算」，範圍對應GET /api/reviews/{productId}與
 * POST /api/reviews當下所需的資料，<b>不包含</b>六大分項Base Score的完整評分重算引擎
 * （六大分項如何依商品商業條件／核心客群／歷史銷售等原始資料算出business_score等分數，
 * 屬於另一項獨立任務，商品評估結果目前假設由其他流程寫入product_evaluations表，
 * 本類別對該表僅做唯讀）。
 *
 * 之後若要補上ScoringController／完整評分重算邏輯，直接在本類別擴充方法即可，
 * ReviewService已經是透過本類別取得資料、不直接注入Repository，符合分層決議，
 * 屆時不需要更動ReviewService的呼叫方式。
 */
@Service
public class ScoringService {

	// Boost Cap：企劃書「節慶加成計分規則」明訂為「暫訂+5，絕對分數」，
	// 之後的歷史資料回測校準屬於Phase 2待辦（見十三），此處先照文件明訂值寫死。
	private static final BigDecimal BOOST_CAP = new BigDecimal("5");

	// 季節型檔期PREPARING期間固定係數（企劃書節慶加成計分規則明訂，非本類別臆測）。
	private static final BigDecimal SEASON_PREPARING_URGENCY_FACTOR = new BigDecimal("0.20");

	// Match Weight三層數值已改由FestiveCampaignTagMatchTier enum攜帶（見該類別），
	// 這裡不再需要暫定的DEFAULT_MATCH_WEIGHT——festive_campaign_tags表補上分級資料後，
	// 缺口已解決，見buildMatchedCampaignSnapshot()。

	@Autowired
	private ProductEvaluationRepository productEvaluationRepository;

	@Autowired
	private EvaluationModeRepository evaluationModeRepository;

	@Autowired
	private EvaluationFactorRepository evaluationFactorRepository;

	@Autowired
	private FestiveCampaignRepository festiveCampaignRepository;

	@Autowired
	private FestiveCampaignTagRepository festiveCampaignTagRepository;

	@Autowired
	private TrendSignalRepository trendSignalRepository;

	/** 商品目前的即時評估結果（product_evaluations，唯讀）。可能為空——見類別註解。 */
	@Transactional(readOnly = true)
	public Optional<ProductEvaluation> getCurrentEvaluation(Long productId) {
		return productEvaluationRepository.findByProductId(productId);
	}

	/** 依評估模式ID查詢完整模式資料（用於補上評估模式名稱／版本）。 */
	@Transactional(readOnly = true)
	public Optional<EvaluationMode> getEvaluationMode(Long evaluationModeId) {
		if (evaluationModeId == null) {
			return Optional.empty();
		}
		return evaluationModeRepository.findById(evaluationModeId);
	}

	/**
	 * 組裝review_records.weight_snapshot：該評估模式當下的完整固定權重明細。
	 * evaluationModeId為null（商品尚未評估過）或查無此模式時回傳null。
	 */
	@Transactional(readOnly = true)
	public WeightSnapshot buildWeightSnapshot(Long evaluationModeId) {
		return getEvaluationMode(evaluationModeId).map(mode -> {
			List<EvaluationFactor> factors = evaluationFactorRepository
					.findByEvaluationModeIdOrderBySortOrderAsc(mode.getId());

			WeightSnapshot snapshot = new WeightSnapshot();
			snapshot.setModeCode(mode.getModeCode());
			snapshot.setModeName(mode.getModeName());
			snapshot.setVersion(mode.getVersion());
			snapshot.setFactors(factors.stream().map(this::toWeightFactorSnapshot).toList());
			return snapshot;
		}).orElse(null);
	}

	private WeightFactorSnapshot toWeightFactorSnapshot(EvaluationFactor factor) {
		WeightFactorSnapshot dto = new WeightFactorSnapshot();
		dto.setFactorCode(factor.getFactorCode());
		dto.setFactorName(factor.getFactorName());
		dto.setCategory(factor.getCategory());
		dto.setWeight(factor.getWeight());
		return dto;
	}

	/**
	 * 組裝review_records.trend_snapshot：該商品最新一筆趨勢資料。無資料時回傳null。
	 */
	@Transactional(readOnly = true)
	public TrendSnapshot buildTrendSnapshot(Long productId) {
		return trendSignalRepository.findFirstByProductIdOrderByCollectedAtDesc(productId).map(signal -> {
			TrendSnapshot snapshot = new TrendSnapshot();
			snapshot.setSource(signal.getSource());
			snapshot.setKeyword(signal.getKeyword());
			snapshot.setTrendScore(signal.getTrendScore());
			snapshot.setPopularityScore(signal.getPopularityScore());
			snapshot.setTrendDirection(signal.getTrendDirection() != null ? signal.getTrendDirection().name() : null);
			snapshot.setCollectedAt(signal.getCollectedAt());
			return snapshot;
		}).orElse(null);
	}

	/**
	 * 組裝review_records.matched_campaign_snapshot：Festival Boost可解釋性明細。
	 *
	 * 命中判定與係數計算依企劃書「節慶加成計分規則」：
	 * - 僅比對campaign_status IN (PREPARING, ACTIVE)的檔期
	 * - 命中＝product.campaign_tags與該檔期festive_campaign_tags.tag集合的交集非空
	 * - Match Weight＝命中標籤中最高等級的match_tier權重（核心1.0／一般0.6／弱0.3），
	 *   多標籤取最高等級不加總（見FestiveCampaignTagMatchTier）
	 * - 多節慶重疊：以「該檔期算出的Festival Boost」取最大值(MAX)，不加總
	 * - 未命中任何檔期時回傳null（對應「未命中檔期時不顯示此區塊，Festival Boost=0」）
	 *
	 * 一次批次查出所有候選檔期的標籤明細（findByCampaignIdIn），而非對每個候選檔期
	 * 各自查一次，避免候選檔期數量增加時的N+1查詢。
	 */
	@Transactional(readOnly = true)
	public MatchedCampaignSnapshot buildMatchedCampaignSnapshot(Product product) {
		Set<String> productTags = splitTags(product.getCampaignTags());
		if (productTags.isEmpty()) {
			return null;
		}

		List<FestiveCampaign> candidates = festiveCampaignRepository
				.findByCampaignStatusIn(List.of(FestiveCampaignStatus.PREPARING, FestiveCampaignStatus.ACTIVE));
		if (candidates.isEmpty()) {
			return null;
		}

		List<Long> candidateIds = candidates.stream().map(FestiveCampaign::getId).toList();
		Map<Long, List<FestiveCampaignTag>> tagsByCampaign = festiveCampaignTagRepository
				.findByCampaignIdIn(candidateIds).stream()
				.collect(Collectors.groupingBy(FestiveCampaignTag::getCampaignId));

		FestiveCampaign bestCampaign = null;
		Set<String> bestMatchedTags = Set.of();
		BigDecimal bestMatchWeight = BigDecimal.ZERO;
		BigDecimal bestUrgencyFactor = BigDecimal.ZERO;
		BigDecimal bestBoost = BigDecimal.ZERO;

		for (FestiveCampaign campaign : candidates) {
			List<FestiveCampaignTag> campaignTags = tagsByCampaign.getOrDefault(campaign.getId(), List.of());

			// 該檔期底下，命中商品標籤的所有tag，取其中match_tier權重最高者
			FestiveCampaignTag bestMatch = null;
			Set<String> matchedTags = new LinkedHashSet<>();
			for (FestiveCampaignTag campaignTag : campaignTags) {
				if (!productTags.contains(campaignTag.getTag())) {
					continue;
				}
				matchedTags.add(campaignTag.getTag());
				if (bestMatch == null || campaignTag.getMatchTier().getMatchWeight()
						.compareTo(bestMatch.getMatchTier().getMatchWeight()) > 0) {
					bestMatch = campaignTag;
				}
			}
			if (bestMatch == null) {
				continue; // 未命中
			}

			BigDecimal matchWeight = bestMatch.getMatchTier().getMatchWeight();
			BigDecimal urgencyFactor = calculateUrgencyFactor(campaign);
			BigDecimal boost = matchWeight.multiply(urgencyFactor).multiply(BOOST_CAP);

			if (boost.compareTo(bestBoost) > 0) {
				bestBoost = boost;
				bestCampaign = campaign;
				bestMatchedTags = matchedTags;
				bestMatchWeight = matchWeight;
				bestUrgencyFactor = urgencyFactor;
			}
		}

		if (bestCampaign == null) {
			return null;
		}

		MatchedCampaignSnapshot snapshot = new MatchedCampaignSnapshot();
		snapshot.setCampaignId(bestCampaign.getId());
		snapshot.setCampaignName(bestCampaign.getCampaignName());
		snapshot.setMatchedTags(new ArrayList<>(bestMatchedTags));
		snapshot.setMatchWeight(bestMatchWeight);
		snapshot.setUrgencyFactor(bestUrgencyFactor);
		return snapshot;
	}

	/**
	 * Urgency Factor（時間緊迫係數）：
	 * - ACTIVE：固定1.0
	 * - PREPARING＋節日型(FESTIVAL)：線性遞增 1－(剩餘天數/準備天數)
	 * - PREPARING＋季節型(SEASON)：固定0.2（不遞增）
	 * 其餘狀態（UPCOMING／EXPIRED）理論上不會進到這裡（呼叫端已用
	 * findByCampaignStatusIn(PREPARING, ACTIVE)過濾），此處僅防禦性回傳0。
	 */
	private BigDecimal calculateUrgencyFactor(FestiveCampaign campaign) {
		if (campaign.getCampaignStatus() == FestiveCampaignStatus.ACTIVE) {
			return BigDecimal.ONE;
		}
		if (campaign.getCampaignStatus() != FestiveCampaignStatus.PREPARING) {
			return BigDecimal.ZERO;
		}
		if (campaign.getCategory() == FestiveCategory.SEASON) {
			return SEASON_PREPARING_URGENCY_FACTOR;
		}

		long leadDays = campaign.getPreparationLeadDays() != null && campaign.getPreparationLeadDays() > 0
				? campaign.getPreparationLeadDays()
				: 1;
		long remainingDays = Math.max(ChronoUnit.DAYS.between(LocalDate.now(), campaign.getStartDate()), 0);

		BigDecimal ratio = BigDecimal.valueOf(remainingDays).divide(BigDecimal.valueOf(leadDays), 4,
				RoundingMode.HALF_UP);
		BigDecimal factor = BigDecimal.ONE.subtract(ratio);

		if (factor.compareTo(BigDecimal.ZERO) < 0) {
			factor = BigDecimal.ZERO;
		}
		if (factor.compareTo(BigDecimal.ONE) > 0) {
			factor = BigDecimal.ONE;
		}
		return factor.setScale(2, RoundingMode.HALF_UP);
	}

	private Set<String> splitTags(String tags) {
		if (tags == null || tags.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
