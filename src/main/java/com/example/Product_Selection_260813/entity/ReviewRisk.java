package com.example.Product_Selection_260813.entity;

import com.example.Product_Selection_260813.enums.ReviewRiskSource;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 審核紀錄與風險選項的關聯。
 *
 * <b>為什麼需要 source 與 isSelected 這兩個欄位</b>：
 * Gate 判定不通過時，審核畫面會預先勾選對應的風險選項。如果系統自動勾的和
 * 主管手動勾的混在同一份清單裡，審核快照就分不出來是誰勾的——主管沒看直接
 * 送出，快照上會呈現為「主管確認的風險評估」，等於用自動化偷偷取代人工確認。
 *
 * 四種狀態都必須能被記錄：
 * <table>
 * <tr><td>SYSTEM_AUTO + isSelected=true </td><td>系統判定，主管保留</td></tr>
 * <tr><td>SYSTEM_AUTO + isSelected=false</td><td><b>系統判定，主管推翻</b></td></tr>
 * <tr><td>MANUAL + isSelected=true      </td><td>主管自行勾選</td></tr>
 * <tr><td>MANUAL + isSelected=false     </td><td>不寫入（無此風險）</td></tr>
 * </table>
 *
 * 第二種是稽核價值最高的一筆——它記錄了「系統說有問題，但主管認為可以」。
 * 事後檢討時這種紀錄比其他三種都有用。而複合主鍵原本的設計下，取消勾選
 * 就是資料不存在，這筆資訊會完全消失，所以 isSelected 不能用「不寫入」代替。
 */
@Entity
@Table(name = "review_risks")
public class ReviewRisk {

	@EmbeddedId
	private ReviewRiskId id;

	/** MANUAL=主管自行勾選 / SYSTEM_AUTO=Gate 判定帶入。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 20)
	private ReviewRiskSource source = ReviewRiskSource.MANUAL;

	/**
	 * 主管最終是否保留此項。
	 *
	 * 預設 true 是為了向後相容：本次改版前寫入的歷史紀錄沒有這一欄，
	 * migration 的 DEFAULT 1 會把它們全部視為「已勾選」，這與當時的語意一致
	 *（當時只要存在就代表勾選了）。
	 */
	@Column(name = "is_selected", nullable = false)
	private Boolean isSelected = true;

	/** 系統帶入時的具體原因，來自 GateResult.reason。主管手動勾選時為 null。 */
	@Column(name = "trigger_reason", length = 500)
	private String triggerReason;

	public ReviewRisk() {
	}

	public ReviewRiskId getId() {
		return id;
	}

	public void setId(ReviewRiskId id) {
		this.id = id;
	}

	public ReviewRiskSource getSource() {
		return source;
	}

	public void setSource(ReviewRiskSource source) {
		this.source = source;
	}

	public Boolean getIsSelected() {
		return isSelected;
	}

	public void setIsSelected(Boolean isSelected) {
		this.isSelected = isSelected;
	}

	public String getTriggerReason() {
		return triggerReason;
	}

	public void setTriggerReason(String triggerReason) {
		this.triggerReason = triggerReason;
	}
}
