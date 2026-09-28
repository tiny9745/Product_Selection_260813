package com.example.Product_Selection_260813.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.Product_Selection_260813.entity.CustomFieldDefinition;
import com.example.Product_Selection_260813.entity.FactorDefinition;
import com.example.Product_Selection_260813.entity.Product;
import com.example.Product_Selection_260813.entity.ProductCustomFieldValue;
import com.example.Product_Selection_260813.repository.CustomFieldDefinitionRepository;
import com.example.Product_Selection_260813.repository.ProductCustomFieldValueRepository;

/**
 * 2026-09-29 修正：編輯自訂屬性會建立新版本（新 id、同 fieldCode），綁定的因子改指到新 id，
 * 但舊商品的答案還在舊 id 底下。計分要改用同一個 fieldCode 的答案，否則編輯題目之後，
 * 所有舊商品的這個因子一律變成「無資料」。
 */
@ExtendWith(MockitoExtension.class)
class ProductFactorScorerCustomFieldVersionTest {

	private static final Long OLD_FIELD_ID = 1L;
	private static final Long NEW_FIELD_ID = 5L;

	@Mock
	private ProductCustomFieldValueRepository productCustomFieldValueRepository;
	@Mock
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@InjectMocks
	private ProductFactorScorer scorer;

	@Test
	void 題目被編輯成新版本後_舊版本的答案仍然餵給綁定新版本的因子() {
		when(productCustomFieldValueRepository.findByProductId(1L)).thenReturn(List.of(answer(OLD_FIELD_ID, "4")));
		when(customFieldDefinitionRepository.findAllById(List.of(NEW_FIELD_ID, OLD_FIELD_ID)))
				.thenReturn(List.of(field(OLD_FIELD_ID, "ECO_LEVEL"), field(NEW_FIELD_ID, "ECO_LEVEL")));

		Map<Long, BigDecimal> values = scorer.resolveCustomFieldValues(product(), List.of(boundFactor(NEW_FIELD_ID)));

		assertThat(values.get(NEW_FIELD_ID)).isEqualByComparingTo("4");
	}

	@Test
	void 同時有新舊版本答案時_用最新版本的答案() {
		when(productCustomFieldValueRepository.findByProductId(1L))
				.thenReturn(List.of(answer(OLD_FIELD_ID, "2"), answer(3L, "5")));
		when(customFieldDefinitionRepository.findAllById(List.of(NEW_FIELD_ID, OLD_FIELD_ID, 3L)))
				.thenReturn(List.of(field(OLD_FIELD_ID, "ECO_LEVEL"), field(3L, "ECO_LEVEL"), field(NEW_FIELD_ID, "ECO_LEVEL")));

		Map<Long, BigDecimal> values = scorer.resolveCustomFieldValues(product(), List.of(boundFactor(NEW_FIELD_ID)));

		assertThat(values.get(NEW_FIELD_ID)).isEqualByComparingTo("5");
	}

	@Test
	void 答案直接對得上綁定的題目時_不多查題目定義() {
		when(productCustomFieldValueRepository.findByProductId(1L)).thenReturn(List.of(answer(NEW_FIELD_ID, "3")));

		Map<Long, BigDecimal> values = scorer.resolveCustomFieldValues(product(), List.of(boundFactor(NEW_FIELD_ID)));

		assertThat(values.get(NEW_FIELD_ID)).isEqualByComparingTo("3");
		verify(customFieldDefinitionRepository, never()).findAllById(anyList());
	}

	@Test
	void 不同題目的答案不會被誤用() {
		when(productCustomFieldValueRepository.findByProductId(1L)).thenReturn(List.of(answer(OLD_FIELD_ID, "4")));
		when(customFieldDefinitionRepository.findAllById(List.of(NEW_FIELD_ID, OLD_FIELD_ID)))
				.thenReturn(List.of(field(OLD_FIELD_ID, "OTHER_FIELD"), field(NEW_FIELD_ID, "ECO_LEVEL")));

		Map<Long, BigDecimal> values = scorer.resolveCustomFieldValues(product(), List.of(boundFactor(NEW_FIELD_ID)));

		assertThat(values).doesNotContainKey(NEW_FIELD_ID);
	}

	// ---------------------------------------------------------------------

	private static Product product() {
		Product product = new Product();
		product.setId(1L);
		return product;
	}

	private static ProductCustomFieldValue answer(Long fieldId, String value) {
		ProductCustomFieldValue answer = new ProductCustomFieldValue();
		answer.setProductId(1L);
		answer.setFieldDefinitionId(fieldId);
		answer.setNumericValue(new BigDecimal(value));
		return answer;
	}

	private static CustomFieldDefinition field(Long id, String code) {
		CustomFieldDefinition field = new CustomFieldDefinition();
		field.setId(id);
		field.setFieldCode(code);
		return field;
	}

	private static FactorDefinition boundFactor(Long customFieldId) {
		FactorDefinition factor = new FactorDefinition();
		factor.setFactorCode("ECO_PACKAGING");
		factor.setCustomFieldDefinitionId(customFieldId);
		factor.setIsActive(true);
		return factor;
	}
}
