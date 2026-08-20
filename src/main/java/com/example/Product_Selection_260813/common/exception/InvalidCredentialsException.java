package com.example.Product_Selection_260813.common.exception;

/**
 * 帳號不存在 或 密碼錯誤，統一丟這個例外。
 *
 * 刻意不區分「帳號不存在」與「密碼錯誤」：若分開，錯誤訊息的差異會讓攻擊者
 * 可以列舉出系統中存在哪些帳號（User Enumeration，OWASP常見弱點），
 * 因此無論何種原因，一律回傳同一句「帳號或密碼錯誤」。
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("帳號或密碼錯誤");
    }
}
