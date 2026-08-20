package com.example.Product_Selection_260813.common;

/**
 * 統一API回應格式。
 *
 * 沿用既有ApiResponse{success, message}的設計，新增泛型data欄位，
 * 讓Auth等需要回傳結構化資料的API也能共用同一套Envelope，
 * 不需要像QuizController那樣區分「回ApiResponse」與「直接回DTO」兩種寫法。
 *
 * 相容性：success(String)／failure(String)兩個既有靜態方法簽名不變，
 * 呼叫端（例如GlobalExceptionHandler既有程式碼）不需要修改。
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /** 成功且不需要帶資料（例如登出、刪除） */
    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /** 成功且需要帶資料（例如登入回傳使用者資訊） */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
