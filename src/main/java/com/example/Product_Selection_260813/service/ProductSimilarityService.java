package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.Product_Selection_260813.dto.response.SimilarProductCandidateResponse;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.repository.ProductRepository;

/**
 * RESALE 商品搜尋相似候選參考商品。
 *
 * <b>這裡只做搜尋建議，不做自動合併判定</b>——系統排序建議，最終由人工從候選
 * 清單裡選一筆確認「是同一件」，或者都不選、當成全新品項處理。不自動判定的
 * 理由：誤判的代價不對稱。沒配到候選時系統退回品類層平均分數，這是保守、
 * 看得出來的（樣本數顯示 0）；但如果自動判定錯誤、把兩件實際不同的商品
 * 誤合併，算出來的分數會是一個看起來正常、但底層資料是錯的數字，不會有人
 * 回頭懷疑它——錯誤的連結比沒有連結更危險，因為它會被誤信任。
 *
 * <b>為什麼選 Jaro-Winkler 而非單純 Levenshtein</b>：商品名稱這種短字串，
 * Jaro-Winkler 對「開頭相同」給比較高的分數，符合商品名稱的實際狀況——
 * 「日式蜂蜜蛋糕」和「日式蜂蜜蛋糕 6入」差別在後面加了規格描述，開頭完全
 * 一致，Jaro-Winkler 會給出較高的相似度；單純 Levenshtein 對這種「後面
 * 多幾個字」的情況分數會被字數差距拉低。
 */
@Service
public class ProductSimilarityService {

	/**
	 * 名稱相似度的權重高於供應商相似度——名稱通常承載更多識別商品本身的
	 * 資訊（品項、規格、容量），供應商名稱只能佐證「這兩筆來源一致」，
	 * 不能單獨判斷是不是同一件商品（同一個供應商底下可能賣很多不同商品）。
	 */
	private static final BigDecimal NAME_WEIGHT = new BigDecimal("0.7");
	private static final BigDecimal SUPPLIER_WEIGHT = new BigDecimal("0.3");

	/**
	 * 低於這個綜合分數的候選不回傳——避免把明顯不相關的商品也塞進候選清單，
	 * 讓使用者每次都要從一長串裡面自己過濾。這個門檻是初始猜測值，沒有拿
	 * 真實資料驗證過，之後如果發現太嚴或太鬆，直接調整這個常數即可。
	 */
	private static final BigDecimal MIN_SCORE_THRESHOLD = new BigDecimal("0.3");

	/** 最多回傳幾筆候選，避免候選池很大時一次算出一長串低相關性結果。 */
	private static final int MAX_RESULTS = 10;

	private final ProductRepository productRepository;
	private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

	@Autowired
	public ProductSimilarityService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	/**
	 * 依品類（必要條件）＋名稱／供應商相似度（排序依據）找出候選參考商品。
	 *
	 * @param productTypeId 商品的小類 id，候選池的必要篩選條件；不同品類即使
	 *                      名稱再像也不會被列入，兩件不同品類的商品不該被
	 *                      視為同一件的可能性極低，硬要比對只會製造雜訊
	 * @param name          目前正在建立／編輯的商品名稱，用來跟候選逐一比對
	 * @param supplierName  目前的供應商名稱，可為 null（比對時該筆的供應商
	 *                      相似度會是 null，不影響名稱相似度的計算）
	 * @param excludeId     編輯既有商品時，排除商品自己（新增時傳 null）
	 */
	public List<SimilarProductCandidateResponse> findSimilarCandidates(Long productTypeId, String name,
			String supplierName, Long excludeId) {
		List<Product> pool = productRepository.findCandidatesByProductType(productTypeId);

		return pool.stream()
				.filter(p -> excludeId == null || !excludeId.equals(p.getId()))
				.map(p -> score(p, name, supplierName))
				.filter(r -> r.getCombinedScore().compareTo(MIN_SCORE_THRESHOLD) >= 0)
				.sorted(Comparator.comparing(SimilarProductCandidateResponse::getCombinedScore).reversed())
				.limit(MAX_RESULTS)
				.toList();
	}

	private SimilarProductCandidateResponse score(Product candidate, String name, String supplierName) {
		BigDecimal nameSim = similarity(name, candidate.getName());

		// 供應商相似度：任一邊沒填供應商名稱時回傳 null（無法比較），不是 0——
		// 0 分會被當成「明確不像」拉低綜合分數，但「沒填」跟「填了但不一樣」
		// 是完全不同的語意，不該用同一個數字表示。
		BigDecimal supplierSim = (supplierName != null && !supplierName.isBlank()
				&& candidate.getSupplierName() != null && !candidate.getSupplierName().isBlank())
				? similarity(supplierName, candidate.getSupplierName())
				: null;

		// 綜合分數：供應商相似度不存在時，權重全部歸給名稱相似度，而不是把
		// 缺項當 0 分帶進加權平均——否則「供應商欄位沒填」會系統性拉低所有
		// 候選的分數，跟供應商本身像不像沒有關係，是資料完整度造成的偏誤。
		BigDecimal combined = supplierSim != null
				? nameSim.multiply(NAME_WEIGHT).add(supplierSim.multiply(SUPPLIER_WEIGHT))
				: nameSim;

		return SimilarProductCandidateResponse.of(candidate, nameSim, supplierSim,
				combined.setScale(4, RoundingMode.HALF_UP));
	}

	private BigDecimal similarity(String a, String b) {
		if (a == null || b == null) {
			return BigDecimal.ZERO;
		}
		double score = jaroWinkler.apply(a.trim(), b.trim());
		return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
	}
}
