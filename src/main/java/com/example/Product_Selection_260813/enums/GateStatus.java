package com.example.Product_Selection_260813.enums;

/**
 * Gate 判定的四態。
 *
 * <b>INSUFFICIENT_DATA 不可被當成 PASSED</b>。若把「沒填資料」當成通過，系統就變成
 * 填得越少越容易過關——採購發現「溫層不填就自動過，填了反而可能被擋」，理性的
 * 做法就是不填，這會把整套 Gate 機制自己拆掉。標示為資料不足才會讓缺漏變成一個
 * 看得見的待辦。
 *
 * <b>NOT_APPLICABLE 與 INSUFFICIENT_DATA 是不同的事</b>。前者是「這個檢查對這件
 * 商品沒有意義」（文具沒有效期概念、商品沒對到任何檔期所以沒有備貨期限），
 * 後者是「這個檢查有意義但你沒給我資料」。混為一談會讓「資料不足」的數量
 * 灌水——當五項有四項都標示資料不足，主管會學會忽略這個標示，然後連真正
 * 該補的那一項也一起忽略。
 *
 * 因此 Gate 彙總必須分開統計四態，不可把後三者合併成「未通過」。
 */
public enum GateStatus {
	/** 檢查過，沒有問題。 */
	PASSED("通過"),
	/** 檢查過，確定有問題。 */
	FAILED("不通過"),
	/** 檢查有意義，但缺少判斷所需的資料。 */
	INSUFFICIENT_DATA("資料不足，無法判斷"),
	/** 這個檢查對這件商品不適用。 */
	NOT_APPLICABLE("不適用");

	private final String label;

	private GateStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** 是否為「明確擋下」。只有 FAILED 算，其餘三者都不阻擋。 */
	public boolean isBlocking() {
		return this == FAILED;
	}
}
