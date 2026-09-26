package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.Product_Selection_260813.dto.request.ProductFilterRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductExportLog;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductExportLogRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductSearchCriteria;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;

/** 審核通過商品 CSV 匯出：固定 APPROVED、全量不分頁、同一交易寫入匯出紀錄、數字取審核快照。 */
@ExtendWith(MockitoExtension.class)
class ProductExportServiceTest {

	@Mock
	private ProductRepository productRepository;
	@Mock
	private ReviewRecordRepository reviewRecordRepository;
	@Mock
	private ProductTypeRepository productTypeRepository;
	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private ProductExportLogRepository productExportLogRepository;

	private ProductExportService exportService;
	private AppUser exporter;

	@BeforeEach
	void setUp() {
		exportService = new ProductExportService(new ProductService(), productRepository, reviewRecordRepository,
				productTypeRepository, appUserRepository, productExportLogRepository);
		exporter = new AppUser();
		exporter.setId(7L);
		exporter.setName("陳小姐");
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(exporter));
	}

	private Product product(long id, String name) {
		Product product = new Product();
		product.setId(id);
		product.setName(name);
		product.setSupplierName("好物供應");
		product.setSalePrice(new BigDecimal("400"));
		product.setCostPrice(new BigDecimal("300"));
		return product;
	}

	private ReviewRecord approvedRecord(long productId, String finalScore, LocalDateTime reviewedAt) {
		ReviewRecord record = new ReviewRecord();
		record.setProductId(productId);
		record.setReviewerId(9L);
		record.setReviewedAt(reviewedAt);
		record.setFinalScoreSnapshot(new BigDecimal(finalScore));
		return record;
	}

	@Test
	void 固定只匯出審核通過_不分頁_並在同一交易寫入匯出紀錄() {
		ProductFilterRequest filter = new ProductFilterRequest();
		filter.setReviewStatus(ProductReviewStatus.PENDING); // 前端送什麼都會被覆寫
		when(productRepository.search(any(ProductSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(product(1L, "中秋禮盒"), product(2L, "=HYPERLINK(\"x\")"))));
		when(reviewRecordRepository.findByProductIdIn(any())).thenReturn(List.of(
				approvedRecord(1L, "82.50", LocalDateTime.of(2026, 9, 20, 10, 0)),
				approvedRecord(2L, "70", LocalDateTime.of(2026, 9, 21, 9, 30))));
		when(productTypeRepository.findAllById(any())).thenReturn(Collections.emptyList());
		when(appUserRepository.findAllById(any())).thenReturn(Collections.emptyList());

		ProductExportService.ExportResult result = exportService.exportApproved(filter, "buyer01");

		ArgumentCaptor<ProductSearchCriteria> criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
		verify(productRepository).search(criteria.capture(), any(Pageable.class));
		assertThat(criteria.getValue().reviewStatus()).isEqualTo(ProductReviewStatus.APPROVED);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ProductExportLog>> logs = ArgumentCaptor.forClass((Class<List<ProductExportLog>>) (Class<?>) List.class);
		verify(productExportLogRepository).saveAll(logs.capture());
		assertThat(logs.getValue().size()).isEqualTo(2);

		assertThat(result.rowCount()).isEqualTo(2);
		String csv = new String(result.content(), StandardCharsets.UTF_8);
		assertThat(csv.startsWith("\uFEFF商品編號,商品名稱")).isTrue();
		// 審核時間新→舊：商品 2（9/21）在商品 1（9/20）之前。
		assertThat(csv.indexOf("\r\n2,")).isEqualTo(csv.indexOf("\r\n") );
		// 最終分數取審核快照；毛利率 (400−300)/400 = 25.0%。
		assertThat(csv.contains(",82.5,")).isTrue();
		assertThat(csv.contains(",25,")).isTrue();
		// 公式注入防護：以 = 開頭的商品名稱前面補單引號，且含雙引號的欄位依 RFC 4180 跳脫。
		assertThat(csv.contains("\"'=HYPERLINK(\"\"x\"\")\"")).isTrue();
	}

	@Test
	void 沒有符合條件時只回表頭且不寫匯出紀錄() {
		when(productRepository.search(any(ProductSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		ProductExportService.ExportResult result = exportService.exportApproved(null, "buyer01");

		assertThat(result.rowCount()).isEqualTo(0);
		verify(productExportLogRepository, never()).saveAll(any());
	}

	@Test
	void 超過同步匯出上限時拒絕() {
		List<Product> many = new ArrayList<>();
		for (long i = 0; i <= ProductExportService.MAX_EXPORT_ROWS; i++) {
			many.add(product(i, "p" + i));
		}
		when(productRepository.search(any(ProductSearchCriteria.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(many));

		assertThatThrownBy(() -> exportService.exportApproved(null, "buyer01")).isInstanceOf(IllegalStateException.class);
		verify(productExportLogRepository, never()).saveAll(any());
	}

}
