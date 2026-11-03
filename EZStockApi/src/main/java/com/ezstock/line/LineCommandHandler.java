package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MemberDao;
import com.ezstock.dao.StockDao;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Member;
import com.ezstock.model.Stock;
import com.ezstock.service.AssetService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把使用者傳給 Bot 的文字變成回覆。
 * 支援：查股價（傳代號或名稱）、綁定帳號、查資產、解除綁定、說明。
 */
@Component
public class LineCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LineCommandHandler.class);
    private static final int QUOTE_LOOKBACK_DAYS = 14;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    /** 「綁定 123456」「綁定123456」都可以 */
    private static final Pattern BIND = Pattern.compile("^綁定\\s*(\\d{6})$");

    static final String HELP = """
            EZStock 小幫手可以做這些事：
            ・傳股票代號或名稱查股價，例如：2330、台積電
            ・傳「資產」查淨資產（要先綁定帳號）
            ・傳「回報」看今天的每日回報（淨資產＋自選股站上月線）
            ・傳「綁定 123456」綁定網站帳號（綁定碼在網站「我的資產」頁產生）
            ・傳「解除綁定」取消綁定
            ・傳「說明」看這段說明""";

    static final String NOT_LINKED = "還沒有綁定網站帳號。請到 EZStock 網站「我的資產」頁產生綁定碼，再傳「綁定 123456」給我。";

    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final LineBindingService bindingService;
    private final MemberDao memberDao;
    private final AssetService assetService;
    private final LineMessageFormatter formatter;
    private final LineDailyReportService reportService;
    private final Clock clock;

    public LineCommandHandler(StockDao stockDao, DailyPriceDao dailyPriceDao, LineBindingService bindingService,
                              MemberDao memberDao, AssetService assetService, LineMessageFormatter formatter,
                              LineDailyReportService reportService, Clock clock) {
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.bindingService = bindingService;
        this.memberDao = memberDao;
        this.assetService = assetService;
        this.formatter = formatter;
        this.reportService = reportService;
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
        Matcher bind = BIND.matcher(input);
        if (bind.matches()) {
            return List.of(bind(userId, bind.group(1)));
        }
        if (input.equals("資產") || input.equals("淨資產")) {
            return List.of(assets(userId));
        }
        if (input.equals("回報") || input.equals("每日回報")) {
            Optional<Member> member = memberDao.findByLineUserId(userId);
            if (member.isEmpty()) {
                return List.of(NOT_LINKED);
            }
            log.info("[LINE] 會員 id={} 手動查詢每日回報", member.get().getId());
            return reportService.build(member.get());
        }
        if (input.equals("解除綁定")) {
            return List.of(bindingService.unlinkByLineUser(userId)
                    .map(m -> "已解除綁定，之後不會再收到每日回報。")
                    .orElse("這個 LINE 帳號本來就沒有綁定。"));
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

    private String bind(String userId, String code) {
        return switch (bindingService.bind(userId, code)) {
            case LineBindingService.BindResult.Success success ->
                    "綁定成功！" + success.member().getDisplayName() + "，之後每天收盤後會傳淨資產回報給你。\n傳「資產」可以隨時查詢。";
            case LineBindingService.BindResult.WrongCode wrong ->
                    "綁定碼錯誤或已過期，請到網站重新產生。（還可以試 " + wrong.remainingTries() + " 次）";
            case LineBindingService.BindResult.TooManyFailures ignored ->
                    "錯誤太多次，請 10 分鐘後再試。";
        };
    }

    private String assets(String userId) {
        Optional<Member> member = memberDao.findByLineUserId(userId);
        if (member.isEmpty()) {
            return NOT_LINKED;
        }
        log.info("[LINE] 會員 id={} 查詢資產", member.get().getId());
        return formatter.assetReport(member.get().getDisplayName(), LocalDate.now(clock),
                assetService.summary(member.get().getId()));
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
