package com.example.Product_Selection_260813.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全域例外處理。
 *
 * 目的： <br>
 * 1. 安全性：任何「未預期」的例外（SQL錯誤、NullPointerException等）一律回傳通用訊息，
 * 詳細內容只寫進伺服器log，不外洩內部實作細節（資料庫結構、SQL語法、class名稱等）。
 * 
 * 2. 完整性：涵蓋 QuizController<br>
 * 目前所有API可能拋出的例外類型， 即使部分API(getAllQuizzes / getQuestionsByQuizId)成功時回傳的是
 * List<...>，發生錯誤時也統一改回傳 ApiResponse{success:false, message}格式，
 * 前端統一用同一套邏輯處理錯誤即可，不用因為API不同而寫不同的錯誤判斷邏輯。
 *
 * 注意：以下 handler 的比對順序由 Spring 依例外類別的繼承關係自動決定， 並非由程式碼撰寫順序決定；例如
 * DataAccessException 雖然也是 RuntimeException 的子類別， 但因為比對到較明確的
 * DataAccessException handler，就不會落到最下面的 Exception handler， 因此不需要、也不應該額外寫一個
 * RuntimeException.class 的 handler （那樣會連同 DataAccessException 都接住，反而造成訊息外洩風險）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// ========== @Valid 驗證失敗（欄位格式錯誤，例如描述未填、選項內容空白）==========
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream().findFirst().map(FieldError::getDefaultMessage)
				.orElse("請求格式錯誤");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(message));
	}

	// ========== 業務邏輯驗證失敗 ==========
	// 對應 QuizService 內 validateQuizTime()、validateQuestionOptions() 拋出的例外，
	// 這些訊息是程式本身寫死、可控的文字，可以安全地回傳給前端顯示
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(ex.getMessage()));
	}

	// ========== 資料庫存取例外（SQL錯誤、連線失敗、表不存在等）==========
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiResponse> handleDataAccessException(DataAccessException ex) {
		log.error("資料庫存取發生錯誤", ex); // 完整堆疊只留在伺服器log，絕不回傳給前端
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("伺服器發生錯誤，請稍後再試"));
	}
	
	
	// ========== 不合法的請求(資料目前狀態不允許) ==========
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiResponse> handleIllegalState(IllegalStateException e) {

		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse(false, e.getMessage()));
	}

	// ========== 保底：其餘所有未預期的例外 ==========
	// 刻意不直接回傳 ex.getMessage()，因為無法保證訊息內容不含內部實作細節
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse> handleUnexpectedException(Exception ex) {
		log.error("發生未預期的例外", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("伺服器發生錯誤，請稍後再試"));
	}
}