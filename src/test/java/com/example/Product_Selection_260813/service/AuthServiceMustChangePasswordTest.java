package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Product_Selection_260813.dto.response.LoginResult;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.UserRole;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.security.JwtTokenProvider;

/** V24：使用者自行修改密碼成功後解除「必須修改密碼」，且新 token 依解除後的狀態簽發。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceMustChangePasswordTest {

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@InjectMocks
	private AuthService authService;

	@Test
	void changePassword_換掉管理者給的密碼後解除強制修改() {
		AppUser user = new AppUser();
		ReflectionTestUtils.setField(user, "id", 2L);
		ReflectionTestUtils.setField(user, "username", "buyer01");
		ReflectionTestUtils.setField(user, "password", "temp-hash");
		ReflectionTestUtils.setField(user, "name", "陳小姐");
		ReflectionTestUtils.setField(user, "role", UserRole.PURCHASER);
		ReflectionTestUtils.setField(user, "enabled", true);
		ReflectionTestUtils.setField(user, "mustChangePassword", true);

		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Temp-1234", "temp-hash")).thenReturn(true);
		when(passwordEncoder.matches("Mine-5678", "temp-hash")).thenReturn(false);
		when(passwordEncoder.encode("Mine-5678")).thenReturn("mine-hash");
		when(appUserRepository.save(user)).thenReturn(user);
		when(jwtTokenProvider.generateToken(user)).thenAnswer(invocation -> {
			// generateToken() 讀的是當下的 mustChangePassword，必須已經是 false。
			assertThat(user.getMustChangePassword()).isFalse();
			return "new-token";
		});

		LoginResult result = authService.changePassword("buyer01", "Temp-1234", "Mine-5678");

		assertThat(user.getMustChangePassword()).isFalse();
		assertThat(result.getUser().getMustChangePassword()).isFalse();
	}
}
