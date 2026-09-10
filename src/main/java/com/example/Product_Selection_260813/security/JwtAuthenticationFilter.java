package com.example.Product_Selection_260813.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 每個請求進來時，嘗試從httpOnly Cookie取出JWT並驗證，驗證成功則把使用者身份
 * 寫進SecurityContext，讓後續的@AuthenticationPrincipal、@PreAuthorize能取用。
 *
 * 設計取捨：這裡直接信任JWT裡的claims（username／role），不會為了每個請求
 * 都額外查一次資料庫確認enabled狀態——若真的要做到帳號停用立即生效，
 * 每個受保護的API都要多一次DB查詢，對一個雛型系統而言成本大於效益。
 * 目前只有AuthService.getCurrentUser()（對應/api/auth/me）刻意重查資料庫，
 * 其餘API的「帳號停用生效時間」則依循六-4決議：最長等到8小時token過期為止，
 * 這是文件裡「不做Refresh Token/黑名單機制」的必然結果，不是這支filter獨自的取捨。
 *
 * 【單一登入機制對上述決議的更動】
 * 六-4決議明確記載「不做黑名單機制」，單一登入功能上線後這句話不再成立——
 * 這裡新增了一次額外的資料庫查詢（sessionVersion 比對），性質上就是六-4
 * 當初刻意省略的狀態追蹤機制。這是在明確需求下的架構調整，不是對舊決議的
 * 誤觸；文件裡的六-4段落應該回頭更新，避免之後的人以為現狀仍是純無狀態設計。
 *
 * 這裡只查 activeSessionVersion 這一個整數欄位（用 Repository 的投影查詢，
 * 不撈整個 AppUser 實體），把多查一次資料庫的成本壓到最低，但終究是每個
 * 受保護請求都要多一次 DB 往返，這是單一登入這個需求本身的必要代價，
 * 沒有辦法在維持「新登入立即讓舊 token 失效」的前提下繞過。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String TOKEN_COOKIE_NAME = "access_token";

	private final JwtTokenProvider jwtTokenProvider;
	private final com.example.Product_Selection_260813.repository.AppUserRepository appUserRepository;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
			com.example.Product_Selection_260813.repository.AppUserRepository appUserRepository) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.appUserRepository = appUserRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		extractTokenFromCookie(request).flatMap(jwtTokenProvider::parseClaims).ifPresent(claims -> {
			String username = jwtTokenProvider.getUsername(claims);
			String role = jwtTokenProvider.getRole(claims);
			Integer tokenVersion = jwtTokenProvider.getSessionVersion(claims);

			// 單一登入核心比對：token 裡帶的版本號與資料庫目前的版本號不一致，
			// 就視為這個 token 已失效——不管它本身是否還在 8 小時效期內。
			// 新登入會讓資料庫版本號往前推進，舊 token 帶的還是登入當下的
			// 舊版本號，比對就會在這裡失敗，達到「新登入自動讓舊 token 失效」
			// 的效果，不需要額外的登出動作或黑名單清單。
			//
			// tokenVersion 為 null（V5 上線前簽發、尚未過期的舊 token）也視為
			// 不符——這批舊 token 沒有攜帶版本資訊，沒有辦法比對，保守起見
			// 一律要求重新登入，而不是放行一個無法驗證的 token。
			Integer currentVersion = appUserRepository.findActiveSessionVersionByUsername(username).orElse(null);
			if (tokenVersion == null || currentVersion == null || !tokenVersion.equals(currentVersion)) {
				return;
			}

			// 統一加上"ROLE_"前綴：Spring Security的hasRole("MANAGER")底層比對的
			// 就是"ROLE_MANAGER"這個Authority字串，這是框架慣例，不是本專案自創的規則。
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

			var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		});

		filterChain.doFilter(request, response);
	}

	private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
				return Optional.ofNullable(cookie.getValue());
			}
		}
		return Optional.empty();
	}
}
