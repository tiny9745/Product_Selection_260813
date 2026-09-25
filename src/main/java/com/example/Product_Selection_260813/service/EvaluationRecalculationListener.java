package com.example.Product_Selection_260813.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 評分設定變更後，全量重算尚未核准商品的評估（2026-09-24）。
 *
 * 與 FestivalBoostRefreshListener 相同的設計：AFTER_COMMIT（設定寫入後才重算，回滾則不重算）、
 * 同步執行、失敗只記 log 不往外拋（設定已提交，拋例外只會讓使用者誤以為儲存失敗）。
 * 失敗時不會自動補算加權總分（每日排程只補節慶加成），下一次任何評分設定變更、
 * 或商品編輯時才會更新，因此以 error 等級記錄。
 */
@Component
public class EvaluationRecalculationListener {

	private static final Logger log = LoggerFactory.getLogger(EvaluationRecalculationListener.class);

	private final ScoringService scoringService;

	public EvaluationRecalculationListener(ScoringService scoringService) {
		this.scoringService = scoringService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onEvaluationSettingsChanged(EvaluationSettingsChangedEvent event) {
		try {
			int recalculated = scoringService.recalculatePendingEvaluations();
			log.info("評分設定變更（{}）後全量重算，完成 {} 筆", event.reason(), recalculated);
		} catch (RuntimeException e) {
			log.error("評分設定變更（{}）後全量重算失敗，品項分數仍為變更前的值", event.reason(), e);
		}
	}
}
