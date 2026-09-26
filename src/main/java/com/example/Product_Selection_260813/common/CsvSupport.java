package com.example.Product_Selection_260813.common;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV 輸出共用規則（2026-09-26 由 ProductExportService 抽出，決策紀錄匯出一起用）：
 * UTF-8 含 BOM（Excel 開啟中文不亂碼）、CRLF 換行、RFC 4180 跳脫、文字欄位防公式注入。
 * 兩個匯出必須長得一樣，規則只維護這一份。
 */
public final class CsvSupport {

	/** Excel 開啟 UTF-8 CSV 需要 BOM，否則中文會變亂碼。 */
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
	private static final String LINE_BREAK = "\r\n";

	private CsvSupport() {
	}

	/**
	 * 文字欄位防 CSV 公式注入：以 = + - @ 或 Tab／CR 開頭的內容，Excel 開啟時會當成公式執行。
	 * 使用者輸入的文字前面補一個單引號讓 Excel 當成純文字。
	 * 只套用在文字欄位；數字欄位（例如負毛利率）不經過這裡，維持可計算的數值。
	 */
	public static String text(String value) {
		if (value == null) {
			return "";
		}
		if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
			return "'" + value;
		}
		return value;
	}

	/** 數字欄位：去掉尾端多餘的 0，null 留白（不是 0）。 */
	public static String number(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	/** RFC 4180：含逗號、雙引號或換行的欄位以雙引號包住，內部雙引號重複一次。 */
	public static String escape(String field) {
		if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
			return "\"" + field.replace("\"", "\"\"") + "\"";
		}
		return field;
	}

	/** 表頭＋資料列 → 含 BOM 的 UTF-8 位元組。 */
	public static byte[] toBytes(List<String> headers, List<List<String>> rows) {
		StringBuilder csv = new StringBuilder();
		appendLine(csv, headers);
		rows.forEach(row -> appendLine(csv, row));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(UTF8_BOM);
		out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	private static void appendLine(StringBuilder csv, List<String> fields) {
		csv.append(fields.stream().map(CsvSupport::escape).collect(Collectors.joining(","))).append(LINE_BREAK);
	}
}
