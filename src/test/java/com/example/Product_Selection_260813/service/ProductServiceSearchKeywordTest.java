package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.dto.request.ProductCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductUpdateRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductCustomFieldValueRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
import com.example.Product_Selection_260813.service.discovery.DiscoveredItemService;

/**
 * 2026-09-30：商品「搜尋關鍵字」（V37 products.search_keyword）的建立與編輯規則。
 * - 使用者填的值去頭尾空白；空白字串視為未設定（null）。
 * - 從 AI 商品雷達建立、且使用者沒填時，帶入雷達 AI 抽出的關鍵字；使用者有填則以使用者為準。
 * - 屬一般基本資料，編輯時可以設定也可以清除。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceSearchKeywordTest {

	private static final Long PRODUCT_TYPE_ID = 7L;
	private static final Long USER_ID = 3L;

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
		user.setId(USER_ID);
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user));
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product product = invocation.getArgument(0);
			if (product.getId() == null) {
				product.setId(100L);
			}
			return product;
		});
	}

	private void givenActiveProductType() {
		ProductType type = new ProductType();
		type.setId(PRODUCT_TYPE_ID);
		type.setIsActive(true);
		when(productTypeRepository.findById(PRODUCT_TYPE_ID)).thenReturn(Optional.of(type));
	}

	private static ProductCreateRequest createRequest(String name, String searchKeyword, Long discoveredItemId) {
		ProductCreateRequest request = new ProductCreateRequest();
		request.setProductTypeId(PRODUCT_TYPE_ID);
		request.setPricingType(ProductPricingType.NEW);
		request.setName(name);
		request.setSearchKeyword(searchKeyword);
		request.setTargetCustomerDescription("家庭");
		request.setDiscoveredItemId(discoveredItemId);
		return request;
	}

	private static DiscoveredItem radarItem(String searchKeyword) {
		DiscoveredItem item = new DiscoveredItem();
		item.setId(5L);
		item.setSearchKeyword(searchKeyword);
		return item;
	}

	@Test
	void 從雷達建立且沒填關鍵字_帶入雷達抽出的關鍵字() {
		givenActiveProductType();
		when(discoveredItemService.markConverted(5L, 100L, USER_ID)).thenReturn(radarItem(" 文旦 "));

		ProductResponse created = productService.createProduct(createRequest("麻豆文旦 10台斤禮盒", "  ", 5L), "buyer01");

		assertThat(created.getSearchKeyword()).isEqualTo("文旦");
	}

	@Test
	void 從雷達建立但使用者有填關鍵字_以使用者為準() {
		givenActiveProductType();
		when(discoveredItemService.markConverted(5L, 100L, USER_ID)).thenReturn(radarItem("文旦"));

		ProductResponse created = productService.createProduct(createRequest("麻豆文旦 10台斤禮盒", " 麻豆文旦 ", 5L),
				"buyer01");

		assertThat(created.getSearchKeyword()).isEqualTo("麻豆文旦");
	}

	@Test
	void 一般建立_空白關鍵字存成null_不呼叫雷達() {
		givenActiveProductType();

		ProductResponse created = productService.createProduct(createRequest("氣炸鍋 5L", "   ", null), "buyer01");

		assertThat(created.getSearchKeyword()).isNull();
		verify(discoveredItemService, never()).markConverted(any(), any(), any());
	}

	@Test
	void 編輯_可以設定也可以清除關鍵字() {
		Product existing = new Product();
		existing.setId(100L);
		existing.setProductTypeId(PRODUCT_TYPE_ID);
		existing.setPricingType(ProductPricingType.NEW);
		existing.setName("麻豆文旦 10台斤禮盒");
		when(productRepository.findById(100L)).thenReturn(Optional.of(existing));
		when(productTypeRepository.existsById(PRODUCT_TYPE_ID)).thenReturn(true);

		ProductUpdateRequest request = new ProductUpdateRequest();
		request.setProductTypeId(PRODUCT_TYPE_ID);
		request.setPricingType(ProductPricingType.NEW);
		request.setName("麻豆文旦 10台斤禮盒");
		request.setTargetCustomerDescription("家庭");
		request.setSearchKeyword(" 文旦 ");
		assertThat(productService.updateProduct(100L, request, "buyer01").getSearchKeyword()).isEqualTo("文旦");

		request.setSearchKeyword("");
		assertThat(productService.updateProduct(100L, request, "buyer01").getSearchKeyword()).isNull();
	}
}
