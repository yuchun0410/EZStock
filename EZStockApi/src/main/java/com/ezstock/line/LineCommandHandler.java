package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.StockDao;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 把使用者傳給 Bot 的文字變成回覆。
 * 目前支援：查股價（傳代號或名稱，例如「2330」「台積電」）、說明。
 */
@Component
public class LineCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LineCommandHandler.class);
    private static final int QUOTE_LOOKBACK_DAYS = 14;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");

    static final String HELP = """
            EZStock 小幫手可以做這些事：
            ・傳股票代號或名稱查股價，例如：2330、台積電
            ・傳「說明」看這段說明""";

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final Clock clock;

    public LineCommandHandler(StockDao stockDao, DailyPriceDao dailyPriceDao, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.clock = clock;
    }

    /**
     * @param userId LINE 使用者 id（之後綁定帳號會用到）
     * @param text   使用者傳的文字
     * @return 要回覆的訊息（一則以上）
     */
    public List<String> handle(String userId, String text) {
        String input = text.trim();
        if (input.isEmpty() || input.equals("說明") || input.equalsIgnoreCase("help") || input.equals("?")) {
            return List.of(HELP);
        }
        if (input.length() <= 12) {
            Optional<Stock> stock = findStock(input);
            if (stock.isPresent()) {
                log.info("[LINE] 使用者 {} 查詢股價 {}", LineMessagingClient.mask(userId), stock.get().getStockId());
                return List.of(quote(stock.get()));
            }
        }
        log.info("[LINE] 使用者 {} 傳了看不懂的指令（{} 字）", LineMessagingClient.mask(userId), input.length());
        return List.of("看不懂「" + abbreviate(input) + "」，找不到這檔股票。\n\n" + HELP);
    }

    /** 先當成代號找，找不到再用名稱搜尋第一筆 */
    private Optional<Stock> findStock(String input) {
        Optional<Stock> byId = stockDao.findById(input.toUpperCase(Locale.ROOT));
        if (byId.isPresent()) {
            return byId;
        }
        return stockDao.search(input, 1).stream().findFirst();
    }

    String quote(Stock stock) {
        LocalDate from = LocalDate.now(clock).minusDays(QUOTE_LOOKBACK_DAYS);
        List<DailyPrice> prices = dailyPriceDao.findByStocksSince(List.of(stock.getStockId()), from);
        String title = stock.getStockId() + " " + stock.getName();
        if (prices.isEmpty()) {
            return title + "\n最近沒有股價資料。";
        }
        DailyPrice latest = prices.get(prices.size() - 1);
        StringBuilder text = new StringBuilder(title)
                .append("\n收盤 ").append(price(latest.getClosePrice()))
                .append("（").append(latest.getTradeDate().format(DATE)).append("）");
        if (prices.size() >= 2) {
            BigDecimal previous = prices.get(prices.size() - 2).getClosePrice();
            BigDecimal change = latest.getClosePrice().subtract(previous);
            BigDecimal percent = change.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
            text.append("\n漲跌 ").append(arrow(change)).append(signed(change))
                    .append("（").append(signed(percent)).append("%）");
        }
        return text.toString();
    }

    static String arrow(BigDecimal value) {
        return value.signum() > 0 ? "▲" : value.signum() < 0 ? "▼" : "";
    }

    static String price(BigDecimal value) {
        return new DecimalFormat("#,##0.00").format(value);
    }

    static String signed(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + price(value);
    }

    private static String abbreviate(String text) {
        return text.length() > 20 ? text.substring(0, 20) + "…" : text;
    }
}
