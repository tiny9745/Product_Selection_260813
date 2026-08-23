package com.example.Product_Selection_260813.common.exception;

/**
 * 帳號密碼正確，但帳號已被停用（enabled=false）。
 *
 * 與InvalidCredentialsException分開的原因：這個例外只會在「密碼已驗證通過」之後才拋出
 * （見AuthService.login()的檢查順序），此時對方必須先猜對密碼才會看到這個訊息，
 * 不構成User Enumeration風險，因此可以給出比「帳號或密碼錯誤」更明確的提示。
 */
public class AccountDisabledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountDisabledException() {
        super("帳號已被停用，請聯繫管理員");
    }
}
