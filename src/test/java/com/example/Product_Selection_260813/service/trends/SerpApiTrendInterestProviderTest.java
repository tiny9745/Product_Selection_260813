package com.example.Product_Selection_260813.service.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.enums.GoogleTrendStatus;
import com.example.Product_Selection_260813.enums.TrendSignalTrendDirection;

/**
 * 解析與換算：fixture 取自 2026-09-28 實際呼叫 SerpApi 的回應（已移除帳號相關的網址欄位）。
 */
class SerpApiTrendInterestProviderTest {

	private static String fixture(String name) throws IOException {
		try (InputStream in = SerpApiTrendInterestProviderTest.class.getResourceAsStream("/serpapi/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void 實測氣炸鍋_排除未完整的最後一點後算出持平() throws IOException {
		TrendInterest interest = SerpApiTrendInterestProvider.parse(fixture("google_trends_airfryer.json"));

		assertThat(interest.status()).isEqualTo(GoogleTrendStatus.OK);
		// 93 點，最後一點 partial_data=true 被排除
		assertThat(interest.pointCount()).isEqualTo(92);
		assertThat(interest.recentAvg()).isEqualByComparingTo("40.71");
		assertThat(interest.baselineAvg()).isEqualByComparingTo("42.46");
		assertThat(interest.growthRate()).isEqualByComparingTo("-4.12");
		assertThat(interest.direction()).isEqualTo(TrendSignalTrendDirection.STABLE);
	}

	@Test
	void 實測冷門品名_Google查無結果時是查無資料而不是錯誤() throws IOException {
		TrendInterest interest = SerpApiTrendInterestProvider.parse(fixture("google_trends_niche.json"));

		assertThat(interest.status()).isEqualTo(GoogleTrendStatus.NO_DATA);
		assertThat(interest.direction()).isNull();
		assertThat(interest.growthRate()).isNull();
	}

	@Test
	void 其他錯誤訊息_例如金鑰錯誤_丟出例外且只帶SerpApi的error欄位() {
		assertThatThrownBy(() -> SerpApiTrendInterestProvider.parse("{\"error\":\"Invalid API key.\"}"))
				.isInstanceOf(TrendInterestUnavailableException.class)
				.hasMessageContaining("Invalid API key.");
	}

	@Test
	void 查詢尚未完成或格式錯誤時丟出例外() {
		assertThatThrownBy(() -> SerpApiTrendInterestProvider.parse("{\"search_metadata\":{\"status\":\"Processing\"}}"))
				.isInstanceOf(TrendInterestUnavailableException.class);
		assertThatThrownBy(() -> SerpApiTrendInterestProvider.parse("not json"))
				.isInstanceOf(TrendInterestUnavailableException.class);
	}

	@Test
	void 小於1的極低量視為0() {
		String body = "{\"search_metadata\":{\"status\":\"Success\"},\"interest_over_time\":{\"timeline_data\":["
				+ String.join(",", Collections.nCopies(20,
						"{\"values\":[{\"value\":\"<1\"}]}"))
				+ "]}}";
		// 全部是 0 → 查無資料
		assertThat(SerpApiTrendInterestProvider.parse(body).status()).isEqualTo(GoogleTrendStatus.NO_DATA);
	}

	// ========================= 換算規則 =========================

	private static List<Integer> series(int baselineValue, int baselineDays, int recentValue) {
		List<Integer> values = new ArrayList<>(Collections.nCopies(baselineDays, baselineValue));
		values.addAll(Collections.nCopies(TrendInterestCalculator.RECENT_POINTS, recentValue));
		return values;
	}

	@Test
	void 成長超過15百分比為上升_低於負15為下降() {
		TrendInterest up = TrendInterestCalculator.fromSeries(series(40, 28, 50));
		assertThat(up.growthRate()).isEqualByComparingTo(new BigDecimal("25.00"));
		assertThat(up.direction()).isEqualTo(TrendSignalTrendDirection.UP);

		TrendInterest down = TrendInterestCalculator.fromSeries(series(40, 28, 30));
		assertThat(down.growthRate()).isEqualByComparingTo(new BigDecimal("-25.00"));
		assertThat(down.direction()).isEqualTo(TrendSignalTrendDirection.DOWN);

		assertThat(TrendInterestCalculator.fromSeries(series(40, 28, 44)).direction())
				.isEqualTo(TrendSignalTrendDirection.STABLE);
	}

	@Test
	void 基準期只取近期之前的28點_更早的資料不影響() {
		List<Integer> values = new ArrayList<>(Collections.nCopies(50, 100));
		values.addAll(series(40, 28, 40));
		assertThat(TrendInterestCalculator.fromSeries(values).growthRate()).isEqualByComparingTo("0");
	}

	@Test
	void 基準期為0而近期有量時成長率無法定義_方向為上升() {
		TrendInterest interest = TrendInterestCalculator.fromSeries(series(0, 28, 10));
		assertThat(interest.growthRate()).isNull();
		assertThat(interest.direction()).isEqualTo(TrendSignalTrendDirection.UP);
	}

	@Test
	void 資料點太少或全為0時是查無資料() {
		assertThat(TrendInterestCalculator.fromSeries(series(40, 3, 40)).status()).isEqualTo(GoogleTrendStatus.NO_DATA);
		assertThat(TrendInterestCalculator.fromSeries(series(0, 28, 0)).status()).isEqualTo(GoogleTrendStatus.NO_DATA);
	}
}
