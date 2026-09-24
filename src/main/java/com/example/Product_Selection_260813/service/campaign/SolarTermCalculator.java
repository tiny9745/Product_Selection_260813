package com.example.Product_Selection_260813.service.campaign;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.enums.SolarTerm;

/**
 * 節氣日期查詢（清明、冬至），範圍 2000–2099。
 *
 * 2026-09-24 決議 A：改用內建節氣表 {@code classpath:campaign/solar-terms-2000-2099.csv}
 * （天文演算太陽黃經 15° 與 270° 的時刻，換算成 UTC+8 日期），取代原規劃的壽星公式。
 * 壽星公式在 2026–2060 年間有一年不準（2054 年冬至：公式 12/22，實際 12/21），
 * 內建表沒有這個誤差；公式保留在單元測試當作交叉檢查。
 *
 * 超出範圍時丟出 IllegalArgumentException，不回傳可能錯誤的日期。若與中央氣象署
 * 公告仍有不符，以逐年覆寫表修正。
 */
@Component
public class SolarTermCalculator {

	static final String RESOURCE = "campaign/solar-terms-2000-2099.csv";

	private final Map<Integer, Map<SolarTerm, LocalDate>> byYear;

	public SolarTermCalculator() {
		this.byYear = load();
	}

	public LocalDate dateOf(SolarTerm term, int year) {
		Map<SolarTerm, LocalDate> terms = byYear.get(year);
		if (terms == null || term == null) {
			throw new IllegalArgumentException("節氣年份超出範圍（2000–2099）：" + year);
		}
		return terms.get(term);
	}

	private static Map<Integer, Map<SolarTerm, LocalDate>> load() {
		Map<Integer, Map<SolarTerm, LocalDate>> result = new HashMap<>();
		try (InputStream in = SolarTermCalculator.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("找不到節氣表：" + RESOURCE);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split(",");
				Map<SolarTerm, LocalDate> terms = new EnumMap<>(SolarTerm.class);
				terms.put(SolarTerm.QINGMING, LocalDate.parse(parts[1]));
				terms.put(SolarTerm.DONGZHI, LocalDate.parse(parts[2]));
				result.put(Integer.parseInt(parts[0]), terms);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("讀取節氣表失敗", e);
		}
		return Map.copyOf(result);
	}
}
