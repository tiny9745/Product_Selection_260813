package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.PasswordResetRequest;
import com.example.Product_Selection_260813.enums.PasswordResetRequestStatus;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.PasswordResetRequestRepository;

/**
 * V27 重設密碼申請：公開申請不透露帳號是否存在、同帳號只保留一筆待處理申請、
 * 停用帳號不建立申請；結案需要有待處理申請（沒有就是 409）。
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetRequestServiceTest {

	@Mock
	private PasswordResetRequestRepository requestRepository;

	@Mock
	private AppUserRepository appUserRepository;

	@InjectMocks
	private PasswordResetRequestService service;

	private AppUser user(boolean enabled) {
		AppUser user = new AppUser();
		ReflectionTestUtils.setField(user, "id", 2L);
		ReflectionTestUtils.setField(user, "username", "buyer01");
		ReflectionTestUtils.setField(user, "enabled", enabled);
		return user;
	}

	@Test
	void 有效帳號首次申請時建立一筆待處理申請() {
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user(true)));
		when(requestRepository.findByUserIdAndStatus(2L, PasswordResetRequestStatus.PENDING)).thenReturn(List.of());

		service.apply("  buyer01 ");

		verify(requestRepository).save(any(PasswordResetRequest.class));
	}

	@Test
	void 已有待處理申請時不重複建立() {
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user(true)));
		when(requestRepository.findByUserIdAndStatus(2L, PasswordResetRequestStatus.PENDING))
				.thenReturn(List.of(new PasswordResetRequest(2L, LocalDateTime.now())));

		service.apply("buyer01");

		verify(requestRepository, never()).save(any());
	}

	@Test
	void 帳號不存在或已停用時靜默忽略_不丟例外() {
		when(appUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(user(false)));

		service.apply("nobody");
		service.apply("buyer01");

		verify(requestRepository, never()).save(any());
	}

	@Test
	void 結案時一併處理所有待處理申請() {
		PasswordResetRequest first = new PasswordResetRequest(2L, LocalDateTime.of(2026, 9, 26, 9, 0));
		PasswordResetRequest second = new PasswordResetRequest(2L, LocalDateTime.of(2026, 9, 26, 9, 1));
		when(requestRepository.findByUserIdAndStatus(2L, PasswordResetRequestStatus.PENDING))
				.thenReturn(List.of(first, second));

		LocalDateTime requestedAt = service.closePending(2L, PasswordResetRequestStatus.COMPLETED, 1L);

		assertThat(first.getStatus()).isEqualTo(PasswordResetRequestStatus.COMPLETED);
		assertThat(second.getStatus()).isEqualTo(PasswordResetRequestStatus.COMPLETED);
		assertThat(first.getHandledBy()).isEqualTo(1L);
		assertThat(requestedAt).isEqualTo(LocalDateTime.of(2026, 9, 26, 9, 0));
	}

	@Test
	void 沒有待處理申請時不能結案() {
		when(requestRepository.findByUserIdAndStatus(2L, PasswordResetRequestStatus.PENDING)).thenReturn(List.of());

		assertThatThrownBy(() -> service.closePending(2L, PasswordResetRequestStatus.COMPLETED, 1L))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 帳號清單以使用者對應最早的申請時間() {
		when(requestRepository.findByStatus(PasswordResetRequestStatus.PENDING)).thenReturn(List.of(
				new PasswordResetRequest(2L, LocalDateTime.of(2026, 9, 26, 9, 5)),
				new PasswordResetRequest(2L, LocalDateTime.of(2026, 9, 26, 9, 0)),
				new PasswordResetRequest(3L, LocalDateTime.of(2026, 9, 26, 10, 0))));

		Map<Long, LocalDateTime> result = service.pendingRequestedAtByUser();

		assertThat(result.get(2L)).isEqualTo(LocalDateTime.of(2026, 9, 26, 9, 0));
		assertThat(result.size()).isEqualTo(2);
	}

	@Test
	void 已結案的申請不可再結案() {
		PasswordResetRequest request = new PasswordResetRequest(2L, LocalDateTime.now());
		request.close(PasswordResetRequestStatus.REJECTED, 1L, LocalDateTime.now());

		assertThatThrownBy(() -> request.close(PasswordResetRequestStatus.COMPLETED, 1L, LocalDateTime.now()))
				.isInstanceOf(IllegalStateException.class);
	}
}
