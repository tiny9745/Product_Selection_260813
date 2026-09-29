package com.example.Product_Selection_260813.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;

public interface DiscoveredItemRepository extends JpaRepository<DiscoveredItem, Long> {

	/**
	 * 第二階段略過回饋：已略過的項目（新到舊）。名稱用來排除相似的新結果，原因代碼用來當 AI 反例。
	 * 數量是人工逐筆略過累積的，規模有限，整批讀出即可。
	 */
	List<DiscoveredItem> findByStatusOrderByHandledAtDesc(DiscoveredItemStatus status);

	/** 一次探索的彙總結果批次比對既有紀錄（依正規化名稱），不逐筆查詢。 */
	List<DiscoveredItem> findByNormalizedNameIn(Collection<String> normalizedNames);

	/**
	 * 探索清單，依適配度排序（第二階段預設）：溫層判定不通過的沉到最後；有適配分的排前面、分數高者優先；
	 * 同分再看熱度、提及篇數、最後出現時間。seenSince 為 null＝不限最後出現時間。
	 */
	@Query(value = """
			SELECT d FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			ORDER BY CASE WHEN d.temperatureGate = com.example.Product_Selection_260813.enums.GateStatus.FAILED THEN 1 ELSE 0 END,
			         CASE WHEN d.fitScore IS NULL THEN 1 ELSE 0 END, d.fitScore DESC,
			         CASE WHEN d.popularityScore IS NULL THEN 1 ELSE 0 END, d.popularityScore DESC,
			         d.mentionCount DESC, d.lastSeenAt DESC, d.id DESC
			""", countQuery = """
			SELECT COUNT(d) FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			""")
	Page<DiscoveredItem> searchByFit(@Param("status") DiscoveredItemStatus status,
			@Param("seenSince") LocalDateTime seenSince, Pageable pageable);

	/** 探索清單，依最後出現時間排序（最新的話題在前）。 */
	@Query(value = """
			SELECT d FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			ORDER BY d.lastSeenAt DESC, d.mentionCount DESC, d.id DESC
			""", countQuery = """
			SELECT COUNT(d) FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			""")
	Page<DiscoveredItem> searchByRecent(@Param("status") DiscoveredItemStatus status,
			@Param("seenSince") LocalDateTime seenSince, Pageable pageable);

	/**
	 * 探索清單，依熱度排序（第一階段的排序）。排序：查過熱度的排前面、熱度高者優先，
	 * 再依近 7 天提及篇數、最後出現時間。seenSince 為 null＝不限最後出現時間。
	 */
	@Query(value = """
			SELECT d FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			ORDER BY CASE WHEN d.popularityScore IS NULL THEN 1 ELSE 0 END,
			         d.popularityScore DESC, d.mentionCount DESC, d.lastSeenAt DESC, d.id DESC
			""", countQuery = """
			SELECT COUNT(d) FROM DiscoveredItem d
			WHERE d.status = :status
			  AND (:seenSince IS NULL OR d.lastSeenAt >= :seenSince)
			""")
	Page<DiscoveredItem> search(@Param("status") DiscoveredItemStatus status,
			@Param("seenSince") LocalDateTime seenSince, Pageable pageable);
}
