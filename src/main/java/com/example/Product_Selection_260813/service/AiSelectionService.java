package com.example.Product_Selection_260813.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.entity.AiAnalysis;
import com.example.Product_Selection_260813.repository.AiAnalysisRepository;

/**
 * 對應企劃書十二-13分層決議：本類別目前僅實作ReviewService所需的AI摘要讀取
 * 與快照組裝，<b>不包含</b>LLM API串接／AI主動選品批次生成邏輯（GET/POST
 * /api/products/{id}/ai-analysis系列、POST /api/products/ai-suggested/batch-generate），
 * 那部分屬於另一項獨立任務（見企劃書十四-2 LLM廠商與預算決議），本類別對
 * ai_analyses表僅做唯讀。
 *
 * 之後若要補上AiSelectionController／LLM串接邏輯，直接在本類別擴充方法即可，
 * ReviewService已經是透過本類別取得資料、不直接注入Repository，符合分層決議。
 */
@Service
public class AiSelectionService {

	@Autowired
	private AiAnalysisRepository aiAnalysisRepository;

	/** 「目前有效版本」規則：該商品generated_at最新一筆（見AiAnalysisRepository註解），唯讀。 */
	@Transactional(readOnly = true)
	public Optional<AiAnalysis> getLatestAnalysis(Long productId) {
		return aiAnalysisRepository.findFirstByProductIdOrderByGeneratedAtDesc(productId);
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
