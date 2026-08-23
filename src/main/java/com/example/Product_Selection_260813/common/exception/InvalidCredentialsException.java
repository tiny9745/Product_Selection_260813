package com.example.Product_Selection_260813.common.exception;

/**
 * 帳號不存在 或 密碼錯誤，統一丟這個例外。
 *
 * 刻意不區分「帳號不存在」與「密碼錯誤」：若分開，錯誤訊息的差異會讓攻擊者
 * 可以列舉出系統中存在哪些帳號（User Enumeration，OWASP常見弱點），
 * 因此無論何種原因，一律回傳同一句「帳號或密碼錯誤」。
 */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("帳號或密碼錯誤");
    }

    /**
     * 供「帳密驗證」以外、但同樣屬於「身份無法確認」的情境使用
     * （例如：token有效但對應的使用者已被刪除），此時「帳號或密碼錯誤」這句話語意不對，
     * 需要一句更符合情境的訊息，但仍歸類為同一種例外，讓GlobalExceptionHandler
     * 只需要一個handler就能涵蓋，不用為了每個邊界情況各開一個例外類別。
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}