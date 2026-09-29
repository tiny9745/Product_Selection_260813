package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.entity.AudienceProfile;
import com.example.Product_Selection_260813.entity.DiscoveredItem;
import com.example.Product_Selection_260813.enums.GateStatus;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.FitResult;
import com.example.Product_Selection_260813.service.discovery.DiscoveryFitRules.VerifiedFit;

class DiscoveryFitRulesTest {

	private static AudienceProfile profile(long id, int version, String name) {
		AudienceProfile profile = new AudienceProfile();
		profile.setId(id);
		profile.setVersion(version);
		profile.setName(name);
		return profile;
	}

	@Test
	void 溫層判定_支援通過_不支援不通過_缺資料不混為不通過() {
		List<String> supported = List.of("NORMAL", "CHILLED");
		assertThat(DiscoveryFitRules.temperatureGate("NORMAL", supported)).isEqualTo(GateStatus.PASSED);
		assertThat(DiscoveryFitRules.temperatureGate("FROZEN", supported)).isEqualTo(GateStatus.FAILED);
		assertThat(DiscoveryFitRules.temperatureGate(null, supported)).isEqualTo(GateStatus.INSUFFICIENT_DATA);
		assertThat(DiscoveryFitRules.temperatureGate("NORMAL", List.of())).isEqualTo(GateStatus.INSUFFICIENT_DATA);
	}

	@Test
	void 客群簽章依id排序_版本改變就需要重評() {
		String signature = DiscoveryFitRules.audienceSignature(List.of(profile(2, 1, "B"), profile(1, 3, "A")));
		assertThat(signature).isEqualTo("1@3,2@1");

		DiscoveredItem item = new DiscoveredItem();
		assertThat(DiscoveryFitRules.needsFitEvaluation(item, signature)).isTrue();
		item.setFitEvaluatedAt(LocalDateTime.now());
		item.setFitAudienceSig(signature);
		assertThat(DiscoveryFitRules.needsFitEvaluation(item, signature)).isFalse();
		assertThat(DiscoveryFitRules.needsFitEvaluation(item, "1@4,2@1")).isTrue();
	}

	@Test
	void 驗證評分_只收送出去的id_分數夾在0到100_疑慮最多3個() {
		Map<String, VerifiedFit> verified = DiscoveryFitRules.verifyFit(List.of(
				new FitResult("d1", 130, " 很適合 ", List.of("a", "b", "c", "d")),
				new FitResult("d1", 10, "重複的第二筆", List.of()),
				new FitResult("d2", -5, "不適合", List.of("單價、運費")),
				new FitResult("d99", 90, "不是這批的", List.of())), Set.of("d1", "d2", "d3"));

		assertThat(verified).containsOnlyKeys("d1", "d2");
		assertThat(verified.get("d1")).isEqualTo(new VerifiedFit(100, "很適合", "a、b、c"));
		// 疑慮裡的頓號會被換掉，避免與分隔符號混淆
		assertThat(verified.get("d2")).isEqualTo(new VerifiedFit(0, "不適合", "單價／運費"));
	}

	@Test
	void 客群背景只寫有填的欄位_沒設定客群時明確告知() {
		AudienceProfile profile = profile(1, 1, "雙薪家庭");
		profile.setAgeMin(30);
		profile.setAgeMax(45);
		String context = DiscoveryFitRules.describeContext(List.of(profile), List.of("NORMAL", "FROZEN"));
		assertThat(context).contains("雙薪家庭").contains("30～45 歲").contains("常溫、冷凍");
		assertThat(context).doesNotContain("偏好").doesNotContain("關鍵字");

		assertThat(DiscoveryFitRules.describeContext(List.of(), List.of())).contains("客群未設定");
	}
}
