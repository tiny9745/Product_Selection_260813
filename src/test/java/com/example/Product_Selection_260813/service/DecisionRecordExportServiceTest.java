package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.example.Product_Selection_260813.dto.request.DecisionRecordExportRequest;
import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.entity.ReviewRecord;
import com.example.Product_Selection_260813.entity.ReviewRisk;
import com.example.Product_Selection_260813.entity.ReviewRiskId;
import com.example.Product_Selection_260813.entity.RiskOption;
import com.example.Product_Selection_260813.enums.ReviewRecordReviewStatus;
import com.example.Product_Selection_260813.json.ProductSnapshot;
import com.example.Product_Selection_260813.repository.AppUserRepository;
import com.example.Product_Selection_260813.repository.ReviewRecordRepository;
import com.example.Product_Selection_260813.repository.ReviewRiskRepository;
import com.example.Product_Selection_260813.repository.RiskOptionRepository;

/**
 * 2026-09-26 管理層唯讀匯出：與決策紀錄頁同一套條件、分數讀審核快照、風險評估一次查回；
 * 這個 Service 沒有任何會寫入的相依（不注入 ProductExportLogRepository），不影響「未曾匯出」。
 */
@ExtendWith(MockitoExtension.class)
class DecisionRecordExportServiceTest {

	@Mock
	private ReviewRecordRepository reviewRecordRepository;
	@Mock
	private ReviewRiskRepository reviewRiskRepository;
	@Mock
	private RiskOptionRepository riskOptionRepository;
	@Mock
	private AppUserRepository appUserRepository;

	@InjectMocks
	private DecisionRecordExportService exportService;

	private ReviewRecord record() {
		ReviewRecord record = new ReviewRecord();
		record.setId(501L);
		record.setProductId(101L);
		record.setReviewerId(9L);
		record.setReviewStatus(ReviewRecordReviewStatus.APPROVED);
		record.setReviewedAt(LocalDateTime.of(2026, 9, 20, 10, 30));
		record.setFinalScoreSnapshot(new BigDecimal("86.20"));
		record.setWeatherBoostSnapshot(new BigDecimal("1.20"));
		record.setReviewComment("=供貨穩定");
		ProductSnapshot snapshot = new ProductSnapshot();
		snapshot.setName("中秋禮盒");
		record.setProductSnapshot(snapshot);
		return record;
	}

	@Test
	void 依決策紀錄條件查詢並輸出審核快照與風險評估() {
		DecisionRecordExportRequest request = new DecisionRecordExportRequest();
		request.setReviewResult(ReviewRecordReviewStatus.APPROVED);
		request.setKeyword(" 禮盒 ");
		request.setReviewedFrom(LocalDate.of(2026, 9, 1));
		request.setReviewedTo(LocalDate.of(2026, 9, 30));
		when(reviewRecordRepository.searchDecisionRecords(eq("APPROVED"), eq(LocalDateTime.of(2026, 9, 1, 0, 0)),
				eq(LocalDateTime.of(2026, 10, 1, 0, 0)), eq("禮盒"), eq("reviewedAt"), eq("DESC"), any()))
				.thenReturn(new PageImpl<>(List.of(record())));
		AppUser reviewer = new AppUser();
		reviewer.setId(9L);
		reviewer.setName("林經理");
		when(appUserRepository.findAllById(any())).thenReturn(List.of(reviewer));
		ReviewRisk risk = new ReviewRisk();
		risk.setId(new ReviewRiskId(501L, 3L));
		risk.setIsSelected(true);
		when(reviewRiskRepository.findById_ReviewIdIn(any())).thenReturn(List.of(risk));
		RiskOption option = new RiskOption();
		option.setId(3L);
		option.setName("供貨風險");
		when(riskOptionRepository.findAll()).thenReturn(List.of(option));

		DecisionRecordExportService.ExportResult result = exportService.export(request);

		assertThat(result.rowCount()).isEqualTo(1);
		String csv = new String(result.content(), StandardCharsets.UTF_8);
		assertThat(csv.startsWith("\uFEFF審核紀錄編號")).isTrue();
		assertThat(csv.contains("501,101,中秋禮盒,,審核通過,林經理,2026-09-20 10:30,,,1.2,86.2,供貨風險,,'=供貨穩定")).isTrue();
	}

	@Test
	void 超過上限時拒絕() {
		List<ReviewRecord> many = new ArrayList<>();
		for (int i = 0; i <= DecisionRecordExportService.MAX_EXPORT_ROWS; i++) {
			many.add(new ReviewRecord());
		}
		when(reviewRecordRepository.searchDecisionRecords(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new PageImpl<>(many));

		assertThatThrownBy(() -> exportService.export(null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 審核日期起日晚於迄日回400() {
		DecisionRecordExportRequest request = new DecisionRecordExportRequest();
		request.setReviewedFrom(LocalDate.of(2026, 9, 30));
		request.setReviewedTo(LocalDate.of(2026, 9, 1));

		assertThatThrownBy(() -> exportService.export(request)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 沒有資料時只回表頭() {
		when(reviewRecordRepository.searchDecisionRecords(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of()));

		assertThat(exportService.export(null).rowCount()).isEqualTo(0);
	}
}
