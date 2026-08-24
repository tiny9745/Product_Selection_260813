package com.example.Product_Selection_260813.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 商品圖片的靜態資源伺服設定，對應四、資料表設計 products.image_url 欄位。
 *
 * <b>公開讀取、不要求登入</b>（安全性與實作複雜度討論的結論）：商品圖片不是機密資料
 * （等同一般電商網站的商品照片，本來就是公開網址），檔名為UUID（見
 * ProductService.uploadImage()），沒有目錄列表功能，無法被列舉出完整清單，
 * 外洩單一網址的風險可接受。若改為要求登入才能讀取，需要捨棄Spring內建的
 * 靜態資源機制、另外寫一支手動讀檔+組裝Response的Controller，複雜度明顯提高，
 * 對內部工具的實際效益不成比例，故不採用。
 *
 * <b>刻意不做</b>（技術債，見對話記錄）：上傳頻率限制、EXIF中繼資料清除、
 * 縮圖／多尺寸產生——皆非本階段風險等級所需，待條件成立時再評估。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	@Value("${app.upload.dir}")
	private String uploadDir;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// URL路徑/images/products/**對應到uploadDir這個實際資料夾。
		// file:前綴代表這是檔案系統路徑，不是classpath資源，Spring才能正確讀到
		// 執行期才寫入的檔案（不像src/main/resources在build時就打包進jar、唯讀）。
		String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
		registry.addResourceHandler("/images/products/**").addResourceLocations("file:" + location);
	}
}
