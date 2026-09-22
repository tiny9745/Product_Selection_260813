package com.example.Product_Selection_260813.constants;

public class ValidationMessage {
	// Auth
	public static final String AUTH_USER_NAME = "帳號不可為空";
	public static final String AUTH_PASSWORD = "密碼不可為空";
	
	// Product（規格驗證：長度）
	public static final String PRODUCT_TYPE_ID_NULL = "商品類型不可為空";
	public static final String PRODUCT_PRICING_TYPE_NULL = "商品分流不可為空";
	public static final String PRODUCT_NAME_NULL = "商品名稱不可為空";
	public static final String PRODUCT_NAME_TOO_LONG = "商品名稱長度不可超過100字元";
	/**
	 * ⚠️ 2026-09-19補上：目標客群描述直接餵給 ScoringService.scoreAudienceMatch()
	 * 計算客群契合度分數（七大計分因子之一），前端表單也標成必填（<em>*</em>）、
	 * 限制 500 字。但後端 DTO 原本完全沒有 @NotBlank／@Size，只要繞過前端
	 * 直接呼叫 API，就能建立一筆這個必要計分輸入是 null 的商品，讓客群契合度
	 * 分數永遠算不出來，而且沒有任何錯誤訊息會告訴呼叫端問題出在哪。
	 * @Size 上限用跟前端一致的 500 字，避免有人送一個沒有上限的超大字串。
	 */
	public static final String PRODUCT_TARGET_CUSTOMER_NULL = "目標客群描述不可為空";
	public static final String PRODUCT_TARGET_CUSTOMER_TOO_LONG = "目標客群描述長度不可超過500字元";
	public static final String PRODUCT_SUPPLIER_NAME_TOO_LONG = "供應商名稱長度不可超過100字元";
	public static final String PRODUCT_IMAGE_URL_TOO_LONG = "商品圖片路徑長度不可超過500字元";
	public static final String PRODUCT_CAMPAIGN_TAGS_TOO_LONG = "節慶標籤長度不可超過255字元";

	// Product（規格驗證：數值範圍）
	// 對應products資料表 decimal(10,2)／decimal(5,2) 的欄位精度，
	// 以及supply_stability／price_competitiveness的1~5分制設計。
	public static final String PRODUCT_COST_PRICE_NEGATIVE = "成本價不可為負數";
	public static final String PRODUCT_COST_PRICE_OVER_DIGITS = "成本價整數位不可超過8位、小數不可超過2位";
	public static final String PRODUCT_SALE_PRICE_NEGATIVE = "團購售價不可為負數";
	public static final String PRODUCT_SALE_PRICE_OVER_DIGITS = "團購售價整數位不可超過8位、小數不可超過2位";
	public static final String PRODUCT_MARKET_PRICE_NEGATIVE = "市售價格不可為負數";
	public static final String PRODUCT_MARKET_PRICE_OVER_DIGITS = "市售價格整數位不可超過8位、小數不可超過2位";
	public static final String PRODUCT_MOQ_NEGATIVE = "最低訂購量不可為負數";
	public static final String PRODUCT_SUPPLY_STABILITY_RANGE = "供應穩定性評估須介於1至5之間";
	public static final String PRODUCT_PRICE_COMPETITIVENESS_RANGE = "價格競爭力評估須介於1至5之間";
	public static final String PRODUCT_ESTIMATED_PURCHASE_RATE_RANGE = "預估購買率須介於0至1之間";

	// Product（商業邏輯驗證，於Service層攔截）
	public static final String PRODUCT_COST_OVER_SALE = "成本價不可高於團購售價";
	public static final String PRODUCT_RESALE_SALE_PRICE_ZERO = "再販售商品的團購售價不可為0";
	public static final String PRODUCT_TYPE_INACTIVE = "此商品類型已停用，無法選用";

	// Settings（商業邏輯驗證，於Service層攔截）
	public static final String CAMPAIGN_DATE_RANGE_INVALID = "檔期開始日期不可晚於結束日期";
	public static final String CAMPAIGN_LEAD_DAYS_NEGATIVE = "準備期天數不可為負數";
	public static final String CAMPAIGN_CODE_TOO_LONG = "檔期代碼長度不可超過50字元";
	public static final String CAMPAIGN_NAME_TOO_LONG = "檔期名稱長度不可超過100字元";
	public static final String AUDIENCE_AGE_RANGE_INVALID = "最小年齡不可大於最大年齡";
	public static final String AUDIENCE_AGE_MIN_RANGE = "最小年齡須介於0至150之間";
	public static final String AUDIENCE_AGE_MAX_RANGE = "最大年齡須介於0至150之間";
	public static final String AUDIENCE_NAME_TOO_LONG = "核心客群名稱長度不可超過100字元";
	public static final String AUDIENCE_KEYWORDS_TOO_LONG = "客群關鍵字長度不可超過500字元";
	public static final String PRODUCT_TYPE_NAME_TOO_LONG = "商品類型名稱長度不可超過50字元";
	public static final String PRODUCT_TYPE_DESCRIPTION_TOO_LONG = "商品類型說明長度不可超過255字元";
	public static final String RISK_OPTION_NAME_TOO_LONG = "風險選項名稱長度不可超過100字元";
	public static final String RISK_OPTION_DESCRIPTION_TOO_LONG = "風險選項說明長度不可超過255字元";
	public static final String RISK_OPTION_ALERT_KEYWORDS_TOO_LONG = "風險提示關鍵字長度不可超過500字元";

	// EvaluationFactor（自訂模式權重編輯）
	public static final String FACTOR_WEIGHTS_EMPTY = "權重清單不可為空";
	public static final String FACTOR_CODE_BLANK = "因子代碼不可為空";
	public static final String FACTOR_WEIGHT_NULL = "權重不可為空";
	public static final String FACTOR_WEIGHT_RANGE = "單項權重須介於0至100之間";
	public static final String FACTOR_WEIGHT_OVER_DIGITS = "權重整數位不可超過3位、小數不可超過2位";
	public static final String FACTOR_MODE_NOT_EDITABLE = "此為系統固定模式，不允許調整權重；請改用自訂模式";
	// 2026-09-20修正：原文寫死「七項」，自訂因子（factor_definitions）上線後
	// 總項數不再固定是七項，訊息裡不再寫死具體數字，避免顯示錯誤的項數。
	public static final String FACTOR_SUM_NOT_100 = "全部因子權重加總須為100，目前為：";
	public static final String FACTOR_CODE_UNKNOWN = "無法辨識的因子代碼：";
	public static final String FACTOR_CODE_DUPLICATE = "因子代碼重複：";
	public static final String FACTOR_CODE_MISSING = "缺少必要的因子：";
	public static final String FACTOR_DEFINITION_CODE_DUPLICATE = "因子代碼已存在（含既有七個固定因子）：";
	public static final String FACTOR_DEFINITION_STRATEGY_NOT_IMPLEMENTED = "此運算邏輯尚未實作：";
	public static final String FACTOR_DEFINITION_DATA_SOURCE_INCOMPATIBLE = "此資料源不支援所選的運算邏輯，請改選：";
	public static final String FACTOR_DEFINITION_SOURCE_XOR_VIOLATION = "dataSourceCode 與 customFieldDefinitionId 必須恰好擇一提供";
	public static final String FACTOR_DEFINITION_NOT_FOUND = "自訂因子不存在：";
	// V14新增：編輯／版本鏈相關驗證訊息
	public static final String FACTOR_DEFINITION_SUPERSEDED_CANNOT_ENABLE = "此版本已被新版本取代，無法重新啟用，請直接編輯目前生效的版本：";

	public static final String CUSTOM_FIELD_CODE_DUPLICATE = "欄位代碼已存在：";
	public static final String CUSTOM_FIELD_NOT_FOUND = "自訂商品屬性不存在：";
	public static final String CUSTOM_FIELD_ROOT_TYPE_INVALID = "品類範圍必須是大類（不可為小類）：";
	public static final String CUSTOM_FIELD_REQUIRED_MISSING = "以下自訂屬性為必填，請填寫：";
	public static final String CUSTOM_FIELD_NOT_APPLICABLE = "此欄位不存在，或已停用，或不適用這個商品的品類：";
	public static final String CUSTOM_FIELD_VALUE_INVALID = "自訂屬性數值不合法：";
	// V14新增：編輯／版本鏈／1~5分數說明相關驗證訊息
	public static final String CUSTOM_FIELD_SUPERSEDED_CANNOT_ENABLE = "此題目已被新版本取代，無法重新啟用，請直接編輯目前生效的版本：";
	public static final String CUSTOM_FIELD_SCALE_LABEL_NOT_APPLICABLE = "只有1~5分數型態（SCALE_1_5）可以設定分數說明";
	public static final String CUSTOM_FIELD_SCALE_LABEL_KEY_INVALID = "分數說明的分數必須介於1至5之間：";

	// Review（商業邏輯驗證，於Service層攔截）
	public static final String REVIEW_RISK_OPTION_INACTIVE = "風險選項已停用，無法選取：";
	public static final String REVIEW_REJECT_REASON_REQUIRED = "退件時請至少勾選一項風險或填寫審核備註";
	
	// Review（規格驗證）
	public static final String REVIEW_PRODUCT_ID_NULL = "商品編號不可為空";
	public static final String REVIEW_STATUS_NULL = "審核結果不可為空";
	
	// User（帳號管理）
	public static final String USER_USERNAME_BLANK = "登入帳號不可為空";
	public static final String USER_USERNAME_TOO_LONG = "登入帳號長度不可超過50字元";
	public static final String USER_NAME_BLANK = "顯示名稱不可為空";
	public static final String USER_NAME_TOO_LONG = "顯示名稱長度不可超過50字元";
	public static final String USER_ROLE_NULL = "使用者角色不可為空";
	public static final String USER_PASSWORD_BLANK = "密碼不可為空";
	public static final String USER_PASSWORD_TOO_SHORT = "密碼長度至少需8個字元";

	// User（自身資料修改，團隊既有功能，非本次異動範圍）
	public static final String USER_CURRENT_PASSWORD_BLANK = "目前密碼不可為空";
	public static final String USER_NEW_PASSWORD_SAME_AS_OLD = "新密碼不可與目前密碼相同";

	// WeatherSignalTagMapping（2026-09-22新增：天氣訊號標籤對照可調整化）
	public static final String WEATHER_SIGNAL_TAG_TOO_LONG = "標籤長度不可超過50字元";
	public static final String WEATHER_SIGNAL_TYPE_NORMAL_NOT_ALLOWED = "一般天氣（NORMAL）不會命中任何商品，不可建立對照";
	public static final String WEATHER_SIGNAL_TAG_MAPPING_DUPLICATE = "此天氣訊號類型與標籤的組合已存在生效中的對照：";
}
