package com.example.Product_Selection_260813.service.resolver;

import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.ProductType;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;

/**
 * 品類屬性繼承解析：小類的值優先，小類為 null 時往上取大類的值。
 *
 * 這是本次擴充的<b>地基方法</b>——MOQ 三層解析、效期門檻、Gate 判定、商品表單的
 * 預設值提示都共用這個類別，因此獨立成 Component 而不是塞進某個既有 Service。
 *
 * null 的語意是「繼承上層」而非「未設定」。這個區別很重要：商品層 moq=null
 * 代表要用品類預設，moq=0 代表明確表示無最低量限制，兩者不能混為一談。
 */
@Component
public class ProductTypeAttributeResolver {

	private final ProductTypeRepository productTypeRepository;

	@Autowired
	public ProductTypeAttributeResolver(ProductTypeRepository productTypeRepository) {
		this.productTypeRepository = productTypeRepository;
	}

	/**
	 * 解析指定品類的完整屬性。
	 *
	 * @param productTypeId 商品掛載的品類 id（依驗證規則應為小類，但方法本身容忍大類）
	 */
	public ResolvedProductTypeAttributes resolve(Long productTypeId) {
		if (productTypeId == null) {
			return emptyResult(null, null);
		}
		Optional<ProductType> leafOpt = productTypeRepository.findById(productTypeId);
		if (leafOpt.isEmpty()) {
			return emptyResult(productTypeId, null);
		}

		ProductType leaf = leafOpt.get();
		// 大類：leaf 本身即為根；小類：往上查一層。兩層固定深度，不需要迴圈。
		ProductType root = leaf.getParentId() == null
				? leaf
				: productTypeRepository.findById(leaf.getParentId()).orElse(leaf);

		return new ResolvedProductTypeAttributes(
				leaf.getId(),
				root.getId(),
				inherit(leaf, root, ProductType::getDefaultTemperatureZone),
				inherit(leaf, root, ProductType::getHasShelfLife),
				inherit(leaf, root, ProductType::getDefaultShelfLifeTier),
				inherit(leaf, root, ProductType::getReturnPolicy),
				inherit(leaf, root, ProductType::getShelfLifeThresholdDays),
				inherit(leaf, root, ProductType::getDefaultMoq),
				inherit(leaf, root, ProductType::getRequiredCertification),
				// 評估模式僅在大類設定有意義，直接取根層，不做小類覆寫
				root.getDefaultEvaluationModeId() != null
						? ResolvedValue.ofProductType(root.getDefaultEvaluationModeId())
						: ResolvedValue.none());
	}

	/** 取得該品類的大類 id；本身就是大類時回傳自己。 */
	public Long resolveRootTypeId(Long productTypeId) {
		if (productTypeId == null) {
			return null;
		}
		return productTypeRepository.findById(productTypeId)
				.map(t -> t.getParentId() == null ? t.getId() : t.getParentId())
				.orElse(null);
	}

	/**
	 * 小類優先、大類次之的繼承取值。
	 *
	 * 小類與大類是同一筆時（商品直接掛大類）只會取到一次，不會誤判來源層級——
	 * 兩者都標示為 PRODUCT_TYPE，對呼叫端而言語意一致。
	 */
	private <T> ResolvedValue<T> inherit(ProductType leaf, ProductType root, Function<ProductType, T> getter) {
		T leafValue = getter.apply(leaf);
		if (leafValue != null) {
			return ResolvedValue.ofProductType(leafValue);
		}
		T rootValue = getter.apply(root);
		if (rootValue != null) {
			return ResolvedValue.ofProductType(rootValue);
		}
		return ResolvedValue.none();
	}

	private ResolvedProductTypeAttributes emptyResult(Long leafId, Long rootId) {
		return new ResolvedProductTypeAttributes(leafId, rootId,
				ResolvedValue.none(), ResolvedValue.none(), ResolvedValue.none(), ResolvedValue.none(),
				ResolvedValue.none(), ResolvedValue.none(), ResolvedValue.none(), ResolvedValue.none());
	}
}
