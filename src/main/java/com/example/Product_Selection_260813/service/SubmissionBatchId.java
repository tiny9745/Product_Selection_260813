package com.example.Product_Selection_260813.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 送審批次識別碼（2026-09 CSV 匯出，規格 Part B2 決議）。
 *
 * 批次的定義：同一位送審人、同一個曆日（自然日，不是 24 小時滾動）送出的商品。
 * 不在資料庫另存批次編號，而是由 products.submitted_by + DATE(submitted_at) 推導，
 * 所以逐筆新增、批次新增、同一天分好幾次送出，都會自然落在同一批，不需要配號邏輯。
 *
 * 對外（API／前端下拉）以不透明字串表示：
 * <ul>
 * <li>{@code yyyy-MM-dd_{userId}}：一個批次，例如 {@code 2026-09-25_3}。
 * 由後端 GET /api/products/submission-batches 產生，前端原樣帶回，不要自行拼組。</li>
 * <li>{@code NONE}：沒有送審批次資料的商品（V25 上線前重新送審過、無法回填）。</li>
 * </ul>
 */
public record SubmissionBatchId(LocalDate submittedDate, Long submittedBy, boolean none) {

	public static final String NONE = "NONE";
	private static final String SEPARATOR = "_";

	public static SubmissionBatchId of(LocalDate submittedDate, Long submittedBy) {
		return new SubmissionBatchId(submittedDate, submittedBy, false);
	}

	/**
	 * 解析前端帶回的批次字串；null／空白＝不篩選（回傳 null）。
	 *
	 * @throws IllegalArgumentException 格式不符（400，由 GlobalExceptionHandler 處理）
	 */
	public static SubmissionBatchId parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		if (NONE.equals(value)) {
			return new SubmissionBatchId(null, null, true);
		}
		int separator = value.lastIndexOf(SEPARATOR);
		if (separator <= 0 || separator == value.length() - 1) {
			throw new IllegalArgumentException("送審批次格式不正確：" + raw);
		}
		try {
			LocalDate date = LocalDate.parse(value.substring(0, separator));
			Long userId = Long.valueOf(value.substring(separator + 1));
			return of(date, userId);
		} catch (DateTimeParseException | NumberFormatException e) {
			throw new IllegalArgumentException("送審批次格式不正確：" + raw);
		}
	}

	/** 批次涵蓋的時間起點（當日 00:00，含）；NONE 為 null。 */
	public LocalDateTime rangeStart() {
		return none ? null : submittedDate.atStartOfDay();
	}

	/** 批次涵蓋的時間終點（隔日 00:00，不含）；NONE 為 null。 */
	public LocalDateTime rangeEndExclusive() {
		return none ? null : submittedDate.plusDays(1).atStartOfDay();
	}

	@Override
	public String toString() {
		return none ? NONE : submittedDate + SEPARATOR + submittedBy;
	}
}
