package com.example.Product_Selection_260813.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.common.exception.AccountDisabledException;
import com.example.Product_Selection_260813.common.exception.InvalidCredentialsException;
import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.response.LoginResult;
import com.example.Product_Selection_260813.dto.response.UserResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.security.JwtTokenProvider;

/**
 * 對應 API總表 一、登入／權限：
 *   POST /api/auth/login  [公開]
 *   GET  /api/auth/me     [操作+管理]
 *   POST /api/auth/logout [操作+管理]
 *
 * 設計取捨：沒有透過Spring Security的AuthenticationManager／DaoAuthenticationProvider／
 * UserDetailsService整條認證鏈，直接在Service內手動比對帳密。原因：
 *   - 系統僅兩種角色、單一登入方式（帳密），沒有多種認證來源（OAuth2、LDAP等）
 *     需要Provider機制的可插拔性
 *   - 手動比對邏輯更直觀，單元測試不需要mock整條Spring Security鏈
 *   - 6週雛型時程，避免為了「更標準」而引入用不到的彈性（YAGNI）
 * 若之後有多種登入來源、或需要與Spring Security其他機制整合，建議改為標準UserDetailsService寫法。
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	/**
	 * POST /api/auth/login 業務邏輯：驗證帳密、建立JWT、回傳使用者角色。
	 *
	 * 檢查順序刻意為「先比對密碼、再檢查enabled」，而不是相反：
	 * 若先檢查enabled，還沒驗證身份的人就能從錯誤訊息得知「這個帳號存在且被停用」；
	 * 密碼驗證通過後才檢查enabled，確保只有真正持有正確密碼的人才看得到這個更明確的訊息。
	 *
	 * @return token與使用者資訊；Cookie的設定屬於HTTP層職責，交由AuthController處理，
	 *         這裡刻意不回傳Controller/HTTP層的物件，讓這支method不依賴Servlet API。
	 */
	@Transactional
	public LoginResult login(String username, String rawPassword) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		// 單一登入：遞增版本號並存檔，讓這次登入之前發出的所有舊 token
		// （不論在哪個裝置）在下一次請求時都會被 JwtAuthenticationFilter
		// 判定版本不符而失效。順序刻意是「先存檔、再產生 token」——
		// generateToken() 讀的是 user 物件當下的 activeSessionVersion，
		// 沒有先遞增的話新 token 會帶著舊版本號，等於一發出就已經失效。
		// 方法本身也從 readOnly 改為一般 @Transactional，因為現在真的
		// 有寫入動作，不再是純查詢。
		user.setActiveSessionVersion(user.getActiveSessionVersion() + 1);
		AppUser saved = appUserRepository.save(user);

		String token = jwtTokenProvider.generateToken(saved);
		log.info("使用者登入成功 username={} role={} sessionVersion={}",
				saved.getUsername(), saved.getRole(), saved.getActiveSessionVersion());

		return new LoginResult(token, jwtTokenProvider.getExpirationSeconds(), UserResponse.from(saved));
	}

	/**
	 * GET /api/auth/me 業務邏輯：取得目前登入使用者的基本資料與角色。
	 *
	 * 這裡刻意重新查一次資料庫，而不是直接信任JwtAuthenticationFilter已經驗證過的token內容：
	 * JWT有效期長達8小時，這段期間管理層若停用某帳號（enabled=false），
	 * 單純信任token內容會讓「停用帳號」這個RBAC相關的管理動作要等token過期
	 * （最長8小時）才會生效。多一次以username查PK索引的查詢，換取這支API的即時性，
	 * 這個成本可接受（/me呼叫頻率不像列表類API那麼高）。
	 *
	 * 注意：這個即時性保證僅限於/me這支API本身，其餘受JwtAuthenticationFilter保護的API
	 * 為了效能，並未在每個請求都重查資料庫，因此帳號停用對其餘API的生效時間仍是
	 * 最長8小時（見JwtAuthenticationFilter註解），這是六-4決議「不做黑名單機制」下的必然結果。
	 */
	@Transactional(readOnly = true)
	public UserResponse getCurrentUser(String username) {
		AppUser user = appUserRepository.findByUsername(username)
				// token有效但使用者已被刪除的邊界情況；不用「帳號或密碼錯誤」
				// （那是登入流程的語意，這裡使用者根本沒有輸入帳密）
				.orElseThrow(() -> new InvalidCredentialsException("登入狀態已失效，請重新登入"));

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		return UserResponse.from(user);
	}

	/**
	 * POST /api/auth/logout 業務邏輯。
	 *
	 * 【單一登入上線後，這裡不再只是稽核記錄】
	 * 原本的限制是「JWT本質無狀態，Service層沒有可以讓token失效的機制」——
	 * 單一登入機制上線後這裡同樣遞增 activeSessionVersion，讓這次登出所屬的
	 * token 立即失效。這麼做的實際效益：如果 token 在登出前已經外洩（例如
	 * 被瀏覽器擴充功能竊取），單純清 Cookie 並不會讓外洩的那份 token 失效，
	 * 遞增版本號才會讓「登出」這個動作真正產生效果，而不只是前端不再帶著
	 * 它送出請求而已。
	 *
	 * 這裡跟 login() 不同的地方是不需要回傳新 token——登出後就是沒有
	 * 有效登入狀態，不像密碼變更那樣需要讓當下這個 session 繼續使用。
	 */
	@Transactional
	public void logout(String username) {
		appUserRepository.findByUsername(username).ifPresent(user -> {
			user.setActiveSessionVersion(user.getActiveSessionVersion() + 1);
			appUserRepository.save(user);
		});
		log.info("使用者登出 username={}", username);
	}
	
	/**
	 * PATCH /api/auth/me 業務邏輯：修改自己的顯示名稱。
	 *
	 * 與getCurrentUser()相同，重新查一次資料庫而非信任JWT聲明的資訊：
	 * 這裡本來就需要拿到受管理的AppUser實體才能save，不是額外成本。
	 */
	@Transactional
	public UserResponse updateProfile(String username, String newName) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new InvalidCredentialsException("登入狀態已失效，請重新登入"));

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		user.setName(newName);
		AppUser saved = appUserRepository.save(user);
		log.info("使用者修改自身顯示名稱 username={}", username);

		return UserResponse.from(saved);
	}

	/**
	 * PATCH /api/auth/me/password 業務邏輯：修改自己的密碼。
	 *
	 * 檢查順序：帳號存在 → 帳號啟用 → 目前密碼正確 → 新舊密碼不可相同。
	 * 目前密碼錯誤與新舊密碼相同皆丟IllegalArgumentException（400）：
	 * 呼叫端此時已持有效JWT（身分已確認），這是輸入驗證層級的業務規則，
	 * 語意上與InvalidCredentialsException代表的「身分無法確認」不同，
	 * 不應混用401。
	 *
	 * 密碼修改成功後重新簽發JWT：使用者剛用「目前密碼」完成一次身分
	 * 重新確認，沒有理由要求他重新登入；回傳LoginResult讓Controller
	 * 沿用login()那套Cookie設定邏輯即可。
	 *
	 * 【單一登入上線後，下面這段舊限制已經不成立，附帶被修正了】
	 * 原本這裡的限制是「其餘裝置上尚未過期的舊JWT仍可繼續使用到自然過期
	 * 才會失效」——單一登入機制上線後，這裡同樣遞增 activeSessionVersion，
	 * 密碼變更後其他裝置上的舊 token 會在下一次請求時就失效，不用等到
	 * 自然過期。這不是本次功能的直接需求，是單一登入這個機制的必然結果，
	 * 但正好補上了先前文件裡明確記載的一個已知缺口。
	 */
	@Transactional
	public LoginResult changePassword(String username, String currentPassword, String newPassword) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(() -> new InvalidCredentialsException("登入狀態已失效，請重新登入"));

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
			throw new IllegalArgumentException("目前密碼輸入錯誤");
		}

		if (passwordEncoder.matches(newPassword, user.getPassword())) {
			throw new IllegalArgumentException(ValidationMessage.USER_NEW_PASSWORD_SAME_AS_OLD);
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		// 同 login()：遞增版本號並存檔，讓其他裝置上的舊 token 立即失效。
		user.setActiveSessionVersion(user.getActiveSessionVersion() + 1);
		AppUser saved = appUserRepository.save(user);
		log.info("使用者修改自身密碼 username={} sessionVersion={}", username, saved.getActiveSessionVersion());

		String token = jwtTokenProvider.generateToken(saved);
		return new LoginResult(token, jwtTokenProvider.getExpirationSeconds(), UserResponse.from(saved));
	}
}