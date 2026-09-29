package com.example.Product_Selection_260813.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscoveryTextTest {

	@Test
	void 正規化_全形半形大小寫空白標點都視為相同() {
		assertThat(DiscoveryText.normalize("Dyson  V15")).isEqualTo("dysonv15");
		assertThat(DiscoveryText.normalize("ＤＹＳＯＮ ｖ１５")).isEqualTo("dysonv15");
		assertThat(DiscoveryText.normalize("dyson-v15！")).isEqualTo("dysonv15");
		assertThat(DiscoveryText.normalize("義美 小泡芙")).isEqualTo("義美小泡芙");
		assertThat(DiscoveryText.normalize(null)).isEmpty();
	}

	@Test
	void 回文與轉錄前綴會被去掉() {
		assertThat(DiscoveryText.cleanTitle("Re: [心得] 氣炸鍋")).isEqualTo("[心得] 氣炸鍋");
		assertThat(DiscoveryText.cleanTitle("Fw: Re: [情報] 小泡芙")).isEqualTo("[情報] 小泡芙");
	}

	@Test
	void 公告板務與過短標題不送給AI() {
		assertThat(DiscoveryText.isExcludedTitle("[公告] 板規修訂")).isTrue();
		assertThat(DiscoveryText.isExcludedTitle("[板務] 水桶名單")).isTrue();
		assertThat(DiscoveryText.isExcludedTitle("[問題]")).isTrue();
		assertThat(DiscoveryText.isExcludedTitle("[情報] 全聯 小泡芙 買一送一")).isFalse();
		// 拿不準的分類交給 AI 判斷，不在規則層擋掉
		assertThat(DiscoveryText.isExcludedTitle("[閒聊] 最近買的吸塵器")).isFalse();
	}
}
