package com.example.Product_Selection_260813.service.groupbuy;

import java.util.ArrayList;
import java.util.List;

/**
 * 最小 CSV 解析器。
 *
 * 專案原則是「不任意新增 Framework」，而 Spring Boot 沒有內建 CSV 解析器。
 * 匯入開團紀錄只需要處理單純的表格資料，為此引入 opencsv 或 commons-csv
 * 這類相依並不划算，因此自行實作最小可用版本。
 *
 * 支援的語法（RFC 4180 子集）：
 * <ul>
 * <li>逗號分隔</li>
 * <li>雙引號包住的欄位，內部可含逗號與換行</li>
 * <li>雙引號內以連續兩個雙引號代表一個雙引號字元</li>
 * <li>CRLF 與 LF 兩種換行</li>
 * <li>自動去除 UTF-8 BOM——Excel 另存新檔的 CSV 幾乎一定會帶 BOM，
 *     不處理的話第一個欄位名稱會變成「\ufeffproduct_type_id」而比對失敗，
 *     這是實務上最常見的匯入失敗原因</li>
 * </ul>
 *
 * 不支援的語法（超出需求範圍，遇到時當一般字元處理）：分號分隔、跳脫反斜線。
 */
public final class CsvParser {

	private static final char DELIMITER = ',';
	private static final char QUOTE = '"';
	private static final char BOM = '\uFEFF';

	private CsvParser() {
	}

	/**
	 * 解析整份 CSV。
	 *
	 * @return 逐列的欄位陣列；空白列（全為空字串）會被略過，避免檔案結尾的
	 *         空行被當成一筆缺資料的紀錄而讓整批匯入失敗
	 */
	public static List<List<String>> parse(String content) {
		List<List<String>> rows = new ArrayList<>();
		if (content == null || content.isEmpty()) {
			return rows;
		}
		// 去掉 UTF-8 BOM
		if (content.charAt(0) == BOM) {
			content = content.substring(1);
		}

		List<String> current = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);

			if (inQuotes) {
				if (c == QUOTE) {
					// 連續兩個雙引號 = 一個字面雙引號
					if (i + 1 < content.length() && content.charAt(i + 1) == QUOTE) {
						field.append(QUOTE);
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					field.append(c);
				}
				continue;
			}

			switch (c) {
			case QUOTE -> inQuotes = true;
			case DELIMITER -> {
				current.add(field.toString().trim());
				field.setLength(0);
			}
			case '\r' -> {
				// CRLF：吃掉 \r，交給下一輪的 \n 處理換行
			}
			case '\n' -> {
				current.add(field.toString().trim());
				field.setLength(0);
				addIfNotBlank(rows, current);
				current = new ArrayList<>();
			}
			default -> field.append(c);
			}
		}

		// 檔案結尾沒有換行時，最後一列還在暫存區
		current.add(field.toString().trim());
		addIfNotBlank(rows, current);
		return rows;
	}

	private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
		boolean allBlank = row.stream().allMatch(s -> s == null || s.isEmpty());
		if (!allBlank) {
			rows.add(new ArrayList<>(row));
		}
	}
}
