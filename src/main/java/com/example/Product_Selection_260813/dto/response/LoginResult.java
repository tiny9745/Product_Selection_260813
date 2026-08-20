package com.example.Product_Selection_260813.dto.response;

/**
 * AuthService.login() 的回傳值。
 *
 * 刻意把token與使用者資訊包在一起回傳，而不是讓Controller事後再呼叫一次
 * 「依username查使用者」：login()內部已經查過一次AppUser，若再讓Controller
 * 呼叫別的method重查一次，等於同一個HTTP請求對同一張表查了兩次，沒有必要。
 *
 * 這個物件本身不會被序列化成HTTP回應：token要放進httpOnly Cookie（Controller負責），
 * user會被包進ApiResponse&lt;UserResponse&gt;（Controller負責），
 * LoginResult只是AuthService與AuthController之間傳遞資料的中介物件。
 */
public class LoginResult {

	private final String token;
	private final long expiresInSeconds;
	private final UserResponse user;

	public LoginResult(String token, long expiresInSeconds, UserResponse user) {
		this.token = token;
		this.expiresInSeconds = expiresInSeconds;
		this.user = user;
	}

	public String getToken() {
		return token;
	}

	public long getExpiresInSeconds() {
		return expiresInSeconds;
	}

	public UserResponse getUser() {
		return user;
	}
}
