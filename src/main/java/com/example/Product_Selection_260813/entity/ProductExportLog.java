package com.example.Product_Selection_260813.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 審核通過商品 CSV 匯出紀錄（V25，product_export_logs）。
 *
 * 每次匯出、每件商品一列，只新增不修改。刻意不寫回 products（理由見 V25 說明：
 * products.updated_at 會被連帶改掉）。「未曾匯出」＝此表沒有該商品的任何一列。
 *
 * exportedAt 由 ProductExportService 明確指定（同一次匯出每列相同），不用
 * @CreationTimestamp：同一批的每一列必須是同一個時間點，才能以時間對得上同一次匯出。
 */
@Entity
@Table(name = "product_export_logs")
public class ProductExportLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "export_run_id", nullable = false, updatable = false, length = 36)
	private String exportRunId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private Long productId;

	@Column(name = "exported_at", nullable = false, updatable = false)
	private LocalDateTime exportedAt;

	@Column(name = "exported_by", nullable = false, updatable = false)
	private Long exportedBy;

	protected ProductExportLog() {
	}

	public ProductExportLog(String exportRunId, Long productId, LocalDateTime exportedAt, Long exportedBy) {
		this.exportRunId = exportRunId;
		this.productId = productId;
		this.exportedAt = exportedAt;
		this.exportedBy = exportedBy;
	}

	public Long getId() {
		return id;
	}

	public String getExportRunId() {
		return exportRunId;
	}

	public Long getProductId() {
		return productId;
	}

	public LocalDateTime getExportedAt() {
		return exportedAt;
	}

	public Long getExportedBy() {
		return exportedBy;
	}
}
