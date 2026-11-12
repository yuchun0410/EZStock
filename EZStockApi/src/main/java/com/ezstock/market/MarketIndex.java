package com.ezstock.market;

import com.ezstock.model.Market;

/**
 * 首頁「市場概況」顯示的指數。指數不是股票，不放進股票清單，也不能拿來回測；
 * 直接跟外部資料來源要價格，記在記憶體裡（見 MarketIndexService）。
 *
 * @param code   外部資料來源的代號：台股加權指數是 TAIEX，美股指數是 ^ 開頭（Yahoo 的寫法）
 * @param market 決定要用台股還是美股的資料集
 */
public enum MarketIndex {
    TAIEX("TAIEX", "加權指數", "台股大盤", Market.TW),
    DOW_JONES("^DJI", "道瓊工業", "美股 30 大藍籌", Market.US),
    SP500("^GSPC", "S&P 500", "美股大盤", Market.US),
    NASDAQ("^IXIC", "那斯達克", "美股科技股", Market.US);

    private final String code;
    private final String displayName;
    private final String note;
    private final Market market;

    MarketIndex(String code, String displayName, String note, Market market) {
        this.code = code;
        this.displayName = displayName;
        this.note = note;
        this.market = market;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
    public String note() { return note; }
    public Market market() { return market; }
}
