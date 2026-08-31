package com.example.Product_Selection_260813.service;

import java.util.Optional;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductEvaluation;

/**
 * LLM廠商依賴的抽象介面（企劃書QA5／十四-2明訂的架構決策）：
 * 「架構上仍以Service層抽象介面隔離廠商依賴，換模型只需改一個實作類別」。
 *
 * <b>現況（2026-08-28更新，取代舊版TODO註解）：</b>目前有兩個實作並存：
 * {@link MockLlmAnalysisService}（模擬資料，不呼叫外部API，不產生費用）與
 * {@link GeminiAnalysisServiceImpl}（正式串接Google Gemini API，標註
 * {@code @Primary}，Spring預設會注入這一個）。月用量保護機制已於
 * GeminiAnalysisServiceImpl內實作（依賴system_settings表計數）。
 *
 * 切換方式：拿掉GeminiAnalysisServiceImpl的{@code @Primary}並改用
 * {@code @Qualifier("mockLlmAnalysisService")}指定呼叫端注入Mock版，
 * 即可暫時切回模擬資料（例如額度耗盡、除錯、demo前避免誤觸真實API費用），
 * 呼叫端AiSelectionService完全不需要更動——與本介面的隔離設計精神一致。
 */
public interface LlmAnalysisService {

	/**
	 * 依商品與其目前評估結果，產生AI摘要／推薦原因／風險提示。
	 *
	 * @param product    要分析的商品，不可為null
	 * @param evaluation 該商品目前的評估結果；商品尚未有評估資料時為
	 *                   {@link Optional#empty()}。刻意使用Optional而非允許
	 *                   null，是為了讓呼叫端在編譯期就必須顯式處理「可能沒有
	 *                   評估資料」這個情境，而非事後才在執行期因忘記判斷null
	 *                   而拋出NullPointerException。
	 * @return 分析結果，包含摘要／推薦方向／理由（含風險提示）／使用的模型名稱
	 */
	LlmAnalysisResult generate(Product product, Optional<ProductEvaluation> evaluation);
}
