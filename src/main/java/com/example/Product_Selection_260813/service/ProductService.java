package com.example.Product_Selection_260813.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.Product_Selection_260813.dto.request.ProductCreateRequest;
import com.example.Product_Selection_260813.dto.request.ProductUpdateRequest;
import com.example.Product_Selection_260813.dto.response.ProductResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.enums.ProductCandidateStatus;
import com.example.Product_Selection_260813.enums.ProductItemStatus;
import com.example.Product_Selection_260813.enums.ProductPricingStatus;
import com.example.Product_Selection_260813.enums.ProductPricingType;
import com.example.Product_Selection_260813.enums.ProductReviewStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ProductRepository;
import com.example.Product_Selection_260813.repository.ProductTypeRepository;
/**
 * 對應 API總表 三、品項管理（不含四、評估／趨勢／AI，那些屬於ScoringService／
 * TrendService／AiSelectionService的職責，見企劃書十二-13分層決議）：
 *
 * GET    /api/products                            -&gt; searchProducts()<br>
 * GET    /api/products/ai-suggested               -&gt; searchAiSuggested()<br>
 * POST   /api/products/{id}/promote-to-candidate  -&gt; promoteToCandidate()<br>
 * GET    /api/products/{id}                       -&gt; getProduct()<br>
 * POST   /api/products                            -&gt; createProduct()<br>
 * PUT    /api/products/{id}                       -&gt; updateProduct()<br>
 * DELETE /api/products/{id}                       -&gt; deleteProduct()<br>
 * POST   /api/products/{id}/resubmit              -&gt; resubmit()<br>
 * POST   /api/products/{id}/archive               -&gt; archive()<br>
 * POST   /api/products/{id}/restore               -&gt; restore()
 *
 * （POST /api/products/ai-suggested/batch-generate屬於系統排程專用，<br>
 * 由 AiSelectionService負責寫入AI_SUGGESTED商品，不在ProductService範圍內。）
 *
 * 例外處理沿用專案既有GlobalExceptionHandler慣例，不新增例外類別： - 資源不存在（商品／商品類型查無資料） -&gt;
 * IllegalArgumentException（400） - 目前狀態不允許此操作（狀態機不合法轉換、審核通過後改核心資料）-&gt;
 * IllegalStateException（409）
 */
@Service
public class ProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	@Value("${app.upload.dir}")
	private String uploadDir;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductTypeRepository productTypeRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private ScoringService scoringService;

	// ========================= 查詢 =========================

	/**
	 * 批次把一頁清單裡出現的 createdBy id 轉成姓名，一次查詢而非逐筆查詢。
	 *
	 * app_users 由管理層代辦建立、數量不多（見 UserController 類別註解：無公開
	 * 自我註冊），用 findAllById 一次撈回整批比每筆各查一次划算，不會產生 N+1。
	 * 這裡查的是使用者帳號本身的資料，不屬於評分／趨勢／AI 網域，不算跨越
	 * 十二-13分層決議劃定的邊界。
	 */
	private Map<Long, String> resolveCreatedByNames(List<Product> products) {
		Set<Long> ids = products.stream()
				.map(Product::getCreatedBy)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return Map.of();
		}
		return appUserRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(AppUser::getId, AppUser::getName));
	}


	/**
	 * GET /api/products：品項管理主清單。
	 *
	 * candidateStatus刻意在這裡（Service層）而非Repository層預設為CANDIDATE：
	 * 企劃書備註「候選狀態篩選器的實際作用範圍：主清單預設只顯示CANDIDATE的品項；
	 * 此篩選器保留是為了UI擴充彈性」——代表這是「業務預設值」而非「查詢一定只能這樣」，
	 * Repository.search()維持通用（null=不篩選），由呼叫端決定要不要套用預設值。
	 */
	@Transactional(readOnly = true)
	public Page<ProductResponse> searchProducts(ProductReviewStatus reviewStatus, ProductItemStatus itemStatus,
			ProductCandidateStatus candidateStatus, Long productTypeId, String keyword, Pageable pageable) {

		ProductCandidateStatus effectiveCandidateStatus = candidateStatus != null ? candidateStatus
				: ProductCandidateStatus.CANDIDATE;

		Page<Product> page = productRepository
				.search(reviewStatus, itemStatus, effectiveCandidateStatus, productTypeId, keyword, pageable);

		// 批次查一次 createdBy 對應的姓名，避免在 .map() 裡逐筆查詢（N+1）。
		Map<Long, String> createdByNameById = resolveCreatedByNames(page.getContent());
		return page.map(product -> ProductResponse.from(product)
				.withCreatedByName(createdByNameById.get(product.getCreatedBy())));
	}

	/**
	 * GET /api/products/ai-suggested：AI建議清單（candidate_status=AI_SUGGESTED）。
	 */
	@Transactional(readOnly = true)
	public Page<ProductResponse> searchAiSuggested(Pageable pageable) {
		Page<Product> page = productRepository.findByCandidateStatus(ProductCandidateStatus.AI_SUGGESTED, pageable);
		Map<Long, String> createdByNameById = resolveCreatedByNames(page.getContent());
		return page.map(product -> ProductResponse.from(product)
				.withCreatedByName(createdByNameById.get(product.getCreatedBy())));
	}

	/**
	 * GET /api/products/{id}：商品核心資料。
	 *
	 * 評估／趨勢／AI／風險等聚合資料不在這裡組裝，見類別註解。
	 */
	@Transactional(readOnly = true)
	public ProductResponse getProduct(Long id) {
		Product product = findProductOrThrow(id);
		String createdByName = product.getCreatedBy() == null ? null
				: appUserRepository.findById(product.getCreatedBy()).map(AppUser::getName).orElse(null);
		return ProductResponse.from(product).withCreatedByName(createdByName);
	}

	// ========================= 新增 =========================

	/**
	 * POST /api/products：手動建立品項。
	 *
	 * <b>刻意不檢查商品名稱是否重複（已與團隊確認，非疏漏）：</b>商品名稱天生就可能
	 * 合法重複——同名商品可能來自不同供應商、不同規格、不同批次進貨（例如
	 * 「A供應商的雞腿禮盒」與「B供應商的雞腿禮盒」）。若在這裡加唯一約束擋下，
	 * 會連帶擋掉這些合法情境，不是單純「防呆」而已。
	 *
	 * 真正該防的風險是「操作人員手滑重複送出同一筆」，這屬於**前端**該處理的問題
	 * （送出後禁用按鈕、debounce、或偵測到同名時跳出確認提示讓使用者自行判斷），
	 * 不該由後端用「一律禁止同名」這種對所有情境都生效的規則來處理——那等於用
	 * 治療症狀的方式，犧牲掉本來合法的使用情境。
	 *
	 * 若未來要調整這個決策（例如確認商品名稱在實務上就是唯一識別品項、同名一定
	 * 是誤操作），需要在這裡新增查詢檢查，並在資料表設計（五、products）補上
	 * 對應的DB層UNIQUE約束——兩者要一起做，不能只加其中一層。
	 *
	 * 預設值（不開放Request傳入，見ProductCreateRequest類別註解）：
	 * review_status=PENDING、item_status=ACTIVE、candidate_status=CANDIDATE
	 * pricing_status：NEW -&gt; PENDING_PRICING；RESALE -&gt; 留空（null）
	 */
	@Transactional
	public ProductResponse createProduct(ProductCreateRequest request, String username) {
		if (!productTypeRepository.existsById(request.getProductTypeId())) {
			throw new IllegalArgumentException("商品類型不存在");
		}
		validateMarketPriceOnlyForResale(request.getPricingType(), request.getMarketPrice());

		Long userId = resolveUserId(username);

		Product product = new Product();
		product.setProductTypeId(request.getProductTypeId());
		product.setPricingType(request.getPricingType());
		product.setName(request.getName());
		product.setDescription(request.getDescription());
		product.setImageUrl(request.getImageUrl());
		product.setSupplierName(request.getSupplierName());
		product.setCostPrice(request.getCostPrice());
		product.setSalePrice(request.getSalePrice());
		product.setMarketPrice(request.getMarketPrice());
		product.setCampaignTags(request.getCampaignTags());
		product.setMoq(request.getMoq());
		product.setSupplyStability(request.getSupplyStability());
		product.setPriceCompetitiveness(request.getPriceCompetitiveness());
		product.setTargetCustomerDescription(request.getTargetCustomerDescription());
		product.setEstimatedPurchaseRate(request.getEstimatedPurchaseRate());

		// review_status／item_status／candidate_status：Entity欄位預設值已經是
		// PENDING／ACTIVE／CANDIDATE（見Product.java），這裡不重複賦值。

		product.setPricingStatus(
				request.getPricingType() == ProductPricingType.NEW ? ProductPricingStatus.PENDING_PRICING : null);

		product.setCreatedBy(userId);
		product.setUpdatedBy(userId);

		Product saved = productRepository.save(product);

		// Demo緊急補上的觸發點（見ScoringService類別Java Doc）：新增成功後立即
		// 重算評估分數，讓品項詳情頁一建立就有分數可看，不用等使用者手動觸發其他動作。
		scoringService.calculateEvaluation(saved.getId(), null);

		return ProductResponse.from(saved);
	}

	// ========================= 修改 =========================

	/**
	 * PUT /api/products/{id}：整份覆蓋更新（見ProductUpdateRequest類別註解）。
	 *
	 * 欄位分組鎖定（四-2）：review_status=APPROVED時，「選品核心資料」群組
	 * 若送來的值與目前值不同，直接409拒絕整次更新（不做「部分套用、部分忽略」， 那樣前端會搞不清楚哪些欄位實際生效）。
	 */
	@Transactional
	public ProductResponse updateProduct(Long id, ProductUpdateRequest request, String username) {
		Product product = findProductOrThrow(id);

		if (!productTypeRepository.existsById(request.getProductTypeId())) {
			throw new IllegalArgumentException("商品類型不存在");
		}
		validateMarketPriceOnlyForResale(request.getPricingType(), request.getMarketPrice());

		if (product.getReviewStatus() == ProductReviewStatus.APPROVED) {
			assertCoreDataUnchanged(product, request);
		}

		// 一般基本資料：任何審核狀態下都可改
		product.setName(request.getName());
		product.setDescription(request.getDescription());
		product.setImageUrl(request.getImageUrl());
		product.setSupplierName(request.getSupplierName());

		// 選品核心資料：若APPROVED，上面assertCoreDataUnchanged()已經保證這裡的值
		// 跟目前值相同（否則已經丟例外），直接寫入不會改變實質內容；
		// 若非APPROVED，直接以Request內容整份覆蓋。
		product.setProductTypeId(request.getProductTypeId());
		product.setPricingType(request.getPricingType());
		product.setCostPrice(request.getCostPrice());
		product.setSalePrice(request.getSalePrice());
		product.setMarketPrice(request.getMarketPrice());
		product.setCampaignTags(request.getCampaignTags());
		product.setMoq(request.getMoq());
		product.setSupplyStability(request.getSupplyStability());
		product.setPriceCompetitiveness(request.getPriceCompetitiveness());
		product.setTargetCustomerDescription(request.getTargetCustomerDescription());
		product.setEstimatedPurchaseRate(request.getEstimatedPurchaseRate());

		// pricing_status自動轉換規則（四-2備註）：僅NEW商品才有意義，
		// RESALE商品pricing_status固定為null，不受這段邏輯影響。
		if (product.getPricingType() == ProductPricingType.NEW && product.getCostPrice() != null
				&& product.getSalePrice() != null
				&& product.getPricingStatus() == ProductPricingStatus.PENDING_PRICING) {
			product.setPricingStatus(ProductPricingStatus.PRICED);
		}

		product.setUpdatedBy(resolveUserId(username));

		Product saved = productRepository.save(product);

		// Demo緊急補上的觸發點：編輯成功後重算，確保分數反映最新欄位內容
		// （例如成本價/售價變動會影響BUSINESS分項）。
		scoringService.calculateEvaluation(saved.getId(), null);

		return ProductResponse.from(saved);
	}

	// ========================= 狀態轉換 =========================

	/**
	 * POST /api/products/{id}/promote-to-candidate：AI_SUGGESTED -&gt; CANDIDATE。
	 */
	@Transactional
	public ProductResponse promoteToCandidate(Long id) {
		Product product = findProductOrThrow(id);

		if (product.getCandidateStatus() != ProductCandidateStatus.AI_SUGGESTED) {
			throw new IllegalStateException("僅AI建議（尚未加入候選）的商品可執行此操作");
		}

		product.setCandidateStatus(ProductCandidateStatus.CANDIDATE);
		return ProductResponse.from(productRepository.save(product));
	}

	/**
	 * POST /api/products/{id}/resubmit：REJECTED -&gt; PENDING，submission_count+1。
	 *
	 * review_status的轉換沿用ProductRepository既有的conditionalUpdateReviewStatus()
	 * （原本為POST /api/reviews的併發控制設計），這裡直接複用同一支條件式UPDATE：
	 * 語意完全吻合（僅在目前狀態等於預期狀態時才更新成功），不需要另外重寫一次。
	 * submission_count+1在條件式UPDATE確認轉換成功之後才執行——此時已經確保 「當下改成PENDING的人就是我」，不會有競態問題。
	 *
	 * 這裡額外補上item_status==ACTIVE的前置檢查（原本沒有）：若不擋，REJECTED+
	 * ARCHIVED商品可以被直接resubmit成PENDING+ARCHIVED，繞過剛補上的restore() 路徑，一樣會產生「不會出現在GET
	 * /api/reviews/pending、卻又不是APPROVED 無法restore」的孤兒狀態（見restore()方法註解）。加這道檢查後，
	 * REJECTED+ARCHIVED商品必須先restore()解封存，才能resubmit()， 狀態機不會再有繞過復用步驟的隱藏路徑。
	 */
	@Transactional
	public ProductResponse resubmit(Long id) {
		Product product = findProductOrThrow(id);

		if (product.getReviewStatus() != ProductReviewStatus.REJECTED) {
			throw new IllegalStateException("僅已審核拒絕的商品可重新送審");
		}
		if (product.getItemStatus() != ProductItemStatus.ACTIVE) {
			throw new IllegalStateException("商品目前已封存，請先復用後再重新送審");
		}

		int updated = productRepository.conditionalUpdateReviewStatus(id, ProductReviewStatus.REJECTED,
				ProductReviewStatus.PENDING);
		if (updated == 0) {
			// 兩個管理端剛好同時操作同一品項時才會發生（例如同時又被改了一次審核結果）
			throw new IllegalStateException("商品狀態已被異動，請重新整理後再試");
		}

		Product refreshed = findProductOrThrow(id); // clearAutomatically=true已清空Persistence Context，重查取得最新值
		refreshed.setSubmissionCount(refreshed.getSubmissionCount() + 1);
		return ProductResponse.from(productRepository.save(refreshed));
	}

	/**
	 * POST /api/products/{id}/archive：(APPROVED或REJECTED) 且 ACTIVE -&gt; ARCHIVED。
	 */
	@Transactional
	public ProductResponse archive(Long id) {
		Product product = findProductOrThrow(id);

		boolean reviewStatusAllowed = product.getReviewStatus() == ProductReviewStatus.APPROVED
				|| product.getReviewStatus() == ProductReviewStatus.REJECTED;
		if (!reviewStatusAllowed) {
			throw new IllegalStateException("僅已審核通過或已審核拒絕的商品可封存");
		}
		if (product.getItemStatus() != ProductItemStatus.ACTIVE) {
			throw new IllegalStateException("商品目前並非使用中，無法封存");
		}

		product.setItemStatus(ProductItemStatus.ARCHIVED);
		return ProductResponse.from(productRepository.save(product));
	}

	/**
	 * POST /api/products/{id}/restore：(APPROVED或REJECTED) 且 ARCHIVED -&gt; ACTIVE。
	 *
	 * 補上REJECTED+ARCHIVED的復用路徑（原企劃書字面只開放APPROVED+ARCHIVED）：
	 * 這裡只解封存，不動review_status——REJECTED維持REJECTED，操作人員復用後
	 * 若要繼續走選品流程，再透過既有resubmit()送審，狀態機不因為這次修改多一條 「復用時直接跳回PENDING」的例外路徑。
	 *
	 * 補這條路徑的原因：resubmit()目前不檢查item_status，REJECTED+ARCHIVED商品
	 * 原本就能被resubmit成「PENDING+ARCHIVED」，但GET /api/reviews/pending的
	 * 預設條件是「未審核＋使用中」，會讓這筆資料從審核佇列消失、卡在無法被任何 端點再次轉換狀態的孤兒狀態；補上這條restore路徑後，操作人員可以先復用
	 * （回到ACTIVE）再resubmit，走完整條正常狀態機，不會再產生孤兒資料。
	 */
	@Transactional
	public ProductResponse restore(Long id) {
		Product product = findProductOrThrow(id);

		boolean reviewStatusAllowed = product.getReviewStatus() == ProductReviewStatus.APPROVED
				|| product.getReviewStatus() == ProductReviewStatus.REJECTED;
		if (!reviewStatusAllowed) {
			throw new IllegalStateException("僅已審核通過或已審核拒絕的商品可復用");
		}
		if (product.getItemStatus() != ProductItemStatus.ARCHIVED) {
			throw new IllegalStateException("商品目前並非已封存，無法復用");
		}

		product.setItemStatus(ProductItemStatus.ACTIVE);
		return ProductResponse.from(productRepository.save(product));
	}

	// ========================= 刪除 =========================

	/**
	 * DELETE /api/products/{id}：僅限「未審核＋無正式審核紀錄」。
	 *
	 * review_status=PENDING 且 submission_count=0 這個組合等價於「無審核紀錄」：
	 * 狀態機只有PENDING-&gt;APPROVED/REJECTED（審核）、REJECTED-&gt;PENDING（resubmit，
	 * 且必定submission_count&gt;=1）兩條路徑會離開/回到PENDING，因此「PENDING且
	 * submission_count=0」只可能是「從未送審過」，不需要額外查review_records表
	 * （企劃書三、品項管理「刪除品項」備註原文即此推導）。
	 */
	@Transactional
	public void deleteProduct(Long id) {
		Product product = findProductOrThrow(id);

		boolean deletable = product.getReviewStatus() == ProductReviewStatus.PENDING
				&& product.getSubmissionCount() != null && product.getSubmissionCount() == 0;
		if (!deletable) {
			throw new IllegalStateException("僅未審核且尚未送審過的商品可刪除");
		}

		productRepository.delete(product);
	}

	/**
	 * POST /api/products/{id}/image：上傳／替換商品圖片。
	 *
	 * <b>驗證順序</b>：副檔名白名單（快速擋掉明顯不合法的情況）→ 用ImageIO實際解碼
	 * 內容確認真的是合法圖片（客戶端宣告的Content-Type可以造假，不能只信任它，
	 * 見安全性與實作複雜度討論）。檔案大小上限由Spring Boot內建的
	 * spring.servlet.multipart.max-file-size設定擋在更早的階段，不在此處重複檢查。
	 *
	 * <b>檔名一律由伺服器端產生</b>（UUID+驗證過的副檔名），不採用使用者上傳時附帶的
	 * 原始檔名——避免路徑穿越或覆蓋掉其他檔案的風險。
	 *
	 * <b>寫入順序</b>：先把新檔案存到硬碟 → 更新Product.imageUrl指向新檔 → 最後才
	 * 嘗試刪除舊檔案。任何一步中途失敗，最差情況只是多一個沒被清掉的孤兒檔案
	 * （無害，可事後清理），不會出現「資料庫指到一個已經不存在的檔案」這種
	 * 更糟的狀態——這是刻意的順序選擇，不是隨意寫的。
	 *
	 * 刪除舊檔案採best-effort：找不到檔案或刪除失敗都只記錄，不影響本次上傳的
	 * 成功結果（新圖片已經生效才是使用者在意的事，舊檔案清理是次要的內務整理）。
	 */
	@Transactional
	public ProductResponse uploadImage(Long id, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("請選擇要上傳的圖片檔案");
		}

		String extension = extractValidatedExtension(file);
		validateActualImageContent(file);

		Product product = findProductOrThrow(id);
		String oldImageUrl = product.getImageUrl();

		String newFilename = UUID.randomUUID() + "." + extension;
		Path targetPath = resolveUploadDir().resolve(newFilename);
		try {
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("圖片儲存失敗，請稍後再試");
		}

		product.setImageUrl("/images/products/" + newFilename);
		Product saved = productRepository.save(product);

		deleteOldImageBestEffort(oldImageUrl);

		return ProductResponse.from(saved);
	}

	private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

	private String extractValidatedExtension(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		String extension = (originalFilename != null && originalFilename.contains("."))
				? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
				: "";
		if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("僅支援 jpg／jpeg／png／webp 格式的圖片");
		}
		return extension;
	}

	private void validateActualImageContent(MultipartFile file) {
		try {
			BufferedImage image = ImageIO.read(file.getInputStream());
			if (image == null) {
				throw new IllegalArgumentException("檔案內容不是合法的圖片格式");
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("檔案內容不是合法的圖片格式");
		}
	}

	private Path resolveUploadDir() {
		Path dir = Paths.get(uploadDir);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new IllegalStateException("圖片儲存目錄無法建立，請確認伺服器設定");
		}
		return dir;
	}

	private void deleteOldImageBestEffort(String oldImageUrl) {
		if (oldImageUrl == null || oldImageUrl.isBlank()) {
			return;
		}
		String oldFilename = oldImageUrl.substring(oldImageUrl.lastIndexOf('/') + 1);
		try {
			Files.deleteIfExists(resolveUploadDir().resolve(oldFilename));
		} catch (IOException e) {
			log.warn("舊圖片檔案刪除失敗，將形成孤兒檔案，不影響本次上傳結果：{}", oldFilename);
		}
	}

	// ========================= 內部輔助方法 =========================

	private Product findProductOrThrow(Long id) {
		return productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
	}

	/** username -&gt; app_users.id；沿用AuthService.getCurrentUser()同樣的重查邏輯與理由。 */
	private Long resolveUserId(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		return user.getId();
	}

	/**
	 * market_price僅RESALE商品填寫，NEW商品不適用（十四-1）； NEW商品若帶了market_price視為請求格式錯誤，而非靜默清空——
	 * 靜默清空會讓前端誤以為送出的值有生效。
	 */
	private void validateMarketPriceOnlyForResale(ProductPricingType pricingType, BigDecimal marketPrice) {
		if (pricingType == ProductPricingType.NEW && marketPrice != null) {
			throw new IllegalArgumentException("市售價格僅適用於再販售(RESALE)商品");
		}
	}

	/**
	 * 已審核通過(APPROVED)商品的「選品核心資料」欄位群組比對： 只要有任一欄位與目前值不同就整批拒絕（見updateProduct()方法註解）。
	 */
	private void assertCoreDataUnchanged(Product current, ProductUpdateRequest request) {
		boolean unchanged = Objects.equals(current.getProductTypeId(), request.getProductTypeId())
				&& current.getPricingType() == request.getPricingType()
				&& bigDecimalEquals(current.getCostPrice(), request.getCostPrice())
				&& bigDecimalEquals(current.getSalePrice(), request.getSalePrice())
				&& Objects.equals(current.getCampaignTags(), request.getCampaignTags())
				&& Objects.equals(current.getMoq(), request.getMoq())
				&& bigDecimalEquals(current.getSupplyStability(), request.getSupplyStability())
				&& bigDecimalEquals(current.getPriceCompetitiveness(), request.getPriceCompetitiveness())
				&& Objects.equals(current.getTargetCustomerDescription(), request.getTargetCustomerDescription())
				&& bigDecimalEquals(current.getEstimatedPurchaseRate(), request.getEstimatedPurchaseRate());

		if (!unchanged) {
			throw new IllegalStateException("商品已審核通過，選品核心資料禁止修改");
		}
	}

	/** BigDecimal不能直接用equals比較（scale不同時會誤判不相等，例如25跟25.00），一律用compareTo。 */
	private boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.compareTo(b) == 0;
	}
}