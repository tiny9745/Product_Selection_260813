package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.common.CsvSupport;
import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.request.DecisionRecordExportRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.ReviewRisk;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.ReviewRiskRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;

/**
 * POST /api/reviews/decision-records/export：管理層唯讀匯出（2026-09-26 決議）。
 *
 * <b>與品項管理的「匯出 CSV」分工</b>：
 * <ul>
 * <li>品項管理（操作層）匯出＝把審核通過商品交接出去，會寫入 product_export_logs，
 * 「只看未曾匯出」依此判斷。</li>
 * <li>這裡（管理層）是報表用途，只讀、<b>不寫任何匯出紀錄</b>——管理者匯出來看，
 * 不能讓商品變成「已匯出」，否則操作層會漏交接。</li>
 * </ul>
 *
 * 內容是審核紀錄（每次審核一列，含被拒絕後重送的各輪），條件與決策紀錄頁相同，
 * 排序固定審核時間新到舊。分數一律讀審核快照，與畫面一致。上限與品項匯出相同。
 */
@Service
public class DecisionRecordExportService {

	static final int MAX_EXPORT_ROWS = ProductExportService.MAX_EXPORT_ROWS;

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	static final List<String> HEADERS = List.of("審核紀錄編號", "商品編號", "商品名稱（審核當時）", "送審次數", "審核結果",
			"審核人", "審核時間", "基本分數", "節慶加成", "天氣加成", "最終分數", "風險評估", "其他風險說明", "審核留言");

	private final ReviewRecordRepository reviewRecordRepository;
	private final ReviewRiskRepository reviewRiskRepository;
	private final RiskOptionRepository riskOptionRepository;
	private final AppUserRepository appUserRepository;

	public DecisionRecordExportService(ReviewRecordRepository reviewRecordRepository,
			ReviewRiskRepository reviewRiskRepository, RiskOptionRepository riskOptionRepository,
			AppUserRepository appUserRepository) {
		this.reviewRecordRepository = reviewRecordRepository;
		this.reviewRiskRepository = reviewRiskRepository;
		this.riskOptionRepository = riskOptionRepository;
		this.appUserRepository = appUserRepository;
	}

	/** 匯出結果：CSV 位元組（含 BOM）、資料列數、建議檔名。 */
	public record ExportResult(byte[] content, int rowCount, String filename) {
	}

	@Transactional(readOnly = true)
	public ExportResult export(DecisionRecordExportRequest request) {
		DecisionRecordExportRequest f = request != null ? request : new DecisionRecordExportRequest();
		if (f.getReviewedFrom() != null && f.getReviewedTo() != null && f.getReviewedFrom().isAfter(f.getReviewedTo())) {
			throw new IllegalArgumentException(ValidationMessage.REVIEW_RECORD_DATE_RANGE_INVALID);
		}
		// 多取一筆判斷是否超過上限；與決策紀錄頁同一支查詢，條件語意保證一致。
		List<ReviewRecord> records = reviewRecordRepository.searchDecisionRecords(
				f.getReviewResult() == null ? null : f.getReviewResult().name(),
				f.getReviewedFrom() == null ? null : f.getReviewedFrom().atStartOfDay(),
				f.getReviewedTo() == null ? null : f.getReviewedTo().plusDays(1).atStartOfDay(),
				ReviewService.toLikeKeyword(f.getKeyword()), "reviewedAt", "DESC",
				PageRequest.of(0, MAX_EXPORT_ROWS + 1)).getContent();
		if (records.size() > MAX_EXPORT_ROWS) {
			throw new IllegalStateException(
					"符合條件的審核紀錄超過單次匯出上限 " + MAX_EXPORT_ROWS + " 筆，請縮小篩選範圍後再匯出");
		}

		String filename = "decision-records_" + LocalDateTime.now(BusinessTimeZone.TAIPEI).format(FILE_STAMP) + ".csv";
		if (records.isEmpty()) {
			return new ExportResult(CsvSupport.toBytes(HEADERS, List.of()), 0, filename);
		}

		Map<Long, String> reviewerNameById = userNames(
				records.stream().map(ReviewRecord::getReviewerId).filter(Objects::nonNull).collect(Collectors.toSet()));
		Map<Long, List<ReviewRisk>> risksByRecord = reviewRiskRepository
				.findById_ReviewIdIn(records.stream().map(ReviewRecord::getId).toList()).stream()
				.collect(Collectors.groupingBy(risk -> risk.getId().getReviewId()));
		Map<Long, String> riskNameById = riskOptionRepository.findAll().stream()
				.collect(Collectors.toMap(RiskOption::getId, option -> Objects.requireNonNullElse(option.getName(), "")));

		List<List<String>> rows = new ArrayList<>(records.size());
		for (ReviewRecord record : records) {
			List<ReviewRisk> risks = risksByRecord.getOrDefault(record.getId(), List.of());
			rows.add(toRow(record, reviewerNameById, risks, riskNameById));
		}
		return new ExportResult(CsvSupport.toBytes(HEADERS, rows), rows.size(), filename);
	}

	private static List<String> toRow(ReviewRecord record, Map<Long, String> reviewerNameById, List<ReviewRisk> risks,
			Map<Long, String> riskNameById) {
		String selectedRisks = risks.stream().filter(risk -> Boolean.TRUE.equals(risk.getIsSelected()))
				.map(risk -> riskNameById.getOrDefault(risk.getId().getRiskOptionId(), ""))
				.filter(name -> !name.isEmpty()).collect(Collectors.joining("、"));
		String otherNote = risks.stream().map(ReviewRisk::getManualNote).filter(Objects::nonNull).findFirst()
				.orElse(null);
		List<String> row = new ArrayList<>(HEADERS.size());
		row.add(String.valueOf(record.getId()));
		row.add(String.valueOf(record.getProductId()));
		row.add(CsvSupport.text(record.getProductSnapshot() == null ? null : record.getProductSnapshot().getName()));
		row.add(record.getSubmissionCount() == null ? "" : String.valueOf(record.getSubmissionCount()));
		row.add(record.getReviewStatus() == null ? "" : record.getReviewStatus().getReviewRecordReviewStatus());
		row.add(CsvSupport.text(record.getReviewerId() == null ? null : reviewerNameById.get(record.getReviewerId())));
		row.add(record.getReviewedAt() == null ? "" : record.getReviewedAt().format(DATE_TIME));
		row.add(CsvSupport.number(record.getTotalScore()));
		row.add(CsvSupport.number(record.getFestivalBoostSnapshot()));
		row.add(CsvSupport.number(record.getWeatherBoostSnapshot()));
		row.add(CsvSupport.number(record.getFinalScoreSnapshot()));
		row.add(CsvSupport.text(selectedRisks));
		row.add(CsvSupport.text(otherNote));
		row.add(CsvSupport.text(record.getReviewComment()));
		return row;
	}

	private Map<Long, String> userNames(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> result = new HashMap<>();
		for (AppUser user : appUserRepository.findAllById(Set.copyOf(ids))) {
			result.put(user.getId(), Objects.requireNonNullElse(user.getName(), ""));
		}
		return result;
	}
}
