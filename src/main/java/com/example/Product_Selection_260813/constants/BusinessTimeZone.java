package com.example.Product_Selection_260813.constants;

import java.time.ZoneId;

/**
 * 業務時區（2026-09-24 檔期規則改版新增）。
 *
 * 檔期的「今天」一律以台灣時間判斷：伺服器若部署在 UTC 主機上，每天 00:00～08:00
 * 之間 LocalDate.now() 會拿到前一天，檔期在台灣已經開始、系統卻還判為準備期。
 * 本次只有新增的檔期程式碼改用這個常數，既有的 LocalDate.now() 呼叫不在本次範圍。
 */
public final class BusinessTimeZone {

	public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

	private BusinessTimeZone() {
	}
}
