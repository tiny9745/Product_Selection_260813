package com.example.Product_Selection_260813;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling：讓AiSuggestionBatchService裡的@Scheduled排程方法生效
// （對應規格書七、AI主動選品Daily Cron機制）。原本專案沒有這個註解，
// 補上後@Scheduled才會真的被Spring排程器執行。
@SpringBootApplication
@EnableScheduling
public class ProductSelection260813Application {

	public static void main(String[] args) {
		SpringApplication.run(ProductSelection260813Application.class, args);
	}

}
