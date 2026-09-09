package com.example.Product_Selection_260813.service.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.algorithm.ScoringAlgorithms;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.service.resolver.AlgorithmSettings;

/**
 * 歷史成團率分數：巢狀貝氏收縮（全域 → 品類 → 商品）。
 *
 * <b>要解決的問題</b>：品類 A 開過 3 次全成團（100%）與品類 B 開過 50 次成團
 * 45 次（90%），直接比較 A 會贏，但常識上 B 更可靠——A 只是樣本太少還沒失敗過。
 * 收縮後 A=78.5、B=87.0，排序正確，而且沒有門檻、沒有斷崖，樣本從 0 筆到
 * 100 筆是平滑過渡的，不需要任何「樣本夠不夠」的判斷式。
 *
 * <b>商品層查誰</b>（設計上最容易誤解的一點）：
 * 正在被評估的通常是新商品，用它自己的 id 查必然是 0 筆，商品層等於不作用。
 * 真正會有資料的是兩種情況，依序嘗試：
 * <ol>
 * <li>RESALE 且採購指定了 resale_reference_product_id → 查那件參考商品的歷史。
 *     優先用它的理由是那是採購的明確判斷（「這兩件的客群和賣點一樣」），
 *     比系統自己推測可靠。</li>
 * <li>否則查這件商品自己的 id → 涵蓋常態品重複開團的情況（衛生紙、洗碗精
 *     每兩個月開一次，商品在系統裡是同一筆）。</li>
 * </ol>
 * 兩者都查不到時 n=0，收縮公式自動退回品類層，不需要額外的分支判斷。
 *
 * <b>商品層天生資料就少</b>：匯入的開團紀錄多半 product_id 為 null（商品不在
 * 系統內），所以商品層是加分項而非主力，主力永遠是品類層。這是設計上就
 * 預期的，不是缺陷。
 */
@Component
public class HistoricalScoreCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * 全域連一筆有效樣本都沒有時的保底先驗值。
	 *
	 * 用中性值 0.5 而非 0——系統剛上線還沒匯入任何開團紀錄時，不該讓所有商品
	 * 的歷史分數都是 0 分（那會被誤讀成「這些商品歷史表現很差」）。
	 */
	private static final BigDecimal FALLBACK_GLOBAL_RATE = new BigDecimal("0.5");

	private final GroupBuyRecordRepository groupBuyRecordRepository;
	private final AlgorithmSettings algorithmSettings;

	@Autowired
	public HistoricalScoreCalculator(GroupBuyRecordRepository groupBuyRecordRepository,
			AlgorithmSettings algorithmSettings) {
		this.groupBuyRecordRepository = groupBuyRecordRepository;
		this.algorithmSettings = algorithmSettings;
	}

	/**
	 * 計算歷史成團率分數，並一併回傳樣本數與模擬資料標記。
	 *
	 * 樣本數要回傳給 Signal 層顯示（「78.5 分（品類樣本 3 筆）」），也要寫進
	 * 審核快照——只存分數不存樣本數，事後無法判斷這個分數的可信度。
	 */
	public HistoricalScoreResult calculate(Product product) {
		if (product == null || product.getProductTypeId() == null) {
			return HistoricalScoreResult.unavailable();
		}

		// ---- 第一層：全域 ----
		long globalEffective = groupBuyRecordRepository.countGlobalEffective();
		BigDecimal globalRate = globalEffective > 0
				? rate(groupBuyRecordRepository.countGlobalFulfilled(), globalEffective)
				: FALLBACK_GLOBAL_RATE;

		// ---- 第二層：品類向全域收縮 ----
		Long productTypeId = product.getProductTypeId();
		long categoryEffective = groupBuyRecordRepository.countEffectiveByProductType(productTypeId);
		BigDecimal categoryRawRate = categoryEffective > 0
				? rate(groupBuyRecordRepository.countFulfilledByProductType(productTypeId), categoryEffective)
				: null;
		BigDecimal categoryAdjusted = ScoringAlgorithms.shrink(
				categoryRawRate, categoryEffective, globalRate, algorithmSettings.getShrinkageKCategory());

		// ---- 第三層：商品向品類收縮 ----
		Long historyProductId = resolveHistoryProductId(product);
		long productEffective = historyProductId != null
				? groupBuyRecordRepository.countEffectiveByProduct(historyProductId)
				: 0L;
		BigDecimal productRawRate = productEffective > 0
				? rate(groupBuyRecordRepository.countFulfilledByProduct(historyProductId), productEffective)
				: null;
		BigDecimal finalRate = ScoringAlgorithms.shrink(
				productRawRate, productEffective, categoryAdjusted, algorithmSettings.getShrinkageKProduct());

		boolean includesSimulated = groupBuyRecordRepository.countSimulatedByProductType(productTypeId) > 0;

		return new HistoricalScoreResult(
				finalRate.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
				categoryEffective,
				productEffective,
				historyProductId,
				includesSimulated);
	}

	/**
	 * 決定商品層要查哪一個 product_id。
	 *
	 * 優先用採購指定的參考商品，其次用商品自己。兩者都沒有歷史時，
	 * 收縮公式會自動退回品類層。
	 */
	private Long resolveHistoryProductId(Product product) {
		if (product.getResaleReferenceProductId() != null) {
			return product.getResaleReferenceProductId();
		}
		return product.getId();
	}

	private BigDecimal rate(long numerator, long denominator) {
		if (denominator <= 0) {
			return null;
		}
		return BigDecimal.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
	}

	/**
	 * 歷史分數計算結果。
	 *
	 * @param score 0~100 的分數；無法計算時為 null
	 * @param categorySampleSize 品類層的有效樣本數（FULFILLED + FAILED，不含 CANCELLED）
	 * @param productSampleSize 商品層的有效樣本數
	 * @param historyProductId 商品層實際查詢的 product_id（可能是參考商品）
	 * @param includesSimulatedData 該品類的歷史資料是否含模擬紀錄
	 */
	public record HistoricalScoreResult(
			BigDecimal score,
			long categorySampleSize,
			long productSampleSize,
			Long historyProductId,
			boolean includesSimulatedData) {

		public static HistoricalScoreResult unavailable() {
			return new HistoricalScoreResult(null, 0L, 0L, null, false);
		}
	}
}
