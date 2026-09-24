package com.example.Product_Selection_260813.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.Product_Selection_260813.entity.AppUser;
import com.example.Product_Selection_260813.enums.UserRole;
import com.example.Product_Selection_260813.repository.AppUserRepository;

/**
 * 本機資料庫若沒有任何帳號，前端登入會永遠失敗（畫面只會顯示帳密錯誤）。
 * 僅在對應 username 不存在時建立，不會覆蓋已有帳號。
 *
 * 預設關閉：只有設定 app.dev-seed-users=true（本機 config/application.properties）
 * 才會執行，避免固定密碼的測試帳號被帶進正式環境。
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed-users", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public DevUserSeeder(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed("manager", "管理測試人員", UserRole.MANAGER);
		seed("purchaser", "操作測試人員", UserRole.PURCHASER);
	}

	private void seed(String username, String name, UserRole role) {
		if (appUserRepository.existsByUsername(username)) {
			return;
		}
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode("demo123"));
		user.setName(name);
		user.setRole(role);
		user.setEnabled(true);
		user.setActiveSessionVersion(0);
		appUserRepository.save(user);
		log.info("已建立本機測試帳號 {} / demo123", username);
	}
}
