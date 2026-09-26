package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.example.Product_Selection_260813.dto.request.UserCreateRequest;
import com.example.Product_Selection_260813.dto.response.UserAccountResponse;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;
import com.example.Product_Selection_260813.enums.UserRole;
import com.example.Product_Selection_260813.repository.AppUserRepository;

/**
 * V24 密碼生命週期：建立帳號與管理者代重設密碼都會設定 mustChangePassword=true；
 * 代重設另外遞增 activeSessionVersion（踢掉對方現有登入），且不可重設自己。
 */
@ExtendWith(MockitoExtension.class)
class UserServicePasswordLifecycleTest {

	@Mock
	private AppUserRepository appUserRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordResetRequestService passwordResetRequestService;

	@InjectMocks
	private UserService userService;

	private AppUser buyer;

	@BeforeEach
	void setUp() {
		buyer = new AppUser();
		ReflectionTestUtils.setField(buyer, "id", 2L);
		ReflectionTestUtils.setField(buyer, "username", "buyer01");
		ReflectionTestUtils.setField(buyer, "password", "old-hash");
		ReflectionTestUtils.setField(buyer, "name", "陳小姐");
		ReflectionTestUtils.setField(buyer, "role", UserRole.PURCHASER);
		ReflectionTestUtils.setField(buyer, "activeSessionVersion", 3);
	}

	@Test
	void resetPassword_設定新密碼並強制下次修改_同時讓現有登入失效() {
		when(appUserRepository.findById(2L)).thenReturn(Optional.of(buyer));
		when(appUserRepository.findByUsername("manager01")).thenReturn(Optional.of(manager()));
		when(passwordEncoder.encode("Temp-1234")).thenReturn("temp-hash");
		when(appUserRepository.save(buyer)).thenReturn(buyer);

		UserAccountResponse result = userService.resetPassword(2L, "Temp-1234", "manager01");

		assertThat(buyer.getPassword()).isEqualTo("temp-hash");
		assertThat(buyer.getMustChangePassword()).isTrue();
		assertThat(buyer.getActiveSessionVersion()).isEqualTo(4);
		assertThat(result.getMustChangePassword()).isTrue();
		// V27：重設即結案本人的申請
		verify(passwordResetRequestService).closePending(2L, PasswordResetRequestStatus.COMPLETED, 1L);
	}

	@Test
	void resetPassword_沒有本人申請時拒絕且密碼不變_V27() {
		when(appUserRepository.findById(2L)).thenReturn(Optional.of(buyer));
		when(appUserRepository.findByUsername("manager01")).thenReturn(Optional.of(manager()));
		when(passwordResetRequestService.closePending(2L, PasswordResetRequestStatus.COMPLETED, 1L))
				.thenThrow(new IllegalStateException("此帳號沒有待處理的重設密碼申請"));

		assertThatThrownBy(() -> userService.resetPassword(2L, "Temp-1234", "manager01"))
				.isInstanceOf(IllegalStateException.class);
		verify(appUserRepository, never()).save(any());
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void rejectPasswordResetRequest_駁回申請且不動密碼_V27() {
		when(appUserRepository.findById(2L)).thenReturn(Optional.of(buyer));
		when(appUserRepository.findByUsername("manager01")).thenReturn(Optional.of(manager()));

		userService.rejectPasswordResetRequest(2L, "manager01");

		verify(passwordResetRequestService).closePending(2L, PasswordResetRequestStatus.REJECTED, 1L);
		assertThat(buyer.getPassword()).isEqualTo("old-hash");
	}

	@Test
	void disableUser_讓對方現有登入立即失效() {
		when(appUserRepository.findById(2L)).thenReturn(Optional.of(buyer));
		when(appUserRepository.save(buyer)).thenReturn(buyer);

		userService.disableUser(2L, "manager01");

		assertThat(buyer.getEnabled()).isFalse();
		assertThat(buyer.getActiveSessionVersion()).isEqualTo(4);
	}

	private AppUser manager() {
		AppUser manager = new AppUser();
		ReflectionTestUtils.setField(manager, "id", 1L);
		ReflectionTestUtils.setField(manager, "username", "manager01");
		ReflectionTestUtils.setField(manager, "role", UserRole.MANAGER);
		return manager;
	}

	@Test
	void resetPassword_不可重設自己() {
		when(appUserRepository.findById(2L)).thenReturn(Optional.of(buyer));

		assertThatThrownBy(() -> userService.resetPassword(2L, "Temp-1234", "buyer01"))
				.isInstanceOf(IllegalStateException.class);
		verify(appUserRepository, never()).save(any());
	}

	@Test
	void resetPassword_使用者不存在() {
		when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.resetPassword(99L, "Temp-1234", "manager01"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createUser_管理者設定的初始密碼也必須在首次登入時修改() {
		UserCreateRequest request = new UserCreateRequest();
		ReflectionTestUtils.setField(request, "username", "buyer03");
		ReflectionTestUtils.setField(request, "name", "張先生");
		ReflectionTestUtils.setField(request, "role", UserRole.PURCHASER);
		ReflectionTestUtils.setField(request, "password", "Init-1234");
		when(appUserRepository.existsByUsername("buyer03")).thenReturn(false);
		when(passwordEncoder.encode("Init-1234")).thenReturn("init-hash");
		when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserAccountResponse result = userService.createUser(request);

		assertThat(result.getMustChangePassword()).isTrue();
	}
}
