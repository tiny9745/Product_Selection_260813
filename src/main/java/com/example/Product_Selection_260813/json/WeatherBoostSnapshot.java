package com.example.Product_Selection_260813.json;

import java.math.BigDecimal;
import java.util.List;

/**
 * 天氣加成明細（V26）：LIVE 明細與審核快照（review_records.weather_boost_detail_snapshot）共用。
 *
 * <pre>
 * 天氣加成 = combinedScore ÷ 100 × boostCap
 * combinedScore = historyScore × historyWeight% + forecastScore × forecastWeight%
 *                 （只有一邊有資料時直接用那一邊；兩邊都沒有資料＝null、加成 0）
 * </pre>
 * 分數皆為 0~100、小數 2 位。快照保存計算當下的比重與上限，日後設定變更不影響已審核商品的重現。
 * JSON key 一律 camelCase（見 learnings：snake_case 會造成反序列化失敗）。
 */
public class WeatherBoostSnapshot {

	/** 過去 historyDays 天的歷史天氣分（四區加權後）；該期間完全沒有資料時為 null。 */
	private BigDecimal historyScore;
	/** 未來 forecastDays 天的預測天氣分；沒有預報資料時為 null。 */
	private BigDecimal forecastScore;
	/** 依比重混合後的天氣分（0~100）；兩邊都沒有資料時為 null。 */
	private BigDecimal combinedScore;
	private BigDecimal historyWeightPercentage;
	private BigDecimal forecastWeightPercentage;
	private BigDecimal boostCap;
	/** 最終天氣加成（分）。 */
	private BigDecimal weatherBoost;
	/** 商品標籤中，在計算期間內至少有一天被天氣訊號命中的標籤。 */
	private List<String> matchedTags;
	/** 實際納入計算的歷史／預報天數（任一區有資料即算一天）。 */
	private Integer historyDays;
	private Integer forecastDays;
	/** 計算期間（yyyy-MM-dd）。 */
	private String historyFrom;
	private String forecastTo;
	/** 天氣資料最近一次同步時間（ISO 日期時間字串），畫面顯示「資料更新時間」。 */
	private String dataUpdatedAt;

	public BigDecimal getHistoryScore() { return historyScore; }
	public void setHistoryScore(BigDecimal historyScore) { this.historyScore = historyScore; }
	public BigDecimal getForecastScore() { return forecastScore; }
	public void setForecastScore(BigDecimal forecastScore) { this.forecastScore = forecastScore; }
	public BigDecimal getCombinedScore() { return combinedScore; }
	public void setCombinedScore(BigDecimal combinedScore) { this.combinedScore = combinedScore; }
	public BigDecimal getHistoryWeightPercentage() { return historyWeightPercentage; }
	public void setHistoryWeightPercentage(BigDecimal historyWeightPercentage) { this.historyWeightPercentage = historyWeightPercentage; }
	public BigDecimal getForecastWeightPercentage() { return forecastWeightPercentage; }
	public void setForecastWeightPercentage(BigDecimal forecastWeightPercentage) { this.forecastWeightPercentage = forecastWeightPercentage; }
	public BigDecimal getBoostCap() { return boostCap; }
	public void setBoostCap(BigDecimal boostCap) { this.boostCap = boostCap; }
	public BigDecimal getWeatherBoost() { return weatherBoost; }
	public void setWeatherBoost(BigDecimal weatherBoost) { this.weatherBoost = weatherBoost; }
	public List<String> getMatchedTags() { return matchedTags; }
	public void setMatchedTags(List<String> matchedTags) { this.matchedTags = matchedTags; }
	public Integer getHistoryDays() { return historyDays; }
	public void setHistoryDays(Integer historyDays) { this.historyDays = historyDays; }
	public Integer getForecastDays() { return forecastDays; }
	public void setForecastDays(Integer forecastDays) { this.forecastDays = forecastDays; }
	public String getHistoryFrom() { return historyFrom; }
	public void setHistoryFrom(String historyFrom) { this.historyFrom = historyFrom; }
	public String getForecastTo() { return forecastTo; }
	public void setForecastTo(String forecastTo) { this.forecastTo = forecastTo; }
	public String getDataUpdatedAt() { return dataUpdatedAt; }
	public void setDataUpdatedAt(String dataUpdatedAt) { this.dataUpdatedAt = dataUpdatedAt; }
}
