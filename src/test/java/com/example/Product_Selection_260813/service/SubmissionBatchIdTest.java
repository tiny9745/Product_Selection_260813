package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** 送審批次識別碼：同一人、同一曆日＝一批；"NONE"＝沒有送審批次資料。 */
class SubmissionBatchIdTest {

	@Test
	void 解析批次字串並換算成當日整天的半開區間() {
		SubmissionBatchId batch = SubmissionBatchId.parse("2026-09-25_3");

		assertThat(batch.none()).isFalse();
		assertThat(batch.submittedBy()).isEqualTo(3L);
		assertThat(batch.rangeStart()).isEqualTo(LocalDateTime.of(2026, 9, 25, 0, 0));
		assertThat(batch.rangeEndExclusive()).isEqualTo(LocalDateTime.of(2026, 9, 26, 0, 0));
		assertThat(batch.toString()).isEqualTo("2026-09-25_3");
	}

	@Test
	void 格式化與解析可互轉() {
		String id = SubmissionBatchId.of(LocalDate.of(2026, 1, 5), 12L).toString();
		assertThat(SubmissionBatchId.parse(id).submittedBy()).isEqualTo(12L);
	}

	@Test
	void NONE代表沒有送審批次資料() {
		SubmissionBatchId none = SubmissionBatchId.parse("NONE");
		assertThat(none.none()).isTrue();
		assertThat(none.rangeStart()).isNull();
	}

	@Test
	void 空值代表不篩選() {
		assertThat(SubmissionBatchId.parse(null)).isNull();
		assertThat(SubmissionBatchId.parse("  ")).isNull();
	}

	@Test
	void 格式錯誤回400() {
		assertThatThrownBy(() -> SubmissionBatchId.parse("2026-13-40_3")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SubmissionBatchId.parse("2026-09-25_abc")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SubmissionBatchId.parse("2026-09-25")).isInstanceOf(IllegalArgumentException.class);
	}
}
