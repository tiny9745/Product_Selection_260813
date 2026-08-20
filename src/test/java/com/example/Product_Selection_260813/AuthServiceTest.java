package com.example.Product_Selection_260813;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Product_Selection_260813.common.exception.AccountDisabledException;
import com.example.Product_Selection_260813.common.exception.InvalidCredentialsException;
import com.example.Product_Selection_260813.dto.response.LoginResult;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.UserRole;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.security.JwtTokenProvider;
import com.example.Product_Selection_260813.service.AuthService;

/**
 * 這支測試完全不需要啟動Spring Context或Servlet環境，
 * 印證了AuthService不依賴HttpServletRequest/Response的設計取捨（見AuthService註解）。
 *
 * 使用field injection（@Autowired）而非建構子注入，Mockito的@InjectMocks仍然能透過
 * 反射把@Mock物件塞進AuthService的private欄位，不影響測試撰寫方式。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@InjectMocks
	private AuthService authService;

	private AppUser enabledUser;

	@BeforeEach
	void setUp() {
		enabledUser = new AppUser();
		ReflectionTestUtils.setField(enabledUser, "id", 1L);
		ReflectionTestUtils.setField(enabledUser, "username", "purchaser01");
		ReflectionTestUtils.setField(enabledUser, "password", "encoded-hash");
		ReflectionTestUtils.setField(enabledUser, "name", "採購A");
		ReflectionTestUtils.setField(enabledUser, "role", UserRole.PURCHASER);
		ReflectionTestUtils.setField(enabledUser, "enabled", true);
	}

	@Test
	void login_成功_回傳token與使用者資訊() {
		when(appUserRepository.findByUsername("purchaser01")).thenReturn(Optional.of(enabledUser));
		when(passwordEncoder.matches("correct-password", "encoded-hash")).thenReturn(true);
		when(jwtTokenProvider.generateToken(enabledUser)).thenReturn("fake-jwt-token");
		when(jwtTokenProvider.getExpirationSeconds()).thenReturn(28800L);

		LoginResult result = authService.login("purchaser01", "correct-password");

		assertThat(result.getToken()).isEqualTo("fake-jwt-token");
		assertThat(result.getExpiresInSeconds()).isEqualTo(28800L);
		assertThat(result.getUser().getUsername()).isEqualTo("purchaser01");
		assertThat(result.getUser().getRole()).isEqualTo(UserRole.PURCHASER);
	}

	@Test
	void login_帳號不存在_拋出InvalidCredentialsException() {
		when(appUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("nobody", "whatever"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_密碼錯誤_拋出InvalidCredentialsException() {
		when(appUserRepository.findByUsername("purchaser01")).thenReturn(Optional.of(enabledUser));
		when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.login("purchaser01", "wrong-password"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_帳號已停用_密碼正確仍拋出AccountDisabledException() {
		ReflectionTestUtils.setField(enabledUser, "enabled", false);
		when(appUserRepository.findByUsername("purchaser01")).thenReturn(Optional.of(enabledUser));
		when(passwordEncoder.matches("correct-password", "encoded-hash")).thenReturn(true);

		assertThatThrownBy(() -> authService.login("purchaser01", "correct-password"))
				.isInstanceOf(AccountDisabledException.class);
	}

	@Test
	void getCurrentUser_帳號已被停用_拋出AccountDisabledException() {
		ReflectionTestUtils.setField(enabledUser, "enabled", false);
		when(appUserRepository.findByUsername("purchaser01")).thenReturn(Optional.of(enabledUser));

		assertThatThrownBy(() -> authService.getCurrentUser("purchaser01"))
				.isInstanceOf(AccountDisabledException.class);
	}
}
