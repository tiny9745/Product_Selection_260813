package com.example.Product_Selection_260813.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.AppUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT的產生／解析／驗證，全部集中在這裡，AuthService不需要知道JWT函式庫的API細節。
 *
 * 六-4 JWT設定決議：
 * - 過期時間：8小時（對應一個工作天）
 * - 不實作Refresh Token輪替機制，過期後直接要求重新登入
 * - Token本身不需要對應資料庫Schema變更（無狀態設計）
 *
 * 對應 build.gradle 已引入的 io.jsonwebtoken:jjwt-api:0.12.6，此版本API為
 * Jwts.builder()...signWith(SecretKey) 與 Jwts.parser().verifyWith(SecretKey)，
 * 與較舊的0.11.x以前版本（SignatureAlgorithm.HS256, key）寫法不同，使用時需注意版本對應。
 */
@Component
public class JwtTokenProvider {

	private static final String CLAIM_USER_ID = "userId";
	private static final String CLAIM_ROLE = "role";
	private static final String CLAIM_NAME = "name";
	private static final String CLAIM_SESSION_VERSION = "sessionVersion";

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-ms}") long expirationMs
	) {
		// HS256要求金鑰長度至少256bit（32 bytes）；secret太短時jjwt會直接在這裡拋例外，
		// 在啟動階段就會發現設定錯誤，而不是等到第一次登入才炸掉，這裡刻意不吃掉這個例外。
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public long getExpirationSeconds() {
		return expirationMs / 1000;
	}

	/**
	 * 產生 token 時把使用者當下的 activeSessionVersion 一併寫進 claim。
	 *
	 * 呼叫端（AuthService）必須先把資料庫裡的版本號遞增、存檔，再呼叫這個
	 * 方法用「遞增後」的 AppUser 物件產生 token——這樣新 token 裡的版本號
	 * 才會跟資料庫最新值一致，後續 JwtAuthenticationFilter 才通過得了。
	 * 順序顛倒的話，新 token 會帶著遞增前的舊版本號，等於一發出就已經失效。
	 */
	public String generateToken(AppUser user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
				.subject(user.getUsername())
				.claim(CLAIM_USER_ID, user.getId())
				.claim(CLAIM_ROLE, user.getRole().name())
				.claim(CLAIM_NAME, user.getName())
				.claim(CLAIM_SESSION_VERSION, user.getActiveSessionVersion())
				.issuedAt(now)
				.expiration(expiry)
				.signWith(signingKey)
				.compact();
	}

	/**
	 * 解析並驗證token；驗證失敗（過期、簽章不符、格式錯誤）一律回傳empty，
	 * 呼叫端（JwtAuthenticationFilter）只需要判斷有沒有值，不需要另外catch各種JJWT例外型別。
	 */
	public Optional<Claims> parseClaims(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return Optional.of(claims);
		} catch (JwtException | IllegalArgumentException e) {
		    return Optional.empty();
		}
	}

	public String getUsername(Claims claims) {
		return claims.getSubject();
	}

	public String getRole(Claims claims) {
		return claims.get(CLAIM_ROLE, String.class);
	}

	/**
	 * token 裡帶的登入版本號。用 Integer 取值再做 null 防呆——理論上這個
	 * claim 在 generateToken() 必定會寫入，但舊版（V5 上線前）簽發的 token
	 * 裡沒有這個 claim，遇到 null 時交由呼叫端（JwtAuthenticationFilter）
	 * 判定為版本不符、視為失效，而不是在這裡直接拋例外讓整個過濾器出錯。
	 */
	public Integer getSessionVersion(Claims claims) {
		return claims.get(CLAIM_SESSION_VERSION, Integer.class);
	}
}
