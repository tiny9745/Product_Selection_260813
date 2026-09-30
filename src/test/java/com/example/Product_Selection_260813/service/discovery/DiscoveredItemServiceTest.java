package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.enums.DiscoveredItemStatus;
import com.example.Product_Selection_260813.enums.DiscoveryDismissReason;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemEvidenceRepository;
import com.example.Product_Selection_260813.repository.DiscoveredItemRepository;

@ExtendWith(MockitoExtension.class)
class DiscoveredItemServiceTest {

	@Mock
	private DiscoveredItemRepository discoveredItemRepository;
	@Mock
	private DiscoveredItemEvidenceRepository discoveredItemEvidenceRepository;
	@Mock
	private AppUserRepository appUserRepository;

	@InjectMocks
	private DiscoveredItemService service;

	private static DiscoveredItem item(DiscoveredItemStatus status) {
		DiscoveredItem item = new DiscoveredItem();
		item.setId(5L);
		item.setDisplayName("義美小泡芙");
		item.setStatus(status);
		return item;
	}

	@Test
	void 待處理項目可以略過_記錄處理人與原因() {
		DiscoveredItem target = item(DiscoveredItemStatus.NEW);
		AppUser buyer = new AppUser();
		buyer.setId(3L);
		buyer.setName("陳小姐");
		when(discoveredItemRepository.findById(5L)).thenReturn(Optional.of(target));
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(buyer));
		when(appUserRepository.findById(3L)).thenReturn(Optional.of(buyer));
		when(discoveredItemRepository.save(any(DiscoveredItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(discoveredItemEvidenceRepository.findByItemIdInOrderByPostedAtDesc(anyList())).thenReturn(List.of());

		var response = service.dismiss(5L, DiscoveryDismissReason.NOT_FOR_GROUP_BUY, " 單價太高 ", "buyer01");

		assertThat(target.getStatus()).isEqualTo(DiscoveredItemStatus.DISMISSED);
		assertThat(target.getDismissReasonCode()).isEqualTo(DiscoveryDismissReason.NOT_FOR_GROUP_BUY);
		assertThat(target.getDismissReason()).isEqualTo("單價太高");
		assertThat(target.getHandledBy()).isEqualTo(3L);
		assertThat(response.handledByName()).isEqualTo("陳小姐");
	}

	@Test
	void 已建立商品的項目不能略過() {
		when(discoveredItemRepository.findById(5L)).thenReturn(Optional.of(item(DiscoveredItemStatus.CONVERTED)));
		assertThatThrownBy(() -> service.dismiss(5L, DiscoveryDismissReason.OTHER, null, "buyer01"))
				.isInstanceOf(IllegalStateException.class);
		verify(discoveredItemRepository, never()).save(any());
	}

	@Test
	void 轉成商品_已轉過的回409_不存在的回400() {
		DiscoveredItem dismissed = item(DiscoveredItemStatus.DISMISSED);
		when(discoveredItemRepository.findById(5L)).thenReturn(Optional.of(dismissed));
		// 2026-09-30：回傳被標記的項目本身，ProductService 用它的 searchKeyword 帶入新商品
		assertThat(service.markConverted(5L, 88L, 3L)).isSameAs(dismissed);
		assertThat(dismissed.getStatus()).isEqualTo(DiscoveredItemStatus.CONVERTED);
		assertThat(dismissed.getConvertedProductId()).isEqualTo(88L);

		assertThatThrownBy(() -> service.markConverted(5L, 99L, 3L)).isInstanceOf(IllegalStateException.class);

		when(discoveredItemRepository.findById(404L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.markConverted(404L, 1L, 3L)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 已略過的項目可以復原() {
		DiscoveredItem dismissed = item(DiscoveredItemStatus.DISMISSED);
		dismissed.setDismissReasonCode(DiscoveryDismissReason.OTHER);
		dismissed.setDismissReason("不適合");
		dismissed.setHandledBy(3L);
		when(discoveredItemRepository.findById(5L)).thenReturn(Optional.of(dismissed));
		when(discoveredItemRepository.save(any(DiscoveredItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(discoveredItemEvidenceRepository.findByItemIdInOrderByPostedAtDesc(anyList())).thenReturn(List.of());

		service.restore(5L);

		assertThat(dismissed.getStatus()).isEqualTo(DiscoveredItemStatus.NEW);
		assertThat(dismissed.getDismissReason()).isNull();
		assertThat(dismissed.getDismissReasonCode()).isNull();
		assertThat(dismissed.getHandledBy()).isNull();
	}

	@Test
	void 沒帶原因代碼時視為其他() {
		DiscoveredItem target = item(DiscoveredItemStatus.NEW);
		AppUser buyer = new AppUser();
		buyer.setId(3L);
		when(discoveredItemRepository.findById(5L)).thenReturn(Optional.of(target));
		when(appUserRepository.findByUsername("buyer01")).thenReturn(Optional.of(buyer));
		when(discoveredItemRepository.save(any(DiscoveredItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(discoveredItemEvidenceRepository.findByItemIdInOrderByPostedAtDesc(anyList())).thenReturn(List.of());

		service.dismiss(5L, null, null, "buyer01");

		assertThat(target.getDismissReasonCode()).isEqualTo(DiscoveryDismissReason.OTHER);
	}
}
