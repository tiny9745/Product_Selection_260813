package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.dto.request.ProductCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductUpdateRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductCustomFieldValueRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.service.discovery.DiscoveredItemService;

/**
 * 2026-09-30 根源修正：再販售參考商品不可為「未審核（PENDING）」的品項。
 *
 * 實例：#139「台灣豬五花禮盒」（RESALE，已審核通過）引用 #122「台灣豬五花禮盒」
 * （NEW，未審核）→ #122 被刪除檢查擋下，而 #139 審核通過後參考商品鎖定無法修改，
 * 形成永遠刪不掉的死結。
 *
 * - 新增時引用未審核品項：擋下（400）。
 * - 編輯時「改成」引用未審核品項：擋下。
 * - 編輯時參考商品沒變（修正前就存在的舊引用）：不擋，其他欄位仍可正常編輯。
 * - 引用已審核（通過或拒絕）的品項：正常。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceResaleReferenceTest {

	private static final Long PRODUCT_TYPE_ID = 2L;
	private static final Long PENDING_REFERENCE_ID = 122L;
	private static final Long REVIEWED_REFERENCE_ID = 93L;
	private static final Long SELF_ID = 139L;

	@Mock
	private ProductRepository productRepository;
	@Mock
	private ProductTypeRepository productTypeRepository;
	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private ScoringService scoringService;
	@Mock
	private SettingsService settingsService;
	@Mock
	private DiscoveredItemService discoveredItemService;
	@Mock
	private ProductCustomFieldValueRepository productCustomFieldValueRepository;

	@InjectMocks
	private ProductService productService;

	@BeforeEach
	void commonStubs() {
		AppUser user = new AppUser();
		user.setId(3L);
		lenient().when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user));
		lenient().when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product product = invocation.getArgument(0);
			if (product.getId() == null) {
				product.setId(200L);
			}
			return product;
		});
		lenient().when(productRepository.findById(PENDING_REFERENCE_ID))
				.thenReturn(Optional.of(reference(PENDING_REFERENCE_ID, ProductReviewStatus.PENDING)));
		lenient().when(productRepository.findById(REVIEWED_REFERENCE_ID))
				.thenReturn(Optional.of(reference(REVIEWED_REFERENCE_ID, ProductReviewStatus.REJECTED)));
	}

	private static Product reference(Long id, ProductReviewStatus status) {
		Product product = new Product();
		product.setId(id);
		product.setName("台灣豬五花禮盒");
		product.setProductTypeId(PRODUCT_TYPE_ID);
		product.setReviewStatus(status);
		return product;
	}

	private void givenActiveProductType() {
		ProductType type = new ProductType();
		type.setId(PRODUCT_TYPE_ID);
		type.setIsActive(true);
		when(productTypeRepository.findById(PRODUCT_TYPE_ID)).thenReturn(Optional.of(type));
	}

	private static ProductCreateRequest resaleCreateRequest(Long referenceId) {
		ProductCreateRequest request = new ProductCreateRequest();
		request.setProductTypeId(PRODUCT_TYPE_ID);
		request.setPricingType(ProductPricingType.RESALE);
		request.setName("台灣豬五花禮盒");
		request.setCostPrice(new BigDecimal("600"));
		request.setSalePrice(new BigDecimal("899"));
		request.setResaleReferenceProductId(referenceId);
		return request;
	}

	private static ProductUpdateRequest resaleUpdateRequest(Long referenceId) {
		ProductUpdateRequest request = new ProductUpdateRequest();
		request.setProductTypeId(PRODUCT_TYPE_ID);
		request.setPricingType(ProductPricingType.RESALE);
		request.setName("台灣豬五花禮盒（新版名稱）");
		request.setCostPrice(new BigDecimal("600"));
		request.setSalePrice(new BigDecimal("899"));
		request.setResaleReferenceProductId(referenceId);
		return request;
	}

	private void givenExistingResale(Long currentReferenceId) {
		Product existing = reference(SELF_ID, ProductReviewStatus.PENDING);
		existing.setPricingType(ProductPricingType.RESALE);
		existing.setResaleReferenceProductId(currentReferenceId);
		when(productRepository.findById(SELF_ID)).thenReturn(Optional.of(existing));
		when(productTypeRepository.existsById(PRODUCT_TYPE_ID)).thenReturn(true);
	}

	@Test
	void 新增_引用未審核品項_擋下且不寫入() {
		givenActiveProductType();

		assertThatThrownBy(() -> productService.createProduct(resaleCreateRequest(PENDING_REFERENCE_ID), "buyer01"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("尚未審核");
		verify(productRepository, never()).save(any(Product.class));
	}

	@Test
	void 新增_引用已審核品項_正常建立() {
		givenActiveProductType();

		assertThat(productService.createProduct(resaleCreateRequest(REVIEWED_REFERENCE_ID), "buyer01")
				.getResaleReferenceProductId()).isEqualTo(REVIEWED_REFERENCE_ID);
	}

	@Test
	void 編輯_改成引用未審核品項_擋下() {
		givenExistingResale(null);

		assertThatThrownBy(() -> productService.updateProduct(SELF_ID, resaleUpdateRequest(PENDING_REFERENCE_ID),
				"buyer01"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("尚未審核");
	}

	@Test
	void 編輯_既有的舊引用沒有改變_其他欄位照常可改() {
		givenExistingResale(PENDING_REFERENCE_ID);

		assertThat(productService.updateProduct(SELF_ID, resaleUpdateRequest(PENDING_REFERENCE_ID), "buyer01")
				.getName()).isEqualTo("台灣豬五花禮盒（新版名稱）");
	}
}
