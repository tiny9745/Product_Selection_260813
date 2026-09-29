package com.example.Product_Selection_260813.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.Product_Selection_260813.entity.DiscoveredItemEvidence;

public interface DiscoveredItemEvidenceRepository extends JpaRepository<DiscoveredItemEvidence, Long> {

	/** 清單頁一次查出整頁的佐證文章（避免 N+1），新到舊。 */
	List<DiscoveredItemEvidence> findByItemIdInOrderByPostedAtDesc(Collection<Long> itemIds);

	/** 寫入前比對哪些文章已經存過（UNIQUE item_id + post_path）。 */
	List<DiscoveredItemEvidence> findByItemId(Long itemId);

	/** 探索視窗內（發文時間 >= since）的所有佐證，用來在不呼叫 AI 的情況下重算提及篇數與推文量。 */
	List<DiscoveredItemEvidence> findByPostedAtGreaterThanEqual(LocalDateTime since);
}
