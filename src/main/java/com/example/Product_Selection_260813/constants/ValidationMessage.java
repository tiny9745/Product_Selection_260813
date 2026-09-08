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
}