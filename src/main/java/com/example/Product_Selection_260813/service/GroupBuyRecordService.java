package com.example.Product_Selection_260813.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.Product_Selection_260813.dto.request.ClaimGroupBuyRecordsRequest;
import com.example.Product_Selection_260813.dto.response.GroupBuyImportResult;
import com.example.Product_Selection_260813.dto.response.GroupBuyImportResult.RowError;
import com.example.Product_Selection_260813.dto.response.GroupBuyRecordClaimCandidateResponse;
import com.example.Product_Selection_260813.dto.response.GroupBuyRecordResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.GroupBuyRecord;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.enums.GroupBuyResult;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.service.groupbuy.CsvParser;

/**
 * 歷史開團紀錄的匯入與查詢。
 *
 * <b>刻意不提供單筆新增／編輯／刪除端點</b>。這與其他 Service 的 CRUD 慣例
 * 不一致，是刻意的系統邊界設計——選品系統的職責到審核為止，不負責開團執行。
 * 開團結果由外部系統產生後批次匯入，系統內視為唯讀的參考資料。
 * Code review 時請勿為了「一致性」而補上 CRUD。
 *
 * 匯入採<b>全有或全無</b>：任何一列有錯就整批拒絕。部分成功會讓使用者無法判斷
 * 資料庫現在是什麼狀態，修正後重匯還會造成重複。
 */
@Service
public class GroupBuyRecordService {

	private static final Logger log = LoggerFactory.getLogger(GroupBuyRecordService.class);

	/** CSV 必要欄位。product_type_name 必填是刻意的，見類別註解與 validateRow()。 */
	private static final List<String> REQUIRED_HEADERS = List.of(
			"product_type_name", "external_product_name",
			"campaign_start_date", "campaign_end_date", "actual_quantity", "result");

	private static final List<String> KNOWN_HEADERS = List.of(
			"product_id", "product_type_name", "external_product_name", "supplier_name",
			"campaign_start_date", "campaign_end_date", "moq_at_time", "sale_price_at_time",
			"cost_price_at_time", "market_price_at_time",
			"target_quantity", "actual_quantity", "participant_count", "result",
			"complaint_count", "return_count", "is_simulated");

	private final GroupBuyRecordRepository groupBuyRecordRepository;
	private final ProductTypeRepository productTypeRepository;
	private final ProductRepository productRepository;
	private final AppUserRepository appUserRepository;

	@Autowired
	public GroupBuyRecordService(GroupBuyRecordRepository groupBuyRecordRepository,
			ProductTypeRepository productTypeRepository,
			ProductRepository productRepository,
			AppUserRepository appUserRepository) {
		this.groupBuyRecordRepository = groupBuyRecordRepository;
		this.productTypeRepository = productTypeRepository;
		this.productRepository = productRepository;
		this.appUserRepository = appUserRepository;
	}

	// =====================================================================
	// 匯入
	// =====================================================================

	/**
	 * 匯入 CSV。
	 *
	 * 驗證全部通過才寫入；有任一錯誤則回傳所有錯誤列，不寫入任何一筆。
	 * 方法標 @Transactional 是為了讓「整批寫入」在同一個交易內，
	 * 中途若有非預期例外（例如 FK 違反）也會整批回滾。
	 */
	@Transactional
	public GroupBuyImportResult importFromCsv(MultipartFile file, String username) {
		String content = readContent(file);
		List<List<String>> rows = CsvParser.parse(content);

		if (rows.isEmpty()) {
			return GroupBuyImportResult.ofFailure(0,
					List.of(new RowError(0, "file", "檔案是空的，或內容無法解析為 CSV")));
		}

		// ---- 標頭驗證 ----
		List<String> headers = rows.get(0).stream().map(h -> h.toLowerCase().trim()).toList();
		List<RowError> headerErrors = validateHeaders(headers);
		if (!headerErrors.isEmpty()) {
			return GroupBuyImportResult.ofFailure(rows.size() - 1, headerErrors);
		}
		Map<String, Integer> columnIndex = new HashMap<>();
		for (int i = 0; i < headers.size(); i++) {
			columnIndex.put(headers.get(i), i);
		}

		// ---- 逐列驗證與轉換 ----
		List<GroupBuyRecord> parsed = new ArrayList<>();
		List<RowError> errors = new ArrayList<>();
		// 先把用到的品類名稱與商品 id 蒐集起來一次查完，避免逐列打 DB 造成 N+1
		Set<String> productTypeNames = new HashSet<>();
		Set<Long> productIds = new HashSet<>();

		List<Map<String, String>> rowMaps = new ArrayList<>();
		for (int i = 1; i < rows.size(); i++) {
			Map<String, String> map = toRowMap(rows.get(i), columnIndex);
			rowMaps.add(map);
			String typeName = map.get("product_type_name");
			if (isPresent(typeName)) {
				productTypeNames.add(typeName.trim());
			}
			addIfLong(productIds, map.get("product_id"));
		}
		// 品類名稱 → id 對照。findByNameAndLevel() 回傳 List 是為了讓這裡能
		// 偵測「同名小類有多筆」這種資料庫沒有唯一約束擋住的邊界情況——
		// 只有剛好一筆才放進對照表，兩筆以上的名稱之後在 validateAndConvert()
		// 會查不到對照、被當成「品類名稱不存在」報錯，而不是靜默取第一筆
		// 造成使用者以為連到 A 品類、實際連到 B 品類。
		Map<String, Long> typeIdByName = new HashMap<>();
		for (String name : productTypeNames) {
			List<ProductType> matches = productTypeRepository.findByNameAndLevel(name, 2);
			if (matches.size() == 1) {
				typeIdByName.put(name, matches.get(0).getId());
			}
		}
		Set<Long> existingProductIds = new HashSet<>();
		productRepository.findAllById(productIds).forEach(p -> existingProductIds.add(p.getId()));

		for (int i = 0; i < rowMaps.size(); i++) {
			int csvRowNumber = i + 2; // +1 跳過標頭、+1 因為使用者看到的列號從 1 起算
			GroupBuyRecord record = validateAndConvert(rowMaps.get(i), csvRowNumber,
					typeIdByName, existingProductIds, errors);
			if (record != null) {
				parsed.add(record);
			}
		}

		if (!errors.isEmpty()) {
			return GroupBuyImportResult.ofFailure(rowMaps.size(), errors);
		}

		// ---- 全部通過才寫入 ----
		String batchId = "GBR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		Long importerId = resolveUserId(username);
		LocalDateTime now = LocalDateTime.now();
		for (GroupBuyRecord record : parsed) {
			record.setImportBatchId(batchId);
			record.setImportedAt(now);
			record.setImportedBy(importerId);
		}
		groupBuyRecordRepository.saveAll(parsed);
		log.info("開團紀錄匯入完成：批次 {}，共 {} 筆，匯入者 {}", batchId, parsed.size(), username);

		return GroupBuyImportResult.ofSuccess(batchId, parsed.size());
	}

	private List<RowError> validateHeaders(List<String> headers) {
		List<RowError> errors = new ArrayList<>();
		for (String required : REQUIRED_HEADERS) {
			if (!headers.contains(required)) {
				errors.add(new RowError(1, required, "缺少必要欄位「" + required + "」"));
			}
		}
		// 未知欄位只記 log 不擋——多帶欄位不影響匯入正確性，
		// 為此讓整批失敗會讓使用者難以從別的系統直接匯出檔案。
		headers.stream()
				.filter(h -> !h.isEmpty() && !KNOWN_HEADERS.contains(h))
				.forEach(h -> log.warn("CSV 含未知欄位「{}」，已忽略", h));
		return errors;
	}

	/**
	 * 逐列驗證與轉換。有錯就把錯誤加進 errors 並回傳 null。
	 *
	 * 刻意收集全部錯誤而非遇到第一個就中斷——使用者一次看到所有問題，
	 * 改一輪就好，不用反覆重送才逐個發現。
	 */
	private GroupBuyRecord validateAndConvert(Map<String, String> row, int rowNumber,
			Map<String, Long> typeIdByName, Set<Long> existingProductIds, List<RowError> errors) {
		int errorCountBefore = errors.size();
		GroupBuyRecord record = new GroupBuyRecord();

		// product_type_name：必填，且必須剛好對應到一筆小類。
		// 匯入必須指定品類、不做商品名稱模糊比對——自動比對會錯配，
		// 而錯配的資料會經由歷史分數進入不可覆蓋的審核快照。
		//
		// ⚠️ 這裡刻意用「名稱」不用「id」：準備這份 CSV 的人多半不是這套
		// 系統的使用者（見類別註解「匯入資料的商品多半不在系統內」），
		// 沒有理由要求他們先另外查過品類在資料庫裡的數字編號才能填表。
		String typeName = row.get("product_type_name");
		if (!isPresent(typeName)) {
			errors.add(new RowError(rowNumber, "product_type_name", "品類名稱必填"));
		} else if (!typeIdByName.containsKey(typeName.trim())) {
			errors.add(new RowError(rowNumber, "product_type_name",
					"品類名稱「" + typeName.trim() + "」不存在，或對應到一筆以上的品類（請確認名稱是否唯一）"));
		} else {
			record.setProductTypeId(typeIdByName.get(typeName.trim()));
		}

		// product_id：選填，但有填就必須存在
		String rawProductId = row.get("product_id");
		if (isPresent(rawProductId)) {
			Long productId = parseLong(rawProductId);
			if (productId == null) {
				errors.add(new RowError(rowNumber, "product_id", "商品 id 須為數字"));
			} else if (!existingProductIds.contains(productId)) {
				errors.add(new RowError(rowNumber, "product_id", "商品 id " + productId + " 不存在"));
			} else {
				record.setProductId(productId);
			}
		}

		String name = row.get("external_product_name");
		if (!isPresent(name)) {
			errors.add(new RowError(rowNumber, "external_product_name", "商品名稱必填"));
		} else if (name.length() > 200) {
			errors.add(new RowError(rowNumber, "external_product_name", "商品名稱長度不可超過 200 字元"));
		} else {
			record.setExternalProductName(name);
		}

		String supplier = row.get("supplier_name");
		if (isPresent(supplier) && supplier.length() > 100) {
			errors.add(new RowError(rowNumber, "supplier_name", "供應商名稱長度不可超過 100 字元"));
		} else {
			record.setSupplierName(isPresent(supplier) ? supplier : null);
		}

		LocalDate start = parseDate(row.get("campaign_start_date"), rowNumber, "campaign_start_date", errors, true);
		LocalDate end = parseDate(row.get("campaign_end_date"), rowNumber, "campaign_end_date", errors, true);
		if (start != null && end != null && start.isAfter(end)) {
			errors.add(new RowError(rowNumber, "campaign_start_date", "開團開始日期不可晚於結束日期"));
		}
		record.setCampaignStartDate(start);
		record.setCampaignEndDate(end);

		record.setMoqAtTime(parseNonNegativeInt(row.get("moq_at_time"), rowNumber, "moq_at_time", errors));
		record.setSalePriceAtTime(parseNonNegativeDecimal(row.get("sale_price_at_time"), rowNumber,
				"sale_price_at_time", errors));
		// 成本價與市價皆選填——舊資料在這次擴充之前匯入的沒有這兩欄是正常的，
		// 不強制要求；有填就驗證非負數，之後供目標區間 HISTORICAL 模式計算使用。
		record.setCostPriceAtTime(parseNonNegativeDecimal(row.get("cost_price_at_time"), rowNumber,
				"cost_price_at_time", errors));
		record.setMarketPriceAtTime(parseNonNegativeDecimal(row.get("market_price_at_time"), rowNumber,
				"market_price_at_time", errors));
		record.setTargetQuantity(parseNonNegativeInt(row.get("target_quantity"), rowNumber, "target_quantity", errors));
		record.setParticipantCount(parseNonNegativeInt(row.get("participant_count"), rowNumber, "participant_count",
				errors));

		// actual_quantity 必填：它是 MOQ 可行性判定的分位數基準來源，缺了就沒有意義
		String rawQty = row.get("actual_quantity");
		if (!isPresent(rawQty)) {
			errors.add(new RowError(rowNumber, "actual_quantity", "實際集單量必填"));
		} else {
			Integer qty = parseNonNegativeInt(rawQty, rowNumber, "actual_quantity", errors);
			record.setActualQuantity(qty);
		}

		// result 必填且須為認得的值
		String rawResult = row.get("result");
		if (!isPresent(rawResult)) {
			errors.add(new RowError(rowNumber, "result", "開團結果必填"));
		} else {
			try {
				record.setResult(GroupBuyResult.valueOf(rawResult.trim().toUpperCase()).name());
			} catch (IllegalArgumentException e) {
				errors.add(new RowError(rowNumber, "result",
						"開團結果須為 FULFILLED / FAILED / CANCELLED，目前為「" + rawResult + "」"));
			}
		}

		Integer complaint = parseNonNegativeInt(row.get("complaint_count"), rowNumber, "complaint_count", errors);
		record.setComplaintCount(complaint != null ? complaint : 0);
		Integer returns = parseNonNegativeInt(row.get("return_count"), rowNumber, "return_count", errors);
		record.setReturnCount(returns != null ? returns : 0);

		// is_simulated 未填一律視為 false（真實資料）。
		// 保守方向是「不把真實資料誤標為模擬」而非相反——標成模擬會讓分數被
		// 加註警語但不影響正確性，標成真實卻是假資料才是真正的問題。
		// 因此要求匯入方主動標記模擬資料。
		record.setIsSimulated(parseBoolean(row.get("is_simulated")));

		return errors.size() == errorCountBefore ? record : null;
	}

	// =====================================================================
	// 查詢與回退
	// =====================================================================

	@Transactional(readOnly = true)
	public List<GroupBuyRecordResponse> findByProductType(Long productTypeId) {
		return groupBuyRecordRepository.findByProductTypeIdOrderByCampaignStartDateDesc(productTypeId)
				.stream().map(GroupBuyRecordResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<GroupBuyRecordResponse> findByProduct(Long productId) {
		return groupBuyRecordRepository.findByProductIdOrderByCampaignStartDateDesc(productId)
				.stream().map(GroupBuyRecordResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<GroupBuyRecordResponse> findAll() {
		return groupBuyRecordRepository.findAll().stream().map(GroupBuyRecordResponse::from).toList();
	}

	/**
	 * 整批回退。匯入錯誤時不需要逐筆刪除。
	 *
	 * 回退後受影響品類的歷史分數會在下次 calculateEvaluation() 時自動反映，
	 * 這裡不主動觸發重算——重算範圍可能很大，應由呼叫端決定時機。
	 */
	@Transactional
	public int deleteBatch(String importBatchId) {
		List<GroupBuyRecord> records = groupBuyRecordRepository.findByImportBatchId(importBatchId);
		if (records.isEmpty()) {
			throw new IllegalArgumentException("找不到匯入批次：" + importBatchId);
		}
		groupBuyRecordRepository.deleteAll(records);
		log.info("開團紀錄批次回退：批次 {}，刪除 {} 筆", importBatchId, records.size());
		return records.size();
	}

	// =====================================================================
	// 解析工具
	// =====================================================================

	private String readContent(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("請選擇要匯入的 CSV 檔案");
		}
		try {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalArgumentException("檔案讀取失敗：" + e.getMessage());
		}
	}

	private Map<String, String> toRowMap(List<String> row, Map<String, Integer> columnIndex) {
		Map<String, String> map = new HashMap<>();
		columnIndex.forEach((header, idx) -> map.put(header, idx < row.size() ? row.get(idx) : null));
		return map;
	}

	private boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

	private void addIfLong(Set<Long> target, String raw) {
		Long v = parseLong(raw);
		if (v != null) {
			target.add(v);
		}
	}

	private Long parseLong(String raw) {
		if (!isPresent(raw)) {
			return null;
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Integer parseNonNegativeInt(String raw, int rowNumber, String field, List<RowError> errors) {
		if (!isPresent(raw)) {
			return null;
		}
		try {
			int v = Integer.parseInt(raw.trim());
			if (v < 0) {
				errors.add(new RowError(rowNumber, field, "不可為負數"));
				return null;
			}
			return v;
		} catch (NumberFormatException e) {
			errors.add(new RowError(rowNumber, field, "須為整數，目前為「" + raw + "」"));
			return null;
		}
	}

	private BigDecimal parseNonNegativeDecimal(String raw, int rowNumber, String field, List<RowError> errors) {
		if (!isPresent(raw)) {
			return null;
		}
		try {
			BigDecimal v = new BigDecimal(raw.trim());
			if (v.compareTo(BigDecimal.ZERO) < 0) {
				errors.add(new RowError(rowNumber, field, "不可為負數"));
				return null;
			}
			return v;
		} catch (NumberFormatException e) {
			errors.add(new RowError(rowNumber, field, "須為數值，目前為「" + raw + "」"));
			return null;
		}
	}

	private LocalDate parseDate(String raw, int rowNumber, String field, List<RowError> errors, boolean required) {
		if (!isPresent(raw)) {
			if (required) {
				errors.add(new RowError(rowNumber, field, "日期必填"));
			}
			return null;
		}
		try {
			// 只接受 ISO 格式（yyyy-MM-dd）。不自動嘗試多種格式——
			// 「2026/03/04」在不同地區可能是 3 月 4 日或 4 月 3 日，猜錯會產生
			// 看起來正常但實際錯誤的資料，明確要求單一格式比較安全。
			return LocalDate.parse(raw.trim());
		} catch (DateTimeParseException e) {
			errors.add(new RowError(rowNumber, field, "日期格式須為 yyyy-MM-dd，目前為「" + raw + "」"));
			return null;
		}
	}

	private boolean parseBoolean(String raw) {
		if (!isPresent(raw)) {
			return false;
		}
		String v = raw.trim().toLowerCase();
		return "true".equals(v) || "1".equals(v) || "y".equals(v) || "yes".equals(v);
	}

	private Long resolveUserId(String username) {
		if (username == null) {
			return null;
		}
		return appUserRepository.findByUsername(username).map(AppUser::getId).orElse(null);
	}

	// =====================================================================
	// 認領歷史紀錄（新增商品時，把匯入當下沒有對應系統商品的舊紀錄補上連結）
	// =====================================================================

	private static final BigDecimal CLAIM_NAME_WEIGHT = new BigDecimal("0.7");
	private static final BigDecimal CLAIM_SUPPLIER_WEIGHT = new BigDecimal("0.3");
	private static final BigDecimal CLAIM_MIN_SCORE_THRESHOLD = new BigDecimal("0.3");
	private static final int CLAIM_MAX_RESULTS = 10;
	private final org.apache.commons.text.similarity.JaroWinklerSimilarity jaroWinkler =
			new org.apache.commons.text.similarity.JaroWinklerSimilarity();

	/**
	 * 認領候選池：指定品類下 product_id 為 null 的歷史紀錄，依名稱／供應商
	 * 相似度排序。比對邏輯與門檻值刻意對齊 ProductSimilarityService
	 * ——同一個概念（人工核對候選、不自動判定）不該在系統裡有兩套不同的
	 * 相似度標準，讓使用者在兩個畫面看到不一致的「像不像」判斷。
	 */
	@Transactional(readOnly = true)
	public List<GroupBuyRecordClaimCandidateResponse> searchUnlinkedCandidates(Long productTypeId, String name,
			String supplierName) {
		List<GroupBuyRecord> pool = groupBuyRecordRepository.findByProductIdIsNullAndProductTypeId(productTypeId);

		return pool.stream()
				.map(record -> scoreClaim(record, name, supplierName))
				.filter(r -> r.getCombinedScore().compareTo(CLAIM_MIN_SCORE_THRESHOLD) >= 0)
				.sorted(Comparator.comparing(GroupBuyRecordClaimCandidateResponse::getCombinedScore).reversed())
				.limit(CLAIM_MAX_RESULTS)
				.toList();
	}

	private GroupBuyRecordClaimCandidateResponse scoreClaim(GroupBuyRecord record, String name,
			String supplierName) {
		BigDecimal nameSim = similarityScore(name, record.getExternalProductName());

		BigDecimal supplierSim = (supplierName != null && !supplierName.isBlank()
				&& record.getSupplierName() != null && !record.getSupplierName().isBlank())
				? similarityScore(supplierName, record.getSupplierName())
				: null;

		BigDecimal combined = supplierSim != null
				? nameSim.multiply(CLAIM_NAME_WEIGHT).add(supplierSim.multiply(CLAIM_SUPPLIER_WEIGHT))
				: nameSim;

		return GroupBuyRecordClaimCandidateResponse.of(record, nameSim, supplierSim,
				combined.setScale(4, java.math.RoundingMode.HALF_UP));
	}

	private BigDecimal similarityScore(String a, String b) {
		if (a == null || b == null) {
			return BigDecimal.ZERO;
		}
		double score = jaroWinkler.apply(a.trim(), b.trim());
		return BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP);
	}

	/**
	 * 把選定的歷史紀錄連結到指定商品。
	 *
	 * 全有全無：任一筆驗證失敗就整批拒絕，不做部分連結——部分成功會讓
	 * 使用者無法判斷到底連了哪幾筆，還要回頭比對，這跟 CSV 匯入的
	 * 全有全無原則是同一個理由。
	 *
	 * 驗證規則：
	 * 1. 商品必須存在
	 * 2. 每一筆歷史紀錄都必須存在
	 * 3. 每一筆歷史紀錄目前的 product_id 必須是 null——已經連結過的紀錄
	 *    不能被覆蓋，避免一次不小心的操作打斷別人已經建立好的連結
	 * 4. 每一筆歷史紀錄的 product_type_id 必須與商品的 product_type_id 一致
	 *    ——不同品類的紀錄不該被連到這件商品，那通常代表選錯了候選
	 */
	@Transactional
	public void claimRecords(ClaimGroupBuyRecordsRequest request) {
		Product product = productRepository.findById(request.getProductId())
				.orElseThrow(() -> new IllegalArgumentException("商品 id " + request.getProductId() + " 不存在"));

		List<GroupBuyRecord> records = groupBuyRecordRepository.findAllById(request.getGroupBuyRecordIds());
		if (records.size() != request.getGroupBuyRecordIds().size()) {
			throw new IllegalArgumentException("部分歷史紀錄 id 不存在");
		}
		for (GroupBuyRecord record : records) {
			if (record.getProductId() != null) {
				throw new IllegalStateException("歷史紀錄 " + record.getId() + " 已經連結過其他商品，不可覆蓋");
			}
			if (!record.getProductTypeId().equals(product.getProductTypeId())) {
				throw new IllegalStateException("歷史紀錄 " + record.getId() + " 的品類與商品不一致，不可連結");
			}
		}

		for (GroupBuyRecord record : records) {
			record.setProductId(product.getId());
		}
		groupBuyRecordRepository.saveAll(records);
		log.info("歷史紀錄認領完成：商品 {}，共 {} 筆", product.getId(), records.size());
	}
}
