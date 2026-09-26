package com.example.Product_Selection_260813.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.dto.request.UserCreateRequest;
import com.example.Product_Selection_260813.dto.response.UserAccountResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;

/**
 * 對應 四、API總表「1-2. 帳號管理」五支端點（皆為[僅管理]）：
 * GET /api/users、POST /api/users、PUT /api/users/{id}/disable、
 * PUT /api/users/{id}/enable、PUT /api/users/{id}/reset-password（V24 新增）、
 * PUT /api/users/{id}/password-reset-request/reject（V27 新增）。
 *
 * <b>V27：重設密碼必須先有本人申請</b>（2026-09-26 決議）。管理者只能重設「有待處理申請」
 * 的帳號，重設即結案；也可以駁回申請。申請入口在登入頁（見 PasswordResetRequestService）。
 * 帳號疑似外洩等緊急情況不走申請，改用 disableUser()（停用會讓現有登入立即失效）。
 *
 * <b>與AuthService的職責分界</b>（七-5決議）：AuthService負責「驗證我是誰」
 * （登入、取得自身資料、登出），本類別負責「管理別人的帳號」（列出、新增、停用、復用）。
 * 兩者僅共用AppUser實體與app_users資料表，不共用商業邏輯。
 *
 * <b>帳號只停用不刪除</b>：app_users被review_records.reviewer_id、
 * products.created_by等欄位參照，實體刪除會使歷史稽核紀錄失去對應人員資料，
 * 故不提供DELETE端點（七-5決議）。因此被停用的帳號需要能重新啟用（見
 * enableUser()），否則管理層一旦誤停用帳號就沒有回復手段，只能改資料庫。
 */
@Service
public class UserService {

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PasswordResetRequestService passwordResetRequestService;

	/**
	 * GET /api/users：列出所有帳號（含已停用者）。
	 *
	 * 刻意不過濾enabled=false：這是管理視角的帳號清單，管理層需要看到
	 * 已停用的帳號才能確認「這個人確實已經被停用」，也才有機會發現
	 * 誤停用的情況；若隱藏已停用帳號，會變成停用後就從畫面上人間蒸發。
	 * 回應中的enabled欄位供前端以標籤或灰階樣式區分。
	 */
	@Transactional(readOnly = true)
	public List<UserAccountResponse> getAllUsers() {
		// V27：一次取出所有待處理的重設密碼申請，標在對應帳號上（不逐帳號查詢）。
		Map<Long, LocalDateTime> requestedAtByUser = passwordResetRequestService.pendingRequestedAtByUser();
		return appUserRepository.findAll().stream()
				.map(user -> UserAccountResponse.from(user).withPasswordResetRequestedAt(requestedAtByUser.get(user.getId())))
				.toList();
	}

	/**
	 * POST /api/users：由管理層代辦建立帳號。
	 *
	 * username唯一性在寫入前先檢查一次，給出明確的中文錯誤訊息；
	 * 資料庫層的uk_app_users_username唯一約束仍是最後防線（併發情況下
	 * 兩個請求可能同時通過檢查），屆時會由GlobalExceptionHandler的
	 * DataAccessException handler攔截成500——這是可接受的取捨：
	 * 「兩位管理員在同一瞬間建立同名帳號」極為罕見，為此加上重試或
	 * 悲觀鎖不符成本效益。
	 */
	@Transactional
	public UserAccountResponse createUser(UserCreateRequest request) {
		if (appUserRepository.existsByUsername(request.getUsername())) {
			throw new IllegalArgumentException("登入帳號已存在：" + request.getUsername());
		}

		AppUser user = new AppUser();
		user.setUsername(request.getUsername());
		user.setName(request.getName());
		user.setRole(request.getRole());
		// 建立帳號與代重設密碼共用同一套「設定管理者給的密碼＋強制下次登入修改」邏輯（V24），
		// 雜湊方式等說明見 assignManagerIssuedPassword()。
		assignManagerIssuedPassword(user, request.getPassword());

		AppUser saved = appUserRepository.save(user);
		return UserAccountResponse.from(saved);
	}

	/**
	 * PUT /api/users/{id}/disable：停用帳號。
	 *
	 * <b>禁止停用自己</b>：若允許，管理層可能在只剩一位管理員的情況下把自己停用，
	 * 導致系統再也沒有人能登入做帳號管理（本系統不開放自我註冊，也沒有
	 * 密碼重設／解鎖端點，屆時只能直接改資料庫才能救回）。這是實際會發生的
	 * 誤操作，成本極低就能防範，故在此擋下。
	 *
	 * 重複停用已停用的帳號不視為錯誤（冪等）：結果狀態與呼叫端的意圖一致，
	 * 沒有理由回報失敗。
	 */
	@Transactional
	public UserAccountResponse disableUser(Long id, String currentUsername) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));

		if (user.getUsername().equals(currentUsername)) {
			throw new IllegalStateException("不可停用自己的帳號");
		}

		user.setEnabled(false);
		// 2026-09-26 修正（High）：停用原本不會讓對方現有的登入失效，被停用的人手上的 token
		// 在剩餘效期（最長 8 小時）內仍可呼叫 API。比照重設密碼遞增 session version，
		// JwtAuthenticationFilter 比對不符即視為登入失效。停用也是帳號疑似外洩時的緊急處理手段。
		user.setActiveSessionVersion(user.getActiveSessionVersion() + 1);
		AppUser saved = appUserRepository.save(user);
		return UserAccountResponse.from(saved);
	}

	/**
	 * PUT /api/users/{id}/enable：復用（重新啟用）已停用的帳號。
	 *
	 * 與disableUser()對稱，但不需要「不可操作自己」的保護：啟用帳號不會讓
	 * 系統失去可登入的管理員，不存在disableUser()那種自我鎖死的風險。
	 *
	 * 重複啟用已啟用的帳號不視為錯誤（冪等），理由與disableUser()相同：
	 * 結果狀態與呼叫端的意圖一致，沒有理由回報失敗。
	 */
	@Transactional
	public UserAccountResponse enableUser(Long id) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));

		user.setEnabled(true);
		AppUser saved = appUserRepository.save(user);
		return UserAccountResponse.from(saved);
	}

	/**
	 * PUT /api/users/{id}/reset-password：管理者代重設密碼（V24）。
	 *
	 * <ul>
	 * <li><b>禁止重設自己</b>：管理者自己的密碼應走個人資料頁的「修改密碼」（需驗證目前密碼）。
	 * 若允許，這支端點會變成一條「不需要目前密碼就能改掉自己密碼」的捷徑，失去
	 * AuthService.changePassword() 驗證目前密碼的保護。</li>
	 * <li><b>遞增 activeSessionVersion</b>：重設的典型情境是「使用者忘記密碼」或「帳號疑似外洩」，
	 * 後者必須讓該帳號現有的所有登入立即失效，否則外洩的 token 在重設後仍可用到自然過期。</li>
	 * <li>已停用的帳號也可以重設（不會因此被啟用），讓管理者能先處理密碼再復用帳號。</li>
	 * </ul>
	 */
	@Transactional
	public UserAccountResponse resetPassword(Long id, String newPassword, String currentUsername) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		if (user.getUsername().equals(currentUsername)) {
			throw new IllegalStateException("不可重設自己的密碼，請改用個人資料頁的「修改密碼」");
		}
		// V27：必須有本人的待處理申請，重設成功即結案（同一筆交易）；沒有申請回 409。
		passwordResetRequestService.closePending(user.getId(), PasswordResetRequestStatus.COMPLETED,
				resolveManagerId(currentUsername));
		assignManagerIssuedPassword(user, newPassword);
		user.setActiveSessionVersion(user.getActiveSessionVersion() + 1);
		AppUser saved = appUserRepository.save(user);
		return UserAccountResponse.from(saved);
	}

	/**
	 * PUT /api/users/{id}/password-reset-request/reject：駁回重設密碼申請（V27）。
	 * 例如管理者無法確認是本人提出。密碼不變；使用者之後可以再申請。沒有待處理申請回 409。
	 */
	@Transactional
	public UserAccountResponse rejectPasswordResetRequest(Long id, String currentUsername) {
		AppUser user = appUserRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("使用者不存在"));
		passwordResetRequestService.closePending(user.getId(), PasswordResetRequestStatus.REJECTED,
				resolveManagerId(currentUsername));
		return UserAccountResponse.from(user);
	}

	private Long resolveManagerId(String username) {
		return appUserRepository.findByUsername(username).map(AppUser::getId).orElse(null);
	}

	/**
	 * 「管理者設定的密碼」共用邏輯：帳號建立與代重設密碼都走這裡，兩者固定觸發
	 * 強制下次登入修改密碼，不提供個別開關（規格決議：不為此多開設定項）。
	 *
	 * 密碼一律經 BCrypt 雜湊後存入，絕不以明文保存；沿用 AuthService.login()
	 * 驗證時使用的同一個 PasswordEncoder Bean，確保產生與驗證兩端的演算法一致。
	 */
	private void assignManagerIssuedPassword(AppUser user, String rawPassword) {
		user.setPassword(passwordEncoder.encode(rawPassword));
		user.setMustChangePassword(true);
	}
}
