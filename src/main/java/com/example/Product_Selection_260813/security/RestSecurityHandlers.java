package com.example.Product_Selection_260813.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 把Spring Security預設的未登入／權限不足回應，改成專案統一的ApiResponse JSON格式。
 *
 * 沒有這兩個類別的話，SecurityFilterChain擋下請求時走的是Spring Security內建的
 * 預設處理（空白403或重導向），前端「所有錯誤都用同一套ApiResponse邏輯解析」的假設
 * 在「未登入」「權限不足」這兩種情況會失效，因此需要手動接管、手動寫JSON回應——
 * 這裡是Servlet Filter層級，不是走Spring MVC的@ExceptionHandler，
 * 因此不能沿用GlobalExceptionHandler，得自己用HttpServletResponse把JSON寫出來。
 *
 * 修正記錄：ObjectMapper原本用@Autowired注入應用程式全域的那顆bean，但實測會在啟動時
 * 拋出UnsatisfiedDependencyException——SecurityFilterChain屬於Spring Boot啟動過程中
 * 很早期就會建立的bean，這個時間點上，spring-boot-starter-web帶進來的Jackson自動配置
 * （負責產生全域ObjectMapper bean）還沒處理完，因此注入會失敗，這是bean建立「時機」問題，
 * 不是少了依賴。這裡要序列化的內容非常單純（ApiResponse的data在這個情境下永遠是null，
 * 不需要用到全域ObjectMapper可能有的自訂設定，例如LocalDateTime格式化），
 * 因此直接改成局部new一個ObjectMapper，完全不經過Spring容器注入，繞開這個時機問題。
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 未帶token／token無效或過期，存取需要登入的API時觸發，對應401 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		writeJsonError(response, HttpStatus.UNAUTHORIZED, "請先登入");
	}

	/** 已登入，但角色不符合該API要求時觸發（例如操作層呼叫僅限管理層的API），對應403 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException {
		writeJsonError(response, HttpStatus.FORBIDDEN, "權限不足");
	}

	private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(message)));
	}
}