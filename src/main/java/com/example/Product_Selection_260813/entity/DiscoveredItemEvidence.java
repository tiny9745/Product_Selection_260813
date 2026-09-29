package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PTT 新品探索的佐證文章（V33）。只存 PTT 文章路徑（/bbs/...），完整網址由
 * 回應 DTO 組出來，PTT 換網域時不需要改資料。
 */
@Entity
@Table(name = "discovered_item_evidence")
public class DiscoveredItemEvidence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "item_id", nullable = false)
	private Long itemId;

	@Column(name = "board", nullable = false, length = 40)
	private String board;

	@Column(name = "post_path", nullable = false, length = 120)
	private String postPath;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Column(name = "push_volume", nullable = false)
	private Integer pushVolume = 0;

	@Column(name = "posted_at", nullable = false)
	private LocalDateTime postedAt;

	@Column(name = "collected_at", nullable = false)
	private LocalDateTime collectedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getItemId() { return itemId; }
	public void setItemId(Long itemId) { this.itemId = itemId; }
	public String getBoard() { return board; }
	public void setBoard(String board) { this.board = board; }
	public String getPostPath() { return postPath; }
	public void setPostPath(String postPath) { this.postPath = postPath; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public Integer getPushVolume() { return pushVolume; }
	public void setPushVolume(Integer pushVolume) { this.pushVolume = pushVolume; }
	public LocalDateTime getPostedAt() { return postedAt; }
	public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
	public LocalDateTime getCollectedAt() { return collectedAt; }
	public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
}
