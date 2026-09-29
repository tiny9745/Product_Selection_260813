package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExistingProductMatcherTest {

	private static List<String> names(String... raw) {
		return java.util.Arrays.stream(raw).map(DiscoveryText::normalize).toList();
	}

	@Test
	void 名稱相同或只差空白大小寫_視為既有商品() {
		assertThat(ExistingProductMatcher.matchesAny(DiscoveryText.normalize("義美 小泡芙"), names("義美小泡芙"))).isTrue();
		assertThat(ExistingProductMatcher.matchesAny(DiscoveryText.normalize("dyson v15"), names("Dyson V15"))).isTrue();
	}

	@Test
	void 既有商品名稱多了規格字樣_仍視為同一件() {
		assertThat(ExistingProductMatcher.matchesAny(DiscoveryText.normalize("義美小泡芙"), names("義美小泡芙 巧克力"))).isTrue();
	}

	@Test
	void 既有商品是籠統名稱_不吃掉更具體的新品() {
		// 「氣炸鍋」只占「飛利浦 XXL 氣炸鍋」的一小部分，包含比例不足
		assertThat(ExistingProductMatcher.matchesAny(DiscoveryText.normalize("飛利浦XXL氣炸鍋"), names("氣炸鍋"))).isFalse();
	}

	@Test
	void 完全不同的商品_不相符() {
		assertThat(ExistingProductMatcher.matchesAny(DiscoveryText.normalize("義美小泡芙"), names("Dyson V15", "日式蜂蜜蛋糕")))
				.isFalse();
		assertThat(ExistingProductMatcher.matchesAny("", names("義美小泡芙"))).isFalse();
	}
}
