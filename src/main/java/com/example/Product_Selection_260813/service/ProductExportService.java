package com.example.Product_Selection_260813.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.common.CsvSupport;
import com.example.Product_Selection_260813.dto.request.ProductFilterRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductExportLog;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductExportLogRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductSearchCriteria;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;

/**
 * POST /api/products/export：審核通過商品 CSV 匯出（2026-09 採購儀表板規格 Part B）。
 *
 * <b>後端全量匯出</b>：沿用品項清單的篩選條件（ProductService.toCriteria()），固定
 * reviewStatus=APPROVED、不分頁，篩選條件下有幾筆就匯出幾筆，不受前端每頁 20 筆限制。
 *
 * <b>匯出紀錄與 CSV 在同一個交易</b>：產生內容與寫入 product_export_logs 在同一次請求、
 * 同一個交易完成，不做「前端下載成功後再呼叫標記 API」的兩段式設計——瀏覽器下載無法
 * 可靠回報成功，兩段式會出現「檔案有了、標記沒打上」或反過來的不一致狀態。
 * 取捨：若回應在網路傳輸途中失敗，商品仍會被記錄為已匯出；需要重拿檔案時，取消
 * 「未曾匯出」篩選再匯出一次即可（匯出紀錄只新增、不覆寫，歷次都保留）。
 *
 * <b>數字以審核快照為準</b>：最終分數讀 review_records.final_score_snapshot（雙軌讀取：
 * APPROVED 商品看核准當下凍結的分數，不看之後持續重算的 product_evaluations）。
 * 售價／成本屬核心選品資料，核准後被鎖定不可修改，直接讀商品本身即等於核准當下的值。
 */
@Service
public class ProductExportService {

	/** 同步匯出的筆數上限。超過時請使用者縮小篩選範圍，避免單一請求過久或記憶體過大。 */
	static final int MAX_EXPORT_ROWS = 5000;

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** 欄位順序即 CSV 欄位順序（中文欄名給人看；商品編號供回查系統）。 */
	static final List<String> HEADERS = List.of("商品編號", "商品名稱", "供應商", "商品分類", "售價", "毛利率(%)", "最終分數",
			"審核結果", "審核人", "審核時間", "送審批次", "檔期標籤", "品項狀態");

	private final ProductService productService;
	private final ProductRepository productRepository;
	private final ReviewRecordRepository reviewRecordRepository;
	private final ProductTypeRepository productTypeRepository;
	private final AppUserRepository appUserRepository;
	private final ProductExportLogRepository productExportLogRepository;

	public ProductExportService(ProductService productService, ProductRepository productRepository,
			ReviewRecordRepository reviewRecordRepository, ProductTypeRepository productTypeRepository,
			AppUserRepository appUserRepository, ProductExportLogRepository productExportLogRepository) {
		this.productService = productService;
		this.productRepository = productRepository;
		this.reviewRecordRepository = reviewRecordRepository;
		this.productTypeRepository = productTypeRepository;
		this.appUserRepository = appUserRepository;
		this.productExportLogRepository = productExportLogRepository;
	}

	/** 匯出結果：CSV 位元組（含 BOM）、資料列數、建議檔名。 */
	public record ExportResult(byte[] content, int rowCount, String filename) {
	}

	@Transactional
	public ExportResult exportApproved(ProductFilterRequest filter, String username) {
		AppUser exporter = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));

		// 這支端點的語意就是「匯出審核通過的商品」：不論前端送來的審核狀態是什麼，一律覆寫。
		ProductSearchCriteria criteria = productService.toCriteria(filter).withReviewStatus(ProductReviewStatus.APPROVED);
		List<Product> products = productRepository.search(criteria, Pageable.unpaged()).getContent();
		if (products.size() > MAX_EXPORT_ROWS) {
			throw new IllegalStateException(
					"符合條件的商品有 " + products.size() + " 筆，超過單次匯出上限 " + MAX_EXPORT_ROWS + " 筆，請縮小篩選範圍後再匯出");
		}

		LocalDateTime now = LocalDateTime.now();
		String filename = "approved-products_" + now.format(FILE_STAMP) + ".csv";
		if (products.isEmpty()) {
			// 0 筆：只回表頭，不寫匯出紀錄（沒有任何商品被匯出）。
			return new ExportResult(toBytes(List.of()), 0, filename);
		}

		Map<Long, ReviewRecord> latestRecordByProduct = latestReviewRecords(products);
		Map<Long, String> typeNameById = productTypeRepository
				.findAllById(products.stream().map(Product::getProductTypeId).filter(Objects::nonNull).collect(Collectors.toSet()))
				.stream().collect(Collectors.toMap(ProductType::getId, type -> Objects.requireNonNullElse(type.getName(), "")));
		Set<Long> userIds = Stream.concat(
				latestRecordByProduct.values().stream().map(ReviewRecord::getReviewerId),
				products.stream().map(Product::getSubmittedBy))
				.filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, String> userNameById = userIds.isEmpty() ? Map.of()
				: appUserRepository.findAllById(userIds).stream()
						.collect(Collectors.toMap(AppUser::getId, user -> Objects.requireNonNullElse(user.getName(), "")));

		// 排序：審核時間新→舊，同時間以商品編號遞增，讓每次匯出的列順序穩定。
		Comparator<Product> order = Comparator
				.comparing((Product product) -> reviewedAt(latestRecordByProduct.get(product.getId())),
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(Product::getId);
		List<List<String>> rows = products.stream().sorted(order)
				.map(product -> toRow(product, latestRecordByProduct.get(product.getId()), typeNameById, userNameById))
				.toList();

		String runId = UUID.randomUUID().toString();
		productExportLogRepository.saveAll(products.stream()
				.map(product -> new ProductExportLog(runId, product.getId(), now, exporter.getId()))
				.toList());

		return new ExportResult(toBytes(rows), rows.size(), filename);
	}

	/** 每件商品最新一筆審核紀錄。APPROVED 商品的最新一筆就是核准快照（核准後不會再送審）。 */
	private Map<Long, ReviewRecord> latestReviewRecords(List<Product> products) {
		List<Long> ids = products.stream().map(Product::getId).toList();
		return reviewRecordRepository.findByProductIdIn(ids).stream()
				.collect(Collectors.toMap(ReviewRecord::getProductId, Function.identity(),
						(a, b) -> Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())
								.compare(a.getReviewedAt(), b.getReviewedAt()) >= 0 ? a : b));
	}

	private static LocalDateTime reviewedAt(ReviewRecord record) {
		return record == null ? null : record.getReviewedAt();
	}

	private List<String> toRow(Product product, ReviewRecord record, Map<Long, String> typeNameById,
			Map<Long, String> userNameById) {
		List<String> row = new ArrayList<>(HEADERS.size());
		row.add(String.valueOf(product.getId()));
		row.add(text(product.getName()));
		row.add(text(product.getSupplierName()));
		row.add(text(product.getProductTypeId() == null ? null : typeNameById.get(product.getProductTypeId())));
		row.add(number(product.getSalePrice()));
		row.add(number(marginRate(product.getSalePrice(), product.getCostPrice())));
		row.add(number(record == null ? null : record.getFinalScoreSnapshot()));
		row.add("審核通過");
		row.add(text(record == null || record.getReviewerId() == null ? null : userNameById.get(record.getReviewerId())));
		row.add(record == null || record.getReviewedAt() == null ? "" : record.getReviewedAt().format(DATE_TIME));
		row.add(text(submissionBatchLabel(product, userNameById)));
		row.add(text(normalizeTags(product.getCampaignTags())));
		row.add(product.getItemStatus() == ProductItemStatus.ARCHIVED ? "已封存" : "使用中");
		return row;
	}

	/** 送審批次的人讀格式「2026-09-25 陳小姐」；沒有批次資料時留白（與畫面的「（無批次資料）」對應）。 */
	private static String submissionBatchLabel(Product product, Map<Long, String> userNameById) {
		if (product.getSubmittedAt() == null) {
			return "";
		}
		String name = product.getSubmittedBy() == null ? "" : userNameById.getOrDefault(product.getSubmittedBy(), "");
		return (product.getSubmittedAt().toLocalDate() + " " + name).trim();
	}

	/** 毛利率（%）：與商品詳情頁同一口徑，(售價 − 成本) ÷ 售價 × 100，小數一位；無法計算時留白。 */
	static BigDecimal marginRate(BigDecimal salePrice, BigDecimal costPrice) {
		if (salePrice == null || costPrice == null || salePrice.signum() == 0) {
			return null;
		}
		return salePrice.subtract(costPrice).multiply(BigDecimal.valueOf(100)).divide(salePrice, 1, RoundingMode.HALF_UP);
	}

	/** 檔期標籤在資料庫是半形逗號分隔，輸出改用頓號，避免讀者誤以為是 CSV 欄位分隔。 */
	private static String normalizeTags(String tags) {
		if (tags == null || tags.isBlank()) {
			return "";
		}
		return Stream.of(tags.split("[,，、]+")).map(String::trim).filter(tag -> !tag.isEmpty())
				.collect(Collectors.joining("、"));
	}

	private static String number(BigDecimal value) {
		return CsvSupport.number(value);
	}

	/** 規則見 CsvSupport.text()（2026-09-26 抽出共用）；保留此入口供既有測試與本類別使用。 */
	static String text(String value) {
		return CsvSupport.text(value);
	}

	/** 規則見 CsvSupport.escape()。 */
	static String escape(String field) {
		return CsvSupport.escape(field);
	}

	static byte[] toBytes(List<List<String>> rows) {
		return CsvSupport.toBytes(HEADERS, rows);
	}
}
