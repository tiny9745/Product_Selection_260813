package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.ExtractedItem;
import com.example.Product_Selection_260813.service.discovery.DiscoveryVerifier.VerifiedItem;

class DiscoveryVerifierTest {

	private static final Map<String, String> TITLES = Map.of(
			"t1", "[情報] 全聯 義美小泡芙 買一送一",
			"t2", "[心得] 義美 小泡芙 新口味",
			"t3", "[問題] 吸塵器推薦");

	@Test
	void 名稱確實出現在引用的標題裡_保留並去掉不相符的引用() {
		VerifiedItem result = DiscoveryVerifier.verify(
				new ExtractedItem("義美小泡芙", "義美小泡芙", "零食/餅乾", List.of("t1", "t2", "t3")), TITLES);

		assertThat(result).isNotNull();
		assertThat(result.normalizedName()).isEqualTo("義美小泡芙");
		// t2 寫成「義美 小泡芙」，正規化後相同 → 保留；t3 根本沒提到 → 去掉
		assertThat(result.titleIds()).containsExactly("t1", "t2");
	}

	@Test
	void AI捏造標題裡沒有的商品_整筆丟棄() {
		assertThat(DiscoveryVerifier.verify(
				new ExtractedItem("Dyson V15", "Dyson V15", null, List.of("t3")), TITLES)).isNull();
	}

	@Test
	void 引用不存在的標題編號_整筆丟棄() {
		assertThat(DiscoveryVerifier.verify(
				new ExtractedItem("義美小泡芙", "義美小泡芙", null, List.of("t99")), TITLES)).isNull();
	}

	@Test
	void 名稱過短或過長_丟棄() {
		assertThat(DiscoveryVerifier.verify(new ExtractedItem("泡", "泡", null, List.of("t1")), TITLES)).isNull();
		String tooLong = "義".repeat(DiscoveryVerifier.MAX_NAME_LENGTH + 1);
		assertThat(DiscoveryVerifier.verify(new ExtractedItem(tooLong, "小泡芙", null, List.of("t1")), TITLES)).isNull();
	}

	@Test
	void 空白品類視為沒有品類() {
		VerifiedItem result = DiscoveryVerifier.verify(
				new ExtractedItem("義美小泡芙", "小泡芙", "  ", List.of("t1")), TITLES);
		assertThat(result.categoryHint()).isNull();
	}
}
