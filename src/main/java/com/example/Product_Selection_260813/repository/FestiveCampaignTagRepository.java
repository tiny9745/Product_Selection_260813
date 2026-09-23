package com.example.Product_Selection_260813.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.FestiveCampaignTag;

public interface FestiveCampaignTagRepository extends JpaRepository<FestiveCampaignTag, Long> {

	// 檔期設定頁「標籤＋分級」清單顯示與編輯（SettingsController／SettingsService負責）
	List<FestiveCampaignTag> findByCampaignId(Long campaignId);

	// Festival Boost比對批次查詢：一次取回多個候選檔期(PREPARING/ACTIVE)底下的所有標籤分級，
	// 避免ScoringService對每個候選檔期各自查一次（見ScoringService.buildMatchedCampaignSnapshot()）
	List<FestiveCampaignTag> findByCampaignIdIn(List<Long> campaignIds);

	/**
	 * PUT /api/settings/festive-campaigns/{id} 整份覆蓋標籤清單：先刪除該檔期底下所有
	 * 既有標籤，再依Request重新寫入。
	 *
	 * ⚠️ 2026-09-17修正：原本是衍生的 `void deleteByCampaignId(Long campaignId)`——
	 * Spring Data JPA 對這種簽名的實作方式是「先 SELECT 出所有符合的 entity，
	 * 再逐一呼叫 entityManager.remove()」，這些 remove 動作會被放進 Hibernate
	 * 的 persistence context 動作佇列，等到真正 flush（通常是交易 commit 前）
	 * 才會送出實際的 DELETE SQL。
	 *
	 * 但 FestiveCampaignTag 用的是 IDENTITY 主鍵產生策略——這種策略的 INSERT
	 * 依規範必須立即同步執行（要馬上拿到資料庫產生的自動遞增 id），沒辦法
	 * 跟其他動作一起延後 flush。SettingsService.updateFestiveCampaign() 的
	 * 呼叫順序是「刪除舊標籤 → saveTags() 立刻 save() 新標籤」，結果就是新標籤
	 * 的 INSERT 搶在舊標籤的 DELETE 真正送出之前就先執行，撞上舊資料還沒被
	 * 刪除、(campaign_id, tag) 唯一約束仍然生效，於是丟出
	 * DataIntegrityViolationException——即使使用者只是改了備戰天數、標籤
	 * 內容完全沒變，也一樣會炸，因為問題根本不是「標籤重複」，是「刪除還
	 * 沒真的執行，新的就先插進去了」。
	 *
	 * 改成 @Modifying 的 JPQL 批次刪除：這種刪除不經過 persistence context
	 * 的動作佇列，呼叫當下就立即送出 DELETE SQL 並執行完成，保證在
	 * saveTags() 的 INSERT 執行之前，舊資料已經確實從資料庫刪除。
	 *
	 * ⚠️ 2026-09-24修正（「編輯檔期儲存後無效」）：原本只有 clearAutomatically = true。
	 * 這個選項會在批次刪除執行完後清空整個 Persistence Context——而
	 * SettingsService.updateFestiveCampaign() 在呼叫這支之前，已經改了 FestiveCampaign
	 * 的名稱／分類／日期／備戰天數，這些異動還停留在 Persistence Context 裡等待 commit
	 * 時 flush（save() 對既有 entity 只是 merge，不會立即送 UPDATE）。清空後 entity
	 * 變成 detached，異動直接被丟掉，commit 時根本沒有 UPDATE festive_campaigns 可送。
	 * 現象就是 SQL log 只有 select → delete tags → insert tags → select tags，標籤
	 * 有存進去、檔期基本資料沒有；回應是用記憶體裡的 detached 物件組的，所以畫面
	 * 當下看起來已更新，重新整理才發現沒存。
	 *
	 * 加上 flushAutomatically = true：執行批次刪除「之前」先 flush，把待送的 UPDATE
	 * 送出去，之後再清空就不會遺失任何異動。clearAutomatically 保留——批次刪除繞過
	 * Persistence Context，若同一交易稍早載入過這些標籤，清空可避免讀到已刪除的舊資料。
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FestiveCampaignTag t where t.campaignId = :campaignId")
	void deleteByCampaignId(@Param("campaignId") Long campaignId);
}
