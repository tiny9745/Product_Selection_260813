package com.example.Product_Selection_260813.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.AppUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-ms:28800000}") long expirationMs // 預設8小時 = 28800000ms
	) {
		// HS256要求金鑰長度至少256bit（32 bytes）；secret太短時jjwt會直接在這裡拋例外，
		// 在啟動階段就會發現設定錯誤，而不是等到第一次登入才炸掉，這裡刻意不吃掉這個例外。
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public long getExpirationSeconds() {
		return expirationMs / 1000;
	}

	public String generateToken(AppUser user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
				.subject(user.getUsername())
				.claim(CLAIM_USER_ID, user.getId())
				.claim(CLAIM_ROLE, user.getRole().name())
				.claim(CLAIM_NAME, user.getName())
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
}
