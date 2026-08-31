package com.example.Product_Selection_260813.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.response.AiAnalysisResponse;
import com.example.Product_Selection_260813.entity.AiAnalysis;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;
import com.example.Product_Selection_260813.repository.AiAnalysisRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;

/**
 * 對應企劃書十二-13分層決議：本類別實作ReviewService所需的AI摘要讀取／快照組裝，
 * 以及AiSelectionController掛的兩支端點（GET /api/products/{id}/ai-analysis、
 * POST /api/products/{id}/ai-analysis/generate）。
 *
 * <b>不包含</b>「AI主動選品」批次生成邏輯（POST /api/products/ai-suggested/batch-generate，
 * 由獨立的{@link AiSuggestionBatchService}負責，屬於三、品項管理範圍，
 * 僅供Daily Cron排程觸發，非本Controller/Service職責）。
 *
 * <b>LLM串接現況（2026-08-28更新，取代已過時的TODO註解）：</b>
 * generateAndReturnResponse()透過{@link LlmAnalysisService}介面取得分析結果，
 * 目前有{@link MockLlmAnalysisService}（模擬資料）與
 * {@link GeminiAnalysisServiceImpl}（正式串接gemini-3.6-flash，標註
 * {@code @Primary}）兩個實作並存，Spring預設注入Gemini版本。月用量保護
 * 機制已於GeminiAnalysisServiceImpl內實作完成（依賴system_settings表）。
 */
@Service
public class AiSelectionService {

	@Autowired
	private AiAnalysisRepository aiAnalysisRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ScoringService scoringService;

	@Autowired
	private LlmAnalysisService llmAnalysisService;

	/** 「目前有效版本」規則：該商品generated_at最新一筆（見AiAnalysisRepository註解），唯讀。 */
	@Transactional(readOnly = true)
	public Optional<AiAnalysis> getLatestAnalysis(Long productId) {
		return aiAnalysisRepository.findFirstByProductIdOrderByGeneratedAtDescIdDesc(productId);
	}

	/**
	 * GET /api/products/{id}/ai-analysis：純讀取，取得已快取的AI摘要／推薦原因／
	 * 風險提示；無快取則回傳空值（全部欄位為null的空物件，不拋錯、不回404——
	 * 「還沒有AI分析」是正常狀態）。本方法刻意不呼叫LlmAnalysisService，
	 * 避免違反REST冪等性、避免重複呼叫產生額外API成本（見企劃書備註）。
	 */
	@Transactional(readOnly = true)
	public AiAnalysisResponse getAiAnalysisResponse(Long productId) {
		if (!productRepository.existsById(productId)) {
			throw new IllegalArgumentException("商品不存在");
		}
		return getLatestAnalysis(productId).map(this::toResponse).orElseGet(AiAnalysisResponse::new);
	}

	/**
	 * POST /api/products/{id}/ai-analysis/generate：觸發生成AI分析並寫入快取。
	 *
	 * <b>關於評估分數的即時性（非雙軌Snapshot邏輯）：</b>此處呼叫
	 * {@link ScoringService#getCurrentEvaluation}取得的是即時評估值，
	 * 不同於{@link ScoringService#getEvaluation}／festival-boost端點採用的
	 * 「APPROVED商品讀Snapshot凍結值、其餘讀即時值」雙軌規則。這是刻意的設計
	 * 選擇：AI分析通常於審核「之前」被觸發、作為輔助判斷依據，語意上代表
	 * 「當下」的市場評估，而非審核當時被凍結的歷史快照。若未來UI允許對已
	 * APPROVED商品重新觸發本端點，分析文字引用的分數可能與該商品評估頁面
	 * 顯示的Snapshot凍結值不同——屆時再評估是否需要改用雙軌邏輯；目前六週
	 * 雛型階段前端不會對已核准商品開放「重新生成」入口，此不一致實務上
	 * 不會被使用者看到。
	 */
	@Transactional
	public AiAnalysisResponse generateAndReturnResponse(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("商品不存在"));
		Optional<ProductEvaluation> evaluationOpt = scoringService.getCurrentEvaluation(productId);

		LlmAnalysisResult result = llmAnalysisService.generate(product, evaluationOpt);

		AiAnalysis analysis = new AiAnalysis();
		analysis.setProductId(productId);
		analysis.setEvaluationId(evaluationOpt.map(ProductEvaluation::getId).orElse(null));
		analysis.setSummary(result.getSummary());
		analysis.setRecommendation(result.getRecommendation());
		analysis.setReasons(result.getReasons());
		analysis.setModelName(result.getModelName());

		AiAnalysis saved = aiAnalysisRepository.save(analysis);
		return toResponse(saved);
	}

	private AiAnalysisResponse toResponse(AiAnalysis analysis) {
		AiAnalysisResponse response = new AiAnalysisResponse();
		response.setSummary(analysis.getSummary());
		response.setRecommendation(analysis.getRecommendation());
		response.setReasons(analysis.getReasons());
		response.setModelName(analysis.getModelName());
		response.setGeneratedAt(analysis.getGeneratedAt());
		return response;
	}

	/**
	 * 組裝review_records.ai_summary_snapshot：「本次審核時管理所看到的AI摘要」。
	 *
	 * ai_summary_snapshot在資料表定義為單一TEXT欄位（非JSON結構化欄位），
	 * 故將summary／recommendation／reasons（含風險提示）合併為一段管理當下
	 * 實際閱讀到的完整文字，而非只存summary、遺漏推薦方向與風險提示；
	 * 無任何AI分析紀錄時回傳null，不虛構內容
	 * （對應企劃書AI摘要備註第2點：「資料不足時不得虛構資料」）。
	 */
	@Transactional(readOnly = true)
	public String buildAiSummarySnapshot(Long productId) {
		return getLatestAnalysis(productId).map(this::formatSnapshotText).orElse(null);
	}

	private String formatSnapshotText(AiAnalysis analysis) {
		StringBuilder sb = new StringBuilder();
		appendSection(sb, null, analysis.getSummary());
		appendSection(sb, "【推薦方向】", analysis.getRecommendation());
		appendSection(sb, "【理由與風險提示】", analysis.getReasons());
		return sb.isEmpty() ? null : sb.toString();
	}

	private void appendSection(StringBuilder sb, String label, String content) {
		if (content == null || content.isBlank()) {
			return;
		}
		if (!sb.isEmpty()) {
			sb.append("\n\n");
		}
		if (label != null) {
			sb.append(label);
		}
		sb.append(content);
	}
}
