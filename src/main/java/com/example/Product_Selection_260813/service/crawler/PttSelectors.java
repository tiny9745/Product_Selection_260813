package com.example.Product_Selection_260813.service.crawler;

import java.util.regex.Pattern;

/**
 * PTT 網頁版（www.ptt.cc）搜尋結果頁的 CSS selector 與網址格式，集中在這裡。
 *
 * PTT 版面偶爾會調整，selector 一旦失效，爬蟲不會報錯，只會「每個看板都抓到
 * 0 篇」——分數全部掉到 0，熱度排行與評分悄悄失準。所以刻意不把 selector 寫死在
 * PttSearchPageParser 的邏輯裡：版面改了只需要改這個檔案，再跑
 * PttSearchPageParserTest（用真實頁面結構做成的 HTML 樣本）確認即可。
 *
 * 以下結構於 2026-09-24 對照 https://www.ptt.cc/bbs/Lifeismoney/search?q=... 確認：
 * <pre>
 * &lt;div class="r-ent"&gt;
 *   &lt;div class="nrec"&gt;&lt;span class="hl f3"&gt;31&lt;/span&gt;&lt;/div&gt;   ← 推文數（淨推數，爆=100+，X1~X9/XX=噓）
 *   &lt;div class="title"&gt;&lt;a href="/bbs/Lifeismoney/M.1750310171.A.4BE.html"&gt;...&lt;/a&gt;&lt;/div&gt;
 *   ...
 * &lt;/div&gt;
 * </pre>
 * 文章網址中的 M.{數字} 是發文當下的 Unix 秒數，直接拿來判斷發文時間，不用解析
 * 列表上不含年份的「9/24」日期字串。已刪除的文章沒有 &lt;a&gt; 連結，會被略過。
 */
public final class PttSelectors {

	private PttSelectors() {
	}

	public static final String BASE_URL = "https://www.ptt.cc";

	/** 看板搜尋網址：/bbs/{看板}/search?page={頁碼}&q={關鍵字}，page=1 是最新一頁。 */
	public static final String SEARCH_PATH_FORMAT = "/bbs/%s/search?page=%d&q=%s";

	/** 每一篇文章的列表項目。 */
	public static final String POST_ENTRY = "div.r-ent";

	/** 列表項目內的推文數。 */
	public static final String PUSH_COUNT = "div.nrec";

	/** 列表項目內的文章連結；已刪除的文章沒有這個元素。 */
	public static final String TITLE_LINK = "div.title > a";

	/** 從文章連結擷取發文時間（Unix 秒）。 */
	public static final Pattern POST_TIMESTAMP = Pattern.compile("/M\\.(\\d+)\\.A\\.");

	/**
	 * 需要「已滿18歲」確認的看板：頁面內會有導向 /ask/over18 的 script。
	 * 偵測到就整個看板略過——刻意不送 over18=1 cookie 去繞過這個確認。
	 */
	public static final String OVER18_MARKER = "/ask/over18";

	/** PTT 搜尋每頁固定 20 篇；少於這個數代表已經是最後一頁。 */
	public static final int POSTS_PER_PAGE = 20;

	/** 推文數「爆」代表 100 以上，這裡以 100 計。 */
	public static final String PUSH_BURST = "爆";

	/** 推文數「XX」代表淨噓 100 以上，以 100 計（討論量只看熱度，不看正負）。 */
	public static final String PUSH_BOO_BURST = "XX";

	/** 推文數「X1」~「X9」代表淨噓 10~90，以 10 倍計。 */
	public static final String PUSH_BOO_PREFIX = "X";

	// ---------------- 看板文章列表（2026-09-29，PTT 新品探索用）----------------
	// 結構與搜尋結果頁相同（div.r-ent），差異只有三點：
	// 1. 最新一頁網址是 /bbs/{看板}/index.html，往舊翻頁靠「‹ 上頁」按鈕的 href（/bbs/{看板}/index1234.html）；
	// 2. 最新一頁底部有置底公告，與一般文章之間以 div.r-list-sep 分隔，分隔線之後的文章要略過；
	// 3. 需要讀文章標題文字（搜尋頁只用到發文時間與推文數）。

	/** 看板最新一頁的路徑。 */
	public static final String INDEX_PATH_FORMAT = "/bbs/%s/index.html";

	/** 看板列表頁的路徑格式（翻頁連結必須符合這個格式才會跟隨，避免被導到其他網站或看板）。 */
	public static final Pattern INDEX_PAGE_PATH = Pattern.compile("^/bbs/[A-Za-z0-9_-]+/index\\d*\\.html$");

	/** 翻頁按鈕列。 */
	public static final String PAGING_LINKS = "div.btn-group-paging a";

	/** 往舊一頁的按鈕文字（「‹ 上頁」）。 */
	public static final String PREVIOUS_PAGE_TEXT = "上頁";

	/** 置底公告的分隔線。 */
	public static final String LIST_SEPARATOR = "div.r-list-sep";

	/** 依文件順序同時選出文章列與分隔線，用來判斷哪些文章在置底區。 */
	public static final String ENTRY_OR_SEPARATOR = "div.r-ent, div.r-list-sep";
}
