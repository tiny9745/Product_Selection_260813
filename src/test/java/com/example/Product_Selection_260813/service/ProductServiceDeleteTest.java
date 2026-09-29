package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.AiAnalysisRepository;
import com.example.Product_Selection_260813.repository.GoogleTrendSignalRepository;
import com.example.Product_Selection_260813.repository.GroupBuyRecordRepository;
import com.example.Product_Selection_260813.repository.ProductEvaluationRepository;
import com.example.Product_Selection_260813.repository.ProductExportLogRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.TrendSignalRepository;

/**
 * ProductService.deleteProduct() 的參照處理（2026-09-29 fk_gbr_product 根源修正）。
 *
 * 重點不是「刪得掉」，而是每一種參照都走到它該走的路：業務／稽核資料擋下回 409、
 * 歷史開團紀錄解除連結保留、衍生資料隨商品刪除。擋下時不可以先動到任何資料。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceDeleteTest {

	private static final Long PRODUCT_ID = 42L;

	@Mock
	private ProductRepository productRepository;
	@Mock
	private ReviewRecordRepository reviewRecordRepository;
	@Mock
	private ProductExportLogRepository productExportLogRepository;
	@Mock
	private GroupBuyRecordRepository groupBuyRecordRepository;
	@Mock
	private AiAnalysisRepository aiAnalysisRepository;
	@Mock
	private ProductEvaluationRepository productEvaluationRepository;
	@Mock
	private TrendSignalRepository trendSignalRepository;
	@Mock
	private GoogleTrendSignalRepository googleTrendSignalRepository;

	@InjectMocks
	private ProductService productService;

	private Product pendingFirstSubmission() {
		Product product = new Product();
		product.setId(PRODUCT_ID);
		product.setName("測試商品");
		product.setReviewStatus(ProductReviewStatus.PENDING);
		product.setSubmissionCount(1);
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		return product;
	}

	@Test
	void unlinksGroupBuyRecordsAndDeletesDerivedData() {
		Product product = pendingFirstSubmission();
		when(productRepository.findByResaleReferenceProductId(PRODUCT_ID)).thenReturn(List.of());
		when(productEvaluationRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());
		when(groupBuyRecordRepository.unlinkProduct(PRODUCT_ID)).thenReturn(2);

		productService.deleteProduct(PRODUCT_ID);

		verify(groupBuyRecordRepository).unlinkProduct(PRODUCT_ID);
		verify(aiAnalysisRepository).deleteByProductId(PRODUCT_ID);
		verify(trendSignalRepository).deleteByProductId(PRODUCT_ID);
		verify(googleTrendSignalRepository).deleteByProductId(PRODUCT_ID);
		verify(productRepository).delete(product);
	}

	@Test
	void rejectsWhenReferencedAsResaleReference() {
		pendingFirstSubmission();
		Product other = new Product();
		other.setId(7L);
		other.setName("再販售商品A");
		when(productRepository.findByResaleReferenceProductId(PRODUCT_ID)).thenReturn(List.of(other));

		assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("「再販售商品A」")
				.hasMessageContaining("再販售參考商品");

		assertNothingTouched();
	}

	@Test
	void rejectsWhenExportLogExists() {
		pendingFirstSubmission();
		when(productExportLogRepository.existsByProductId(PRODUCT_ID)).thenReturn(true);

		assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("匯出紀錄");

		assertNothingTouched();
	}

	@Test
	void rejectsWhenReviewRecordExists() {
		pendingFirstSubmission();
		when(reviewRecordRepository.existsByProductId(PRODUCT_ID)).thenReturn(true);

		assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("審核紀錄");

		assertNothingTouched();
	}

	private void assertNothingTouched() {
		verify(groupBuyRecordRepository, never()).unlinkProduct(anyLong());
		verify(aiAnalysisRepository, never()).deleteByProductId(anyLong());
		verify(productRepository, never()).delete(any(Product.class));
	}
}
