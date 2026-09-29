package com.example.Product_Selection_260813.service.discovery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.response.DiscoveredItemResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.entity.DiscoveredItemEvidence;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemEvidenceRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemRepository;
import com.example.Product_Selection_260813.service.crawler.PttSelectors;

/**
 * PTT 新品探索結果的查詢與人工處理（略過／復原／轉成商品）。
 *
 * <b>轉成商品</b>不是這裡建立 Product：商品一律走既有的 POST /api/products（欄位驗證、
 * 送審批次、評分都在 ProductService），前端在請求裡帶 discoveredItemId，ProductService
 * 建立成功後在同一個交易裡呼叫 {@link #markConverted}——商品建立失敗就不會標記，
 * 標記失敗（例如已被別人轉過）商品也不會建立，兩者一致。
 */
@Service
public class DiscoveredItemService {

	/** 待處理清單預設只列最近這麼多天內還有被提及的項目，舊話題自然沉下去。 */
	public static final int DEFAULT_RECENT_DAYS = 14;

	private static final int MAX_PAGE_SIZE = 50;

	/** 清單排序（第二階段）：FIT＝適配度（預設）、BUZZ＝熱度、RECENT＝最後出現。 */
	public enum Sort {
		FIT, BUZZ, RECENT
	}

	@Autowired
	private DiscoveredItemRepository discoveredItemRepository;

	@Autowired
	private DiscoveredItemEvidenceRepository discoveredItemEvidenceRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	/**
	 * @param recentDays 只看最近幾天內仍被提及的項目；null 時 NEW 預設 {@value #DEFAULT_RECENT_DAYS} 天，
	 *                   DISMISSED／CONVERTED 不限（那兩種是處理紀錄，不該因為話題退燒就看不到）；0＝不限
	 */
	@Transactional(readOnly = true)
	public Page<DiscoveredItemResponse> search(DiscoveredItemStatus status, Integer recentDays, Sort sort, int page,
			int size) {
		DiscoveredItemStatus effectiveStatus = status != null ? status : DiscoveredItemStatus.NEW;
		int days = recentDays != null ? recentDays
				: effectiveStatus == DiscoveredItemStatus.NEW ? DEFAULT_RECENT_DAYS : 0;
		if (days < 0) {
			throw new IllegalArgumentException("recentDays 不可為負數");
		}
		LocalDateTime seenSince = days == 0 ? null : LocalDateTime.now().minusDays(days);
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<DiscoveredItem> items = switch (sort == null ? Sort.FIT : sort) {
			case FIT -> discoveredItemRepository.searchByFit(effectiveStatus, seenSince, pageable);
			case BUZZ -> discoveredItemRepository.search(effectiveStatus, seenSince, pageable);
			case RECENT -> discoveredItemRepository.searchByRecent(effectiveStatus, seenSince, pageable);
		};

		// 佐證文章與處理人姓名都整頁批次查一次，不逐筆查（N+1）。
		List<Long> ids = items.getContent().stream().map(DiscoveredItem::getId).toList();
		Map<Long, List<DiscoveredItemEvidence>> evidenceByItem = ids.isEmpty() ? Map.of()
				: discoveredItemEvidenceRepository.findByItemIdInOrderByPostedAtDesc(ids).stream()
						.collect(Collectors.groupingBy(DiscoveredItemEvidence::getItemId));
		Map<Long, String> userNames = appUserRepository.findAllById(items.getContent().stream()
				.map(DiscoveredItem::getHandledBy).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName, (a, b) -> a));

		return items.map(item -> DiscoveredItemResponse.from(item,
				item.getHandledBy() == null ? null : userNames.get(item.getHandledBy()),
				toEvidence(evidenceByItem.getOrDefault(item.getId(), List.of()))));
	}

	/**
	 * NEW → DISMISSED。其他狀態回 409，避免把已建立商品的項目改成略過。
	 * 原因代碼不帶時視為 OTHER（見 DiscoveredItemDismissRequest）。
	 */
	@Transactional
	public DiscoveredItemResponse dismiss(Long id, DiscoveryDismissReason reasonCode, String reason, String username) {
		DiscoveredItem item = find(id);
		if (item.getStatus() != DiscoveredItemStatus.NEW) {
			throw new IllegalStateException("只有待處理的項目可以略過（目前狀態：" + item.getStatus().getLabel() + "）");
		}
		item.setStatus(DiscoveredItemStatus.DISMISSED);
		item.setDismissReasonCode(reasonCode != null ? reasonCode : DiscoveryDismissReason.OTHER);
		item.setDismissReason(reason == null || reason.isBlank() ? null : reason.trim());
		item.setHandledBy(resolveUser(username).getId());
		item.setHandledAt(LocalDateTime.now());
		return toResponse(discoveredItemRepository.save(item));
	}

	/** DISMISSED → NEW（略過錯了可以復原）。 */
	@Transactional
	public DiscoveredItemResponse restore(Long id) {
		DiscoveredItem item = find(id);
		if (item.getStatus() != DiscoveredItemStatus.DISMISSED) {
			throw new IllegalStateException("只有已略過的項目可以復原（目前狀態：" + item.getStatus().getLabel() + "）");
		}
		item.setStatus(DiscoveredItemStatus.NEW);
		item.setDismissReasonCode(null);
		item.setDismissReason(null);
		item.setHandledBy(null);
		item.setHandledAt(null);
		return toResponse(discoveredItemRepository.save(item));
	}

	/**
	 * 由 ProductService.createProduct() 在同一個交易內呼叫。已略過的項目也允許轉成商品
	 * （人工改變心意），已經轉過的回 409，避免同一個線索建立兩件商品。
	 *
	 * @throws IllegalArgumentException 項目不存在（400）
	 * @throws IllegalStateException    已建立過商品（409）
	 */
	@Transactional
	public void markConverted(Long id, Long productId, Long userId) {
		DiscoveredItem item = find(id);
		if (item.getStatus() == DiscoveredItemStatus.CONVERTED) {
			throw new IllegalStateException("這個探索項目已經建立過商品（商品 ID " + item.getConvertedProductId() + "）");
		}
		item.setStatus(DiscoveredItemStatus.CONVERTED);
		item.setConvertedProductId(productId);
		item.setHandledBy(userId);
		item.setHandledAt(LocalDateTime.now());
		discoveredItemRepository.save(item);
	}

	private DiscoveredItem find(Long id) {
		return discoveredItemRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("探索項目不存在：" + id));
	}

	private AppUser resolveUser(String username) {
		return appUserRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
	}

	private DiscoveredItemResponse toResponse(DiscoveredItem item) {
		String handledByName = item.getHandledBy() == null ? null
				: appUserRepository.findById(item.getHandledBy()).map(AppUser::getName).orElse(null);
		List<DiscoveredItemEvidence> evidence = discoveredItemEvidenceRepository
				.findByItemIdInOrderByPostedAtDesc(List.of(item.getId()));
		return DiscoveredItemResponse.from(item, handledByName, toEvidence(evidence));
	}

	static List<DiscoveredItemResponse.Evidence> toEvidence(List<DiscoveredItemEvidence> rows) {
		List<DiscoveredItemResponse.Evidence> result = new ArrayList<>();
		for (DiscoveredItemEvidence row : rows) {
			if (result.size() >= DiscoveredItemResponse.MAX_EVIDENCE) {
				break;
			}
			result.add(new DiscoveredItemResponse.Evidence(row.getBoard(), PttSelectors.BASE_URL + row.getPostPath(),
					row.getTitle(), row.getPushVolume(), row.getPostedAt()));
		}
		return result;
	}
}
