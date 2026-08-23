package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;

/**
 * {@link LlmAnalysisService}的模擬實作：不呼叫任何外部API，完全不產生費用，
 * 純粹依商品與評估分數組裝模板文字，讓POST /api/products/{id}/ai-analysis/generate
 * 這支端點能完整跑起來、有資料可看，但明確不是真的LLM推論結果。
 *
 * modelName固定標記為MOCK-LLM-v1，與企劃書定案的gpt-5.6-luna等真實模型名稱
 * 明確區隔，避免前端或人工審核誤以為這是真的AI分析結果。
 */
@Service
public class MockLlmAnalysisService implements LlmAnalysisService {

	private static final String MOCK_MODEL_NAME = "MOCK-LLM-v1";

	@Override
	public LlmAnalysisResult generate(Product product, ProductEvaluation evaluation) {
		LlmAnalysisResult result = new LlmAnalysisResult();
		result.setModelName(MOCK_MODEL_NAME);
		result.setSummary(buildSummary(product, evaluation));
		result.setRecommendation(buildRecommendation(evaluation));
		result.setReasons(buildReasons(product, evaluation));
		return result;
	}

	private String buildSummary(Product product, ProductEvaluation evaluation) {
		if (evaluation == null || evaluation.getTotalScore() == null) {
			return String.format("【模擬資料】「%s」目前尚無完整評估分數，暫無法產生摘要。", product.getName());
		}
		return String.format("【模擬資料】「%s」綜合加權分數為%s分，資料完整度%s%%。", product.getName(), evaluation.getTotalScore(),
				evaluation.getDataCompleteness() != null ? evaluation.getDataCompleteness() : "未知");
	}

	private String buildRecommendation(ProductEvaluation evaluation) {
		if (evaluation == null || evaluation.getTotalScore() == null) {
			return "【模擬資料】資料不足，暫緩建議";
		}
		BigDecimal totalScore = evaluation.getTotalScore();
		if (totalScore.compareTo(new BigDecimal("70")) >= 0) {
			return "【模擬資料】建議優先上架";
		}
		if (totalScore.compareTo(new BigDecimal("50")) >= 0) {
			return "【模擬資料】建議先核准，上架時機另議";
		}
		return "【模擬資料】建議暫緩或拒絕";
	}

	private String buildReasons(Product product, ProductEvaluation evaluation) {
		StringBuilder sb = new StringBuilder("【模擬資料，非真實LLM推論結果】");
		if (evaluation != null) {
			sb.append(String.format("六大分項：商業條件%s／核心客群%s／歷史銷售%s／預估購買%s／市場趨勢%s／預測人氣%s。",
					nullSafe(evaluation.getBusinessScore()), nullSafe(evaluation.getAudienceScore()),
					nullSafe(evaluation.getHistoricalScore()), nullSafe(evaluation.getPurchaseScore()),
					nullSafe(evaluation.getTrendScore()), nullSafe(evaluation.getForecastScore())));
		} else {
			sb.append(String.format("「%s」目前尚無評估資料。", product.getName()));
		}
		return sb.toString();
	}

	private String nullSafe(BigDecimal value) {
		return value != null ? value.toString() : "未知";
	}
}
