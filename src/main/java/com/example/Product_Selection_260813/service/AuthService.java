package com.example.Product_Selection_260813.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Product_Selection_260813.common.exception.AccountDisabledException;
import com.example.Product_Selection_260813.common.exception.InvalidCredentialsException;
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
	@Transactional(readOnly = true)
	public LoginResult login(String username, String rawPassword) {
		AppUser user = appUserRepository.findByUsername(username)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		String token = jwtTokenProvider.generateToken(user);
		log.info("使用者登入成功 username={} role={}", user.getUsername(), user.getRole());

		return new LoginResult(token, jwtTokenProvider.getExpirationSeconds(), UserResponse.from(user));
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
				.orElseThrow(InvalidCredentialsException::new); // token有效但使用者已被刪除的邊界情況

		if (!Boolean.TRUE.equals(user.getEnabled())) {
			throw new AccountDisabledException();
		}

		return UserResponse.from(user);
	}

	/**
	 * POST /api/auth/logout 業務邏輯。
	 *
	 * JWT本質無狀態，Service層目前沒有可以「讓token失效」的機制（無黑名單、無Session），
	 * 真正清除登入狀態的動作（清除httpOnly Cookie）發生在Controller層。
	 * 這個method目前只做登出稽核記錄，保留這支API的語意完整性，並作為未來若要加上
	 * token黑名單機制時的擴充點（見六-4、十三 Phase 2待辦）。
	 */
	public void logout(String username) {
		log.info("使用者登出 username={}", username);
	}
}
