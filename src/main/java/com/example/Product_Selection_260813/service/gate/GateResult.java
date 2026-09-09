package com.example.Product_Selection_260813.service.gate;

import java.util.List;

import com.example.Product_Selection_260813.enums.GateStatus;

/**
 * 單一 Gate 的判定結果。
 *
 * @param gateCode 判定項目代碼，如 GATE_TEMPERATURE_ZONE。也用來對應
 *                 risk_options.auto_trigger_code，讓審核端知道要預先勾選哪一項風險
 * @param status   四態判定，見 {@link GateStatus}
 * @param reason   對使用者可讀的說明，不是內部錯誤碼。不通過時要具體指出原因
 *                 （「商品為冷凍品，通路目前不支援冷凍配送」），而不是「Gate 失敗」
 * @param riskCategory 對應的五大風險面向，供審核端把結果歸到正確的風險分類
 */
public record GateResult(
		String gateCode,
		GateStatus status,
		String reason,
		String riskCategory) {

	public static GateResult passed(String gateCode, String riskCategory, String reason) {
		return new GateResult(gateCode, GateStatus.PASSED, reason, riskCategory);
	}

	public static GateResult failed(String gateCode, String riskCategory, String reason) {
		return new GateResult(gateCode, GateStatus.FAILED, reason, riskCategory);
	}

	public static GateResult insufficientData(String gateCode, String riskCategory, String reason) {
		return new GateResult(gateCode, GateStatus.INSUFFICIENT_DATA, reason, riskCategory);
	}

	public static GateResult notApplicable(String gateCode, String riskCategory, String reason) {
		return new GateResult(gateCode, GateStatus.NOT_APPLICABLE, reason, riskCategory);
	}

	/**
	 * Gate 判定的彙總。
	 *
	 * <b>四態分開統計，不可合併</b>。若把「不通過」「資料不足」「不適用」合併成
	 * 一個「未通過」數字，主管就無法分辨「這件有實際問題」和「這件只是資料
	 * 還沒填齊」——而這兩者的處理方式完全不同：前者要判斷要不要例外放行，
	 * 後者是請採購回去補欄位。
	 *
	 * 前端要判斷「有沒有實際問題」用 failedCount &gt; 0，「要不要提示補件」用
	 * insufficientDataCount &gt; 0——不另外提供布林方法，因為 record 的額外方法
	 * 不會被 Jackson 序列化進 JSON，前端拿不到，留著只會誤導。
	 *
	 * @param results 全部判定結果，依 Gate 的重要性排序
	 */
	public record Summary(
			List<GateResult> results,
			int passedCount,
			int failedCount,
			int insufficientDataCount,
			int notApplicableCount) {

		public static Summary of(List<GateResult> results) {
			int passed = 0;
			int failed = 0;
			int insufficient = 0;
			int notApplicable = 0;
			for (GateResult r : results) {
				switch (r.status()) {
				case PASSED -> passed++;
				case FAILED -> failed++;
				case INSUFFICIENT_DATA -> insufficient++;
				case NOT_APPLICABLE -> notApplicable++;
				}
			}
			return new Summary(List.copyOf(results), passed, failed, insufficient, notApplicable);
		}


		/** 不通過的項目，供審核端自動帶入對應的風險選項。 */
		public List<GateResult> failedResults() {
			return results.stream().filter(r -> r.status().isBlocking()).toList();
		}

		/**
		 * 給主管看的一句話摘要，例如「通過 3 項，不通過 1 項，資料不足 1 項」。
		 * 這段文字會存進 review_records.system_gate_summary，與主管自己寫的
		 * review_comment 分開——系統說的話不可混進主管的話裡。
		 */
		public String toDisplaySummary() {
			StringBuilder sb = new StringBuilder();
			sb.append("通過 ").append(passedCount).append(" 項");
			if (failedCount > 0) {
				sb.append("，不通過 ").append(failedCount).append(" 項");
			}
			if (insufficientDataCount > 0) {
				sb.append("，資料不足 ").append(insufficientDataCount).append(" 項");
			}
			if (notApplicableCount > 0) {
				sb.append("，不適用 ").append(notApplicableCount).append(" 項");
			}
			return sb.toString();
		}
	}
}
