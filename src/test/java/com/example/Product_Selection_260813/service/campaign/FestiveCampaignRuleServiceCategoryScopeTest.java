package com.example.Product_Selection_260813.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.Product_Selection_260813.constants.ValidationMessage;
import com.example.Product_Selection_260813.dto.request.FestiveCampaignRuleFields;
import com.example.Product_Selection_260813.enums.CampaignDateRuleType;
import com.example.Product_Selection_260813.entity.FestiveCampaign;
import com.example.Product_Selection_260813.enums.FestiveCategory;

/**
 * 2026-09-24：節慶／季節各自適用的欄位範圍。
 * <ul>
 * <li>地域：節慶不帶地域屬性（一律全國），只有季節型可以指定受影響區域。</li>
 * <li>偏移天數：只適用節慶型；季節型開始月日可直接填，送非 0 偏移一律拒絕，舊資料推算時視為 0。</li>
 * </ul>
 * validateRule() 不碰資料庫，Repository 傳 null 即可測。
 */
class FestiveCampaignRuleServiceCategoryScopeTest {

	private final FestiveCampaignRuleService service = new FestiveCampaignRuleService(null, null, null, null, null,
			null);

	private static FestiveCampaignRuleFields festival(List<String> regions) {
		FestiveCampaignRuleFields fields = new FestiveCampaignRuleFields();
		fields.setCategory(FestiveCategory.FESTIVAL);
		fields.setDateRuleType(CampaignDateRuleType.LUNAR_DATE);
		fields.setRuleMonth(5);
		fields.setRuleDay(5);
		fields.setDurationDays(3);
		fields.setRegions(regions);
		return fields;
	}

	private static FestiveCampaignRuleFields season(List<String> regions) {
		FestiveCampaignRuleFields fields = new FestiveCampaignRuleFields();
		fields.setCategory(FestiveCategory.SEASON);
		fields.setRuleMonth(12);
		fields.setRuleDay(1);
		fields.setEndMonth(2);
		fields.setEndDay(28);
		fields.setRegions(regions);
		return fields;
	}

	@Test
	void 節慶不指定地域可通過() {
		FestiveCampaignRuleFields fields = festival(null);
		service.validateRule(fields);
		assertThat(fields.getRegions()).isEmpty();
	}

	@Test
	void 節慶指定地域被拒絕() {
		assertThatThrownBy(() -> service.validateRule(festival(List.of("SOUTH"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(ValidationMessage.CAMPAIGN_REGION_SEASON_ONLY);
	}

	@Test
	void 季節可指定地域並去除重複() {
		FestiveCampaignRuleFields fields = season(List.of("NORTH", "EAST", "NORTH"));
		service.validateRule(fields);
		assertThat(fields.getRegions()).containsExactly("NORTH", "EAST");
	}

	@Test
	void 無效區域代碼仍優先回報代碼錯誤() {
		assertThatThrownBy(() -> service.validateRule(festival(List.of("WEST"))))
				.hasMessage(ValidationMessage.CAMPAIGN_REGION_INVALID);
	}

	@Test
	void 季節帶偏移天數被拒絕() {
		FestiveCampaignRuleFields fields = season(List.of());
		fields.setRuleOffsetDays(-3);
		assertThatThrownBy(() -> service.validateRule(fields))
				.hasMessage(ValidationMessage.CAMPAIGN_OFFSET_FESTIVAL_ONLY);
	}

	@Test
	void 季節未帶偏移視為零可通過() {
		FestiveCampaignRuleFields fields = season(List.of());
		service.validateRule(fields);
		assertThat(fields.getRuleOffsetDays()).isEqualTo(0);
	}

	@Test
	void 節慶偏移照常保留() {
		FestiveCampaignRuleFields fields = festival(null);
		fields.setRuleOffsetDays(-1);
		service.validateRule(fields);
		assertThat(CampaignDateRules.of(fields).offsetDays()).isEqualTo(-1);
	}

	@Test
	void 季節舊資料的偏移推算時視為零() {
		FestiveCampaign legacy = new FestiveCampaign();
		legacy.setCategory(FestiveCategory.SEASON);
		legacy.setDateRuleType(CampaignDateRuleType.FIXED_DATE);
		legacy.setRuleMonth(12);
		legacy.setRuleDay(1);
		legacy.setEndMonth(2);
		legacy.setEndDay(28);
		legacy.setRuleOffsetDays(5);
		assertThat(CampaignDateRules.of(legacy).offsetDays()).isEqualTo(0);
	}
}
