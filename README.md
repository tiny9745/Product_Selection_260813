# AI 團購選品決策輔助系統 — 後端（Product_Selection_260813）

協助採購人員進行團購選品的 REST API：商品資料標準化、加權評分、市場熱度（PTT／Google 趨勢）、天氣與節慶加成、AI 選品分析、主管審核與決策紀錄。

> **AI 只做決策輔助**：AI 產生的摘要、適配度、風險提示都是參考資訊；正式審核結果一律由主管人工確認，並以不可覆蓋的 Snapshot 保存。

前端專案見 `procurement-dashboard`（Angular）。

---

## 目錄

- [技術棧](#技術棧)
- [環境需求](#環境需求)
- [快速開始](#快速開始)
- [環境變數](#環境變數)
- [專案結構](#專案結構)
- [架構慣例](#架構慣例)
- [認證與權限](#認證與權限)
- [API 總覽](#api-總覽)
- [排程作業](#排程作業)
- [外部服務與額度](#外部服務與額度)
- [資料庫與 Flyway](#資料庫與-flyway)
- [測試](#測試)
- [注意事項與常見問題](#注意事項與常見問題)

---

## 技術棧

| 類別 | 使用 |
|---|---|
| 語言／框架 | Java 21、Spring Boot 4.1.0 |
| Web／驗證 | Spring MVC、Jakarta Validation |
| 安全 | Spring Security、JWT（jjwt 0.12.6），Token 放在 HttpOnly Cookie |
| 資料 | Spring Data JPA／Hibernate、MySQL 8.0（mysql-connector-j） |
| 資料庫版本控管 | Flyway（`flyway-mysql`） |
| JSON | Jackson 3（Spring Boot 4 預設）＋ Jackson 2 `jackson-databind` 2.19.4（WeatherClient、Gemini、RestSecurityHandlers 仍用 Jackson 2 API） |
| 爬蟲 | jsoup 1.23.2（PTT 網頁版） |
| 相似度比對 | Apache Commons Text 1.13.0（Jaro-Winkler） |
| 圖片 | `webp-imageio` 0.1.6（讓 `ImageIO` 能讀 WebP） |
| 開發輔助 | `springboot4-dotenv` 5.1.0（讀專案根目錄 `.env`，僅開發期）、Spring Boot DevTools |
| 建置 | Gradle |

---

## 環境需求

- JDK 21
- MySQL 8.0
- Gradle（或專案內的 Gradle Wrapper）
- 開發 IDE：Eclipse（Gradle 專案匯入；見[注意事項](#注意事項與常見問題)的 `bin\main` 問題）

---

## 快速開始

### 1. 建立空資料庫

```sql
CREATE DATABASE product_selection CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

資料表**不要手動建立**，啟動時由 Flyway 依序執行 `V1`～最新版本的 migration。

### 2. 設定環境變數

在專案根目錄建立 `.env`（**不要提交到 Git**），至少包含必填項目：

```dotenv
DB_URL=jdbc:mysql://localhost:3306/product_selection
DB_USERNAME=root
DB_PASSWORD=your-password
JWT_SECRET=請填入至少 256 bits 的隨機字串
APP_UPLOAD_DIR=C:/product-selection/uploads

# 選填：沒設定時對應功能會回「尚未設定金鑰」，其餘功能不受影響
GEMINI_API_KEY=
GROQ_API_KEY=
SERPAPI_API_KEY=
```

> `.env` 由 `springboot4-dotenv` 讀取，只在開發期生效（`developmentOnly`）。正式環境請改用作業系統環境變數或部署平台的 Secret 設定。
> Eclipse 也可以在 *Run Configurations › Environment* 設定。

完整清單見[環境變數](#環境變數)。

### 3. 建立本機測試帳號（選用）

資料庫沒有任何帳號時，前端會一直登入失敗。本機開發可以開啟測試帳號種子：

```properties
# 本機 config/application.properties（不要放進 src/main/resources）
app.dev-seed-users=true
```

啟動時若帳號不存在會建立：

| 帳號 | 密碼 | 角色 |
|---|---|---|
| `manager` | `demo123` | 管理層（MANAGER） |
| `purchaser` | `demo123` | 操作層（PURCHASER） |

> 預設關閉（`@ConditionalOnProperty`），避免固定密碼帶進正式環境。

### 4. 啟動

```bash
./gradlew bootRun        # Windows：gradlew.bat bootRun
```

預設埠 `8080`。前端開發伺服器透過 proxy 把 `/api`、`/images` 轉到這裡。

### 5. 建置

```bash
./gradlew clean build    # 產出 build/libs/*.jar，會一併執行測試
```

---

## 環境變數

### 必填

| 變數 | 用途 |
|---|---|
| `DB_URL` | JDBC URL，**不含參數**（程式會自動加上 `serverTimezone=Asia/Taipei&useSSL=false&rewriteBatchedStatements=true`） |
| `DB_USERNAME`／`DB_PASSWORD` | 資料庫帳密 |
| `JWT_SECRET` | JWT 簽章金鑰（HS256，**至少 32 bytes**，太短啟動時 jjwt 會直接拋例外） |
| `APP_UPLOAD_DIR` | 商品圖片存放的實體資料夾 |

### 選填（外部服務金鑰）

| 變數 | 用途 | 未設定時 |
|---|---|---|
| `GEMINI_API_KEY` | 單一商品 AI 分析（`gemini-3.6-flash`） | 產生 AI 分析時回「尚未設定 Gemini API 金鑰」 |
| `GROQ_API_KEY` | AI 商品雷達的 AI 抽取與適配評分（`openai/gpt-oss-120b`） | AI 商品雷達無法執行 |
| `SERPAPI_API_KEY` | Google 趨勢參考 | Google 趨勢無法查詢（來源開關預設也是停用） |

### 選填（行為調整，括號內為預設值）

| 變數 | 說明 |
|---|---|
| `JWT_EXPIRATION_MS`（1800000） | Token 有效期，預設 30 分鐘 |
| `COOKIE_SECURE`（true） | Cookie 是否加 `Secure`，見[注意事項](#注意事項與常見問題) |
| `PTT_BOARDS` | 熱度爬蟲與 AI 商品雷達的看板清單（預設 `Lifeismoney,e-shopping,Food,cookclub,e-appliance,PC_Shopping,MobileComm`） |
| `PTT_WINDOW_DAYS`（90）／`PTT_RECENT_DAYS`（7） | 熱度統計窗口／近期動能窗口 |
| `PTT_MAX_PAGES_PER_BOARD`（5）／`PTT_REQUEST_DELAY_MS`（1000） | 每看板最多頁數／請求間隔（避免造成 PTT 負擔） |
| `PTT_REFERENCE_VOLUME`（20）／`PTT_HOT_VOLUME`（1000） | 對應 50 分／100 分的討論量（熱度正規化基準） |
| `DISCOVERY_*` | AI 商品雷達的掃描天數、每批標題數、單次 AI 呼叫上限等，見 `application.properties` |
| `GROQ_MODEL`／`GROQ_REASONING_EFFORT` 等 | Groq 模型與推理強度 |
| `GOOGLE_TRENDS_BATCH_SIZE`（40）／`GOOGLE_TRENDS_RECHECK_DAYS`（7） | Google 趨勢每週批次數量／幾天內查過就略過 |
| `WEATHER_FORECAST_DAYS`（14） | Open-Meteo 預報天數（上限 16） |

每個參數的完整說明寫在 `src/main/resources/application.properties` 的註解中。

---

## 專案結構

```
src/main/java/com/example/Product_Selection_260813/
├── controller/     HTTP 端點（14 支），只處理 Request／Response 與權限註記
├── service/        商業邏輯
│   ├── scoring/        加權評分、評分因子
│   ├── gate/           Gate 判定（做不做得到，與分數獨立）
│   ├── campaign/       節慶檔期、週期規則
│   ├── weather/        天氣資料同步與天氣加成
│   ├── trends/         熱度趨勢
│   ├── crawler/        PTT 爬蟲（jsoup）
│   ├── discovery/      AI 商品雷達（AI 抽取、適配評分、交叉驗證）
│   ├── groupbuy/       歷史開團紀錄
│   └── resolver/       評分資料來源解析
├── repository/     Spring Data JPA Repository
├── entity/         JPA Entity
├── dto/            request／response DTO（API Contract）
├── enums/          列舉（狀態、角色、原因代碼等）
├── algorithm/      純計算邏輯（評分演算法）
├── security/       JWT 產生／驗證、Filter、401/403 處理
├── config/         圖片靜態資源、本機測試帳號種子
├── common/         ApiResponse、GlobalExceptionHandler、CSV、分頁工具
├── json/           JSON 欄位（Snapshot 等）的序列化型別
└── constants/      常數

src/main/resources/
├── application.properties
├── campaign/         節慶相關資料
└── db/migration/     Flyway migration（V1～V36）

src/test/java/...     單元測試（約 50 支）
src/test/resources/serpapi/   SerpApi 回應樣本（測試用）
```

---

## 架構慣例

```
Controller  →  Service  →  Repository  →  MySQL
（HTTP、DTO、權限）（商業邏輯、交易）（資料存取）
```

- **Controller 不放商業邏輯**，只做參數接收、`@Valid` 驗證、權限註記、回傳 DTO。
- **Entity 不直接回傳給前端**，一律轉成 `dto/response`。
- **統一回應格式**：所有 API 回傳 `ApiResponse`（`success`、`message`、`data`）。錯誤由 `GlobalExceptionHandler` 轉成同一格式；未登入（401）、權限不足（403）由 `RestSecurityHandlers` 回傳同一格式，不走 Spring Security 預設頁面。
- **例外與 HTTP 狀態碼**（`GlobalExceptionHandler`）：

  | 例外 | 狀態碼 | 語意 |
  |---|---|---|
  | `MethodArgumentNotValidException`、`IllegalArgumentException`、`MaxUploadSizeExceededException` | 400 | 輸入錯誤 |
  | `InvalidCredentialsException`、`AccountDisabledException` | 401 | 帳密錯誤、帳號停用（訊息不同，前端直接顯示） |
  | `AuthorizationDeniedException` | 403 | 權限不足 |
  | `IllegalStateException`、`DataIntegrityViolationException` | 409 | 狀態不允許（例如重複送審）、違反唯一鍵 |
  | `LlmAnalysisException`、`TrendInterestUnavailableException`、`MarketBuzzUnavailableException` | 502 | 外部服務（AI、SerpApi、PTT）暫時失敗，可以重試 |
  | `SystemConfigurationException` | 500 | 我方設定缺漏（例如沒設金鑰），重試不會成功 |
- **避免 N+1**：清單類查詢在 Service 內整頁批次查關聯資料（例如處理人姓名、佐證文章）。
- **分頁上限**：`spring.data.web.pageable.max-page-size=100`，呼叫端送再大的 `size` 也只會拿到 100 筆。

### 核心業務規則（修改前必讀）

| 規則 | 說明 |
|---|---|
| 狀態分離 | 審核狀態（Review Status）、商品狀態（Item Status）、候選狀態（Candidate Status）是不同概念，不可合併成單一欄位 |
| 審核 Snapshot 不可覆蓋 | 正式審核完成後建立紀錄；重新送審時建立新版本（`submission_count` 從 1 開始），不覆蓋舊紀錄 |
| 五個風險面向 | 商業、供應、品質、市場由主管人工評估；資料風險由系統依規則計算 |
| 評分可解釋 | 修改權重或因子時，要說明對既有商品分數的影響，以及是否需要重新計算 |
| 節慶準備期 | 節慶加成依「距活動開始天數」與 `preparation_lead_days` 判斷，不只看「今天是否在活動期間」 |
| Gate 狀態 | `INSUFFICIENT_DATA` 與 `FAILED` 的補救方式完全不同，不可合併處理 |
| 外部熱度 ≠ 銷售量 | PTT、Google 趨勢都是相對熱度；Google 趨勢只作參考，不併入評分 |
| 功能名稱與程式名稱 | 畫面上的「AI 商品雷達」在程式中一律叫 `discovery`（`/api/discoveries`、`Discovery*` 類別、`discovered_items` 等資料表）。英文名稱本身不綁定資料來源，**不要為了配合中文名稱而改名** |

---

## 認證與權限

### 登入流程

```
POST /api/auth/login
  → AuthService 驗證帳密（BCrypt）
  → JwtTokenProvider 簽發 JWT
  → 以 HttpOnly Cookie「access_token」回傳（SameSite=Strict、Secure 依 COOKIE_SECURE）
之後每個請求
  → 瀏覽器自動帶 Cookie
  → JwtAuthenticationFilter 驗證簽章、到期時間與 session version
  → 取得使用者與角色，交給 @PreAuthorize 判斷
```

- **無狀態**：不使用 HttpSession（`SessionCreationPolicy.STATELESS`）。
- **單一登入**：帳號的 `active_session_version`（V5）會在重新登入時遞增，舊 Token 立即失效。
- **強制改密碼**：管理者代設密碼後 `must_change_password=true`（V24），Token 會帶上對應 claim；改密碼前只能呼叫 `/api/auth/**`，其餘 API 由 Filter 直接回 403。
- **CSRF 關閉**：Cookie 為 `SameSite=Strict`，跨站請求不會帶上 Cookie；純 REST API 也沒有表單登入。
- **不設定 CORS**：前端開發時透過 Angular proxy，正式環境請讓前後端同網域（或由反向代理轉發）。

### 公開端點

| 端點 | 說明 |
|---|---|
| `POST /api/auth/login` | 登入 |
| `POST /api/auth/password-reset-requests` | 忘記密碼申請（不透露帳號是否存在） |
| `GET /images/products/**` | 商品圖片（檔名為 UUID、無目錄列表） |

其餘一律需要登入。

### 角色

| 角色 | 代碼 | 職責 |
|---|---|---|
| 操作層 | `PURCHASER` | 建立與編輯商品、批次匯入、送審、產生 AI 分析、處理 AI 商品雷達的商品線索 |
| 管理層 | `MANAGER` | 審核、決策紀錄匯出、系統設定、帳號管理、排程作業、Google 趨勢查詢 |

> 前端的 Route Guard 只改善導覽體驗，**權限以後端 `@PreAuthorize` 為準**。

---

## API 總覽

所有路徑前綴 `/api`。權限欄：P＝操作層、M＝管理層、登入＝兩者皆可（依端點而定，部分端點另有細部限制，以 Controller 的 `@PreAuthorize` 為準）。

| Controller | 路徑 | 主要功能 | 權限 |
|---|---|---|---|
| `AuthController` | `/auth` | 登入、登出、目前使用者、改個人資料與密碼、忘記密碼申請 | 公開／登入 |
| `ProductController` | `/products` | 商品清單、詳情、新增、批次新增、編輯、刪除、重新送審、封存／復原、圖片上傳、匯出、相似商品、自訂欄位 schema | 查詢登入；異動 P |
| `ScoringController` | `/products/{id}/evaluation`、`/festival-boost` | 評分結果、節慶加成明細 | 登入 |
| `AiSelectionController` | `/products/{id}/ai-analysis` | 查詢／產生 AI 分析（Gemini，有額度限制） | 查詢登入；產生 P |
| `TrendController` | `/products/{id}/trend` | 最新熱度、歷史走勢、立即同步 PTT | 登入 |
| `GoogleTrendController` | `/products/{id}/google-trend`、`/settings/google-trends` | Google 趨勢最新結果、批次涵蓋說明、手動查詢、來源開關、批次查詢 | 查詢登入；操作 M |
| `ReviewController` | `/reviews`、`/products/{id}/reviews` | 待審清單、送出審核、決策紀錄查詢與匯出、歷次審核紀錄 | M |
| `DashboardController` | `/dashboard` | 統計、推薦清單、風險提醒、轉換率、熱度排行 | 登入 |
| `DiscoveryController` | `/discoveries`、`/settings/discovery` | AI 商品雷達清單、略過／復原；排程狀態、執行紀錄、立即執行 | 清單登入；略過／復原 P；排程 M |
| `GroupBuyRecordController` | `/group-buy-records` | 歷史開團紀錄匯入、查詢、認領未連結紀錄 | 匯入 M；其餘登入 |
| `SettingsController` | `/settings` | 評估模式、評分因子、自訂欄位、分數區間、系統參數、風險選項、天氣標籤、區域權重、核心客群、商品類型、節慶檔期 | M |
| `TrendCrawlerController` | `/settings/trend-crawler` | PTT 熱度同步狀態、執行紀錄、來源開關、全部同步 | M |
| `WeatherController` | `/settings/weather` | 天氣同步狀態、手動同步、天氣加成設定 | M |
| `UserController` | `/users` | 帳號清單、新增、停用／啟用、重設密碼、拒絕重設申請 | M |

### 前端需要特別注意的回應行為

- **CSV 匯入失敗時仍回 HTTP 200**，要讀 body 的 `success` 判斷結果；匯入是全部成功或全部失敗，不會部分寫入。
- `supplierSimilarity` 為 `null` 代表「無法比較」，不是 0。
- 成團率的分母**不含**取消開團（CANCELLED）。
- 分數區間為 `HISTORICAL` 模式時，會忽略送出的上下限。

---

## 排程作業

| 時間 | 作業 | 位置 | 時區 |
|---|---|---|---|
| 每天 00:05 | 重算未核准商品的節慶加成 | `ScoringService.refreshFestivalBoosts` | Asia/Taipei |
| 每天 01:30 | AI 商品雷達（PTT） | `DiscoveryRunService` | ⚠️ 伺服器預設時區 |
| 每天 02:00 | PTT 熱度同步 | `TrendSyncRunService` | ⚠️ 伺服器預設時區 |
| 每週一 04:00 | Google 趨勢批次查詢（待審優先，最多 40 個） | `GoogleTrendService` | ⚠️ 伺服器預設時區 |
| 每天 05:00 | 天氣資料同步（完成後重算天氣與節慶加成） | `WeatherDataSyncService` | Asia/Taipei |

- 排程由 `@EnableScheduling`（`ProductSelection260813Application`）啟用。
- 標示 ⚠️ 的排程沒有指定 `zone`，會依 **JVM 預設時區**執行。部署在非台灣時區的主機時，請加上 JVM 參數 `-Duser.timezone=Asia/Taipei`，否則執行時間會偏移。
- AI 商品雷達若超過 25 分鐘會記錄警告（可能壓到 02:00 的熱度同步）。
- AI 商品雷達、PTT 熱度同步、Google 趨勢都有「執行紀錄」可在前端設定頁查看；應用程式在執行途中被關掉時，下次啟動會把停在 RUNNING 的紀錄標記為中斷。

---

## 外部服務與額度

| 服務 | 用途 | 額度控管 |
|---|---|---|
| Google Gemini | 單一商品 AI 分析（摘要、推薦原因、風險提示） | 系統內每月上限；逾時 90 秒 |
| Groq（OpenAI 相容 API） | AI 商品雷達：從 PTT 標題抽取商品、依核心客群打適配分 | `system_settings.discovery_ai_monthly_limit`（預設 1000）；依 Groq `retry-after` 等待，每分鐘 8K tokens 限制 |
| SerpApi（Google Trends） | Google 趨勢參考（方向與成長率，不併入評分） | `system_settings.serpapi_monthly_limit`（預設 200）；來源開關**預設停用** |
| PTT 網頁版 | 討論量熱度、AI 商品雷達的掃描素材 | 只抓公開、免登入、不需滿 18 歲確認的看板；每次請求間隔 1 秒 |
| Open-Meteo | 天氣預報（天氣加成） | 免費公開 API，不需金鑰 |

> 金鑰一律由環境變數注入，**不寫死、不進 Git**。

---

## 資料庫與 Flyway

- Schema 完全由 Flyway 管理（`src/main/resources/db/migration`，目前 V1～V36）。
- `spring.jpa.hibernate.ddl-auto=validate`：Hibernate 只驗證 Entity 與資料表是否一致，不會自動建表或改表。
- `spring.flyway.baseline-on-migrate=false`：新資料庫從 V1 開始完整執行。

### 修改資料表的規則

1. **已套用的 migration 絕對不要修改**。Flyway 會比對 checksum，改了會啟動失敗。要調整一律新增下一個版本（`V37__說明.sql`）。
2. 修改 Entity 時，同步檢查 Repository → Service → DTO → Controller → 前端 API Contract。
3. 新增 Entity 的時間欄位時，確認 `@CreationTimestamp`／`@UpdateTimestamp` 與 `updatable = false` 設定正確（專案曾重複發生缺漏）。
4. DB 欄位是 `TINYINT`、Java 是 `Integer` 時，在欄位加 `@JdbcTypeCode(SqlTypes.TINYINT)`，否則 `validate` 會失敗（例：`product_types.level`）。
5. JSON 欄位（例如審核 Snapshot）的 key 使用 **camelCase**，否則 Jackson 反序列化會失敗。

### 重建資料庫

開發環境的重建流程是：

```sql
DROP DATABASE product_selection;
CREATE DATABASE product_selection CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然後重新啟動應用程式，讓 Flyway 從 V1 執行到最新版。**重建前請先清除舊的建置輸出**（見下方 Eclipse 問題）。

### 刪除商品的順序

`ai_analyses` 同時有外鍵指向 `products.id` 與 `product_evaluations.id`，刪除商品時必須先刪 `ai_analyses`，再刪 `product_evaluations`（`ProductDeletionForeignKeyCoverageTest` 會檢查新外鍵是否被刪除流程涵蓋）。

---

## 測試

```bash
./gradlew test
```

- JUnit 5（`spring-boot-starter-test`），以 Service 與演算法的單元測試為主。
- `src/test/resources/serpapi/` 放 SerpApi 的回應樣本，測試不會真的呼叫外部 API。
- 需要特別注意的測試：
  - `ProductDeletionForeignKeyCoverageTest`：新增指向 `products` 的外鍵時，確認刪除流程有處理。
  - `ReviewServiceWeightSnapshotTest`：審核 Snapshot 的權重不會被後續設定變更影響。

---

## 注意事項與常見問題

### Eclipse 的 `bin\main` 舊檔問題

Eclipse 會把 `src/main/resources` 複製到 `bin\main`。如果這個資料夾是舊的，Flyway 會讀到**舊版的 migration 檔**，出現「本機驗證正確、實際執行卻失敗」的狀況（專案曾因此白改了五輪 V3）。
遇到 Flyway 錯誤時，先執行 *Project › Clean*，或刪除 `bin` 資料夾後重新建置，再判斷是不是 SQL 本身的問題。

### `COOKIE_SECURE`

預設 `true`（Cookie 只在 HTTPS 傳送）。以 `http://localhost` 開發時，主流瀏覽器仍會接受 Secure Cookie；如果用區網 IP 或其他 http 網址測試，登入後會一直被視為未登入，此時設 `COOKIE_SECURE=false`。**正式環境務必維持 `true` 並使用 HTTPS。**

### 上傳限制

- 單檔 5MB；整個 multipart 請求 100MB（為了批次新增一次上傳多張圖片）。
- 副檔名白名單：`jpg`、`jpeg`、`png`、`webp`，並用 `ImageIO` 驗證內容確實是圖片（WebP 解碼靠 `webp-imageio`）。
- 圖片透過 `/images/products/**` 公開讀取，實體位置為 `APP_UPLOAD_DIR`。

### SQL 日誌

`spring.jpa.show-sql=true` 會把所有 SQL 印到主控台，正式環境建議關閉。

### 相關文件

- 企劃書（v8 以上）
- 後端技術參考手冊
- Postman 測試指南
- 前端 API 欄位對照（含顯示層註記）
