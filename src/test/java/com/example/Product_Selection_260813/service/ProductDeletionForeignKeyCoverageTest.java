package com.example.Product_Selection_260813.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 守住 ProductService.deleteProduct() 的外鍵覆蓋率（2026-09-29 fk_gbr_product 根源修正）。
 *
 * 刪除商品已經三度撞外鍵（product_evaluations → google_trend_signals → group_buy_records），
 * 每次都是「有人新增了參照 products 的表，沒人回頭看刪除流程」。這支測試掃描所有
 * Flyway migration，找出每一個 REFERENCES `products` 的外鍵，與下方清單比對：
 * 新增外鍵卻沒登記就失敗，逼開發者當下決定這張表在刪除商品時該怎麼處理。
 *
 * 登記方式：先在 deleteProduct() 的 A／B／C／D 分類註解與實作中處理，再把外鍵名稱加進清單。
 */
class ProductDeletionForeignKeyCoverageTest {

	private static final Path MIGRATION_DIR = Paths.get("src/main/resources/db/migration");

	/** 外鍵名稱 → 對應 deleteProduct() 分類註解的處理方式（僅供閱讀，比對只看 key）。 */
	private static final Set<String> HANDLED_FOREIGN_KEYS = Set.of(
			// A. 擋下（409）
			"fk_review_records_product",
			"fk_product_export_logs_product",
			"fk_product_resale_reference",
			// B. 解除連結
			"fk_gbr_product",
			// C. 隨商品刪除
			"fk_ai_analyses_product",
			"fk_product_evaluations_product",
			"fk_trend_signals_product",
			"fk_google_trend_signals_product",
			// D. 資料庫層 ON DELETE CASCADE／SET NULL
			"fk_product_custom_field_values_product",
			"fk_discovered_items_converted_product");

	private static final Pattern PRODUCT_FK = Pattern.compile(
			"CONSTRAINT\\s+`(\\w+)`\\s+FOREIGN\\s+KEY\\s*\\([^)]*\\)\\s*REFERENCES\\s+`?products`?\\s*\\(",
			Pattern.CASE_INSENSITIVE);

	@Test
	void everyForeignKeyToProductsIsHandledByDeleteProduct() throws IOException {
		Set<String> found = scanProductForeignKeys();

		assertThat(found).isNotEmpty();
		Set<String> unhandled = new TreeSet<>(found);
		unhandled.removeAll(HANDLED_FOREIGN_KEYS);
		assertThat(unhandled)
				.as("新增了參照 products 的外鍵，請先在 ProductService.deleteProduct() 決定處理方式，"
						+ "再登記到 HANDLED_FOREIGN_KEYS：" + unhandled)
				.isEmpty();
	}

	@Test
	void handledListHasNoStaleEntries() throws IOException {
		Set<String> stale = new TreeSet<>(HANDLED_FOREIGN_KEYS);
		stale.removeAll(scanProductForeignKeys());
		assertThat(stale).as("清單中的外鍵在 migration 中已不存在（改名或移除），請同步清單：" + stale).isEmpty();
	}

	private static Set<String> scanProductForeignKeys() throws IOException {
		Set<String> names = new TreeSet<>();
		try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
				Matcher m = PRODUCT_FK.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (m.find()) {
					names.add(m.group(1));
				}
			}
		}
		return names;
	}
}
