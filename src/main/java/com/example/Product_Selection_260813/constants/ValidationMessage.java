package com.example.Product_Selection_260813.constants;

public class ValidationMessage {
	// Auth
	public static final String AUTH_USER_NAME = "帳號不可為空";
	public static final String AUTH_PASSWORD = "密碼不可為空";
	
	// Product
	public static final String PRODUCT_TYPE_ID_NULL = "商品類型不可為空";
	public static final String PRODUCT_PRICING_TYPE_NULL = "商品分流不可為空";
	public static final String PRODUCT_NAME_NULL = "商品名稱不可為空";
	
	// Review
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
