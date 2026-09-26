package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.constants.BusinessTimeZone;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.PasswordResetRequest;
import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.PasswordResetRequestRepository;

/**
 * 重設密碼申請（V27，2026-09-26 決議）：管理者只能重設「本人已申請」的帳號。
 *
 * <b>申請（apply，不需登入）</b>：忘記密碼的人本來就無法登入，所以入口在登入頁。
 * 這支 API 對外公開，因此：
 * <ul>
 * <li>不論帳號是否存在、是否停用、是否已有申請，呼叫端一律得到同一個結果（Controller 回同一段
 * 訊息），避免被用來試出系統有哪些帳號。</li>
 * <li>同一帳號同時只保留一筆待處理申請，重複送出不會新增，資料表不會被灌爆。</li>
 * <li>已停用的帳號不建立申請：停用帳號重設密碼也無法登入，應先由管理者判斷是否復用。</li>
 * </ul>
 *
 * <b>結案</b>：由 UserService.resetPassword()（COMPLETED）與 rejectPasswordResetRequest()（REJECTED）
 * 呼叫 {@link #closePending}，同一筆交易內完成，重設成功就一定結案。
 */
@Service
public class PasswordResetRequestService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestService.class);

	private final PasswordResetRequestRepository requestRepository;
	private final AppUserRepository appUserRepository;

	public PasswordResetRequestService(PasswordResetRequestRepository requestRepository,
			AppUserRepository appUserRepository) {
		this.requestRepository = requestRepository;
		this.appUserRepository = appUserRepository;
	}

	/**
	 * POST /api/auth/password-reset-requests：申請重設密碼。刻意沒有回傳值（理由見類別說明）。
	 * 帳號比對大小寫與登入相同（依資料庫定序），前後空白去除。
	 */
	@Transactional
	public void apply(String username) {
		String trimmed = username == null ? "" : username.trim();
		AppUser user = appUserRepository.findByUsername(trimmed).orElse(null);
		if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
			// 不存在或已停用：靜默忽略。只記 debug，不記帳號內容以免日誌變成帳號探測的紀錄。
			log.debug("忽略一筆無效的重設密碼申請");
			return;
		}
		if (!requestRepository.findByUserIdAndStatus(user.getId(), PasswordResetRequestStatus.PENDING).isEmpty()) {
			return; // 已有待處理申請：不重複建立
		}
		requestRepository.save(new PasswordResetRequest(user.getId(), LocalDateTime.now(BusinessTimeZone.TAIPEI)));
	}

	/** 帳號管理清單用：userId → 最早一筆待處理申請的申請時間（一次查詢）。 */
	@Transactional(readOnly = true)
	public Map<Long, LocalDateTime> pendingRequestedAtByUser() {
		BinaryOperator<LocalDateTime> earliest = (a, b) -> a.isBefore(b) ? a : b;
		return requestRepository.findByStatus(PasswordResetRequestStatus.PENDING).stream()
				.collect(Collectors.toMap(PasswordResetRequest::getUserId, PasswordResetRequest::getRequestedAt, earliest));
	}

	/**
	 * 結案該帳號所有待處理申請（正常只有一筆）。沒有待處理申請時丟 IllegalStateException（409）：
	 * 這就是「沒有申請不能重設」的後端防線，前端停用按鈕只是使用體驗。
	 *
	 * @return 被結案的申請中最早的申請時間（供回應訊息使用）
	 */
	@Transactional
	public LocalDateTime closePending(Long userId, PasswordResetRequestStatus result, Long managerId) {
		List<PasswordResetRequest> pending = requestRepository.findByUserIdAndStatus(userId,
				PasswordResetRequestStatus.PENDING);
		if (pending.isEmpty()) {
			throw new IllegalStateException("此帳號沒有待處理的重設密碼申請，請先由使用者在登入頁提出申請");
		}
		LocalDateTime now = LocalDateTime.now(BusinessTimeZone.TAIPEI);
		pending.forEach(request -> request.close(result, managerId, now));
		requestRepository.saveAll(pending);
		return pending.stream().map(PasswordResetRequest::getRequestedAt).min(Comparator.naturalOrder()).orElse(now);
	}
}
