package com.example.Product_Selection_260813.service.campaign;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 農曆 → 國曆換算（2026-09-24 決議 A：內建對照表，取代原規劃的 ICU4J）。
 *
 * <h2>為什麼不用 ICU4J</h2>
 * 實測 ICU4J 78.3 的 ChineseCalendar 把 2027 年春節算成 2/7、2030 年算成 2/2，
 * 但人事行政總處 116 年辦公日曆表公告 2027 年春節為 2/6（2030 年實際為 2/3）。
 * 這兩年的朔都落在 UTC+8 午夜前後幾分鐘（2027-02-06 23:56、2030-02-03 00:07），
 * ICU 的天文近似在這種邊界判錯日。台灣官方曆以 UTC+8 的朔日為準，錯一天就會讓
 * 節慶檔期整期偏移，所以改用內建表，也省下約 15 MB 的依賴。
 *
 * <h2>對照表來源與驗證</h2>
 * {@code classpath:campaign/lunar-calendar-2000-2099.csv}：以天文演算（朔與中氣，UTC+8；
 * 冬至所在月為十一月，歲內 13 個月時第一個無中氣月為閏月）產生，並與以香港天文台
 * 曆法資料為基礎的獨立實作逐日比對 2000–2099 共 36,529 天，零差異；再以人事總處
 * 115、116 年辦公日曆表公告的春節、端午、中秋抽查。
 *
 * 每列格式：{@code 農曆年,正月初一國曆日期,閏月(0=無),各月大小(L=30天、S=29天，閏月緊接在同號月之後)}。
 */
@Component
public class LunarCalendarService {

	static final String RESOURCE = "campaign/lunar-calendar-2000-2099.csv";

	private record LunarYear(LocalDate newYearDate, int leapMonth, int[] monthLengths) {
	}

	private final Map<Integer, LunarYear> years;

	public LunarCalendarService() {
		this.years = load();
	}

	/**
	 * @param lunarCycleYear 農曆年，以該年正月初一所在的國曆年表示
	 * @param lunarMonth     農曆月 1–12（一律指非閏月）
	 * @param lunarDay       農曆日 1–30；超過該月天數時取月末（例：除夕寫 12/30，小月時得 12/29）
	 * @throws IllegalArgumentException 年份超出對照表範圍（2000–2099）或月日不合法
	 */
	public LocalDate toGregorian(int lunarCycleYear, int lunarMonth, int lunarDay) {
		LunarYear year = years.get(lunarCycleYear);
		if (year == null) {
			throw new IllegalArgumentException("農曆年超出對照表範圍（2000–2099）：" + lunarCycleYear);
		}
		if (lunarMonth < 1 || lunarMonth > 12 || lunarDay < 1 || lunarDay > 30) {
			throw new IllegalArgumentException("農曆日期不存在：" + lunarMonth + "/" + lunarDay);
		}
		// 閏月排在同號月之後，所以閏月之後的月份索引要多跳一格。
		int index = lunarMonth - 1 + (year.leapMonth() > 0 && lunarMonth > year.leapMonth() ? 1 : 0);
		int daysBefore = 0;
		for (int i = 0; i < index; i++) {
			daysBefore += year.monthLengths()[i];
		}
		int day = Math.min(lunarDay, year.monthLengths()[index]);
		return year.newYearDate().plusDays(daysBefore + day - 1L);
	}

	private static Map<Integer, LunarYear> load() {
		Map<Integer, LunarYear> result = new HashMap<>();
		try (InputStream in = LunarCalendarService.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("找不到農曆對照表：" + RESOURCE);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split(",");
				String pattern = parts[3];
				int[] lengths = new int[pattern.length()];
				for (int i = 0; i < pattern.length(); i++) {
					lengths[i] = pattern.charAt(i) == 'L' ? 30 : 29;
				}
				result.put(Integer.parseInt(parts[0]),
						new LunarYear(LocalDate.parse(parts[1]), Integer.parseInt(parts[2]), lengths));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("讀取農曆對照表失敗", e);
		}
		return Map.copyOf(result);
	}
}
