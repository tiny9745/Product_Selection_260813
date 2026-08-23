package com.example.Product_Selection_260813.service;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;

/**
 * LLM廠商依賴的抽象介面（企劃書QA5／十四-2明訂的架構決策）：
 * 「架構上仍以Service層抽象介面隔離廠商依賴，換模型只需改一個實作類別」。
 *
 * 目前唯一實作是{@link MockLlmAnalysisService}，回傳模擬資料、不呼叫任何外部
 * API、不產生任何費用。企劃書已定案廠商為GPT-5.6 Luna（OpenAI相容介面），
 * 但實際串接（API Key管理、HTTP呼叫、prompt設計、失敗重試、月用量保護機制
 * ——依賴system_settings表、屬於SettingsService尚未建立的範圍）都還沒有實作，
 * 屬於另一項獨立任務。
 *
 * <b>TODO：</b>之後要接上真實LLM API時，只需要新增一個實作這個介面的類別
 * （例如GptLunaAnalysisService），並讓Spring改用該實作（例如替換掉
 * MockLlmAnalysisService的@Service，或用@Primary／@Qualifier區分），
 * 呼叫端AiSelectionService完全不需要更動。
 */
public interface LlmAnalysisService {

	/**
	 * 依商品與其目前評估結果，產生AI摘要／推薦原因／風險提示。
	 *
	 * @param product   要分析的商品，不可為null
	 * @param evaluation 該商品目前的評估結果，可能為null（商品尚未有評估資料時）
	 * @return 分析結果，包含摘要／推薦方向／理由（含風險提示）／使用的模型名稱
	 */
	LlmAnalysisResult generate(Product product, ProductEvaluation evaluation);
}
