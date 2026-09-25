package com.example.Product_Selection_260813.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.Product_Selection_260813.service.campaign.FestiveCampaignsChangedEvent;

/**
 * 檔期相關設定變更後，立即重算節慶加成（2026-09-24，方案 2）。
 *
 * AFTER_COMMIT：等設定真正寫入後才重算，重算讀到的一定是新設定；發布端的交易若回滾就不重算。
 * fallbackExecution=true：發布端沒有交易時（理論上不會）也照樣執行，不讓事件被默默丟掉。
 *
 * 重算失敗只記 log、不往外拋：此時設定已提交，拋例外只會讓使用者看到「儲存失敗」的錯誤訊息，
 * 實際上設定已存好；加成最晚在隔天 00:05 排程補上。
 * 另外在應用程式啟動完成時重算一次（onApplicationReady）。
 * 同步執行（非 @Async）：專案未啟用非同步設定，資料量為數百筆等級，重算在同一次請求內完成，
 * 使用者回到頁面時看到的就是新加成。
 */
@Component
public class FestivalBoostRefreshListener {

	private static final Logger log = LoggerFactory.getLogger(FestivalBoostRefreshListener.class);

	private final ScoringService scoringService;

	public FestivalBoostRefreshListener(ScoringService scoringService) {
		this.scoringService = scoringService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onFestiveCampaignsChanged(FestiveCampaignsChangedEvent event) {
		try {
			int updated = scoringService.refreshFestivalBoosts();
			log.info("檔期設定變更（{}）後重算節慶加成，更新 {} 筆", event.reason(), updated);
		} catch (RuntimeException e) {
			log.error("檔期設定變更（{}）後重算節慶加成失敗，將由每日 00:05 排程補算", event.reason(), e);
		}
	}

	/**
	 * 應用程式啟動完成後重算一次（2026-09-24）：部署或重啟期間可能錯過 00:05 排程，
	 * 不補算的話要等到隔天才會更新。失敗只記 log，不影響啟動。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		try {
			int updated = scoringService.refreshFestivalBoosts();
			log.info("應用程式啟動後重算節慶加成，更新 {} 筆", updated);
		} catch (RuntimeException e) {
			log.error("應用程式啟動後重算節慶加成失敗，將由每日 00:05 排程補算", e);
		}
	}
}
