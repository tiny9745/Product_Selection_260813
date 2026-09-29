package com.example.Product_Selection_260813.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 排程作業「執行紀錄」分頁（2026-09-29）：PTT 熱度同步、Google 趨勢、PTT 新品探索三個面板共用。
 *
 * 排序固定在 Repository 方法名稱（started_at 新到舊，同時間以 id 新到舊），不開放呼叫端指定，
 * 避免排序欄位沒有索引；三張表都有 started_at 索引（V23／V30／V33）。
 */
public final class RunHistoryPaging {

	/** 畫面預設每頁筆數。 */
	public static final int DEFAULT_SIZE = 10;
	/** 呼叫端可以要求的上限，避免 ?size=100000 一次撈出整張表。 */
	public static final int MAX_SIZE = 50;

	private RunHistoryPaging() {
	}

	/** page 小於 0 視為 0；size 超出 1～{@value #MAX_SIZE} 時夾回範圍內。 */
	public static Pageable of(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(MAX_SIZE, Math.max(1, size)));
	}
}
