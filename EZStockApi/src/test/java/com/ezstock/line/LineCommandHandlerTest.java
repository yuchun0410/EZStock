package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MemberDao;
import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dao.StockDao;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.Member;
import com.ezstock.market.IndexQuote;
import com.ezstock.market.InstitutionalService;
import com.ezstock.market.InstitutionalSummary;
import com.ezstock.market.MarketIndexService;
import com.ezstock.model.Stock;
import com.ezstock.model.StockType;
import com.ezstock.service.AssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineCommandHandlerTest {

    private final Stock tsmc = new Stock("2330", "台積電", StockType.STOCK, "半導體業");
    private StockDao stockDao;
    private DailyPriceDao dailyPriceDao;
    private LineBindingService bindingService;
    private MemberDao memberDao;
    private AssetService assetService;
    private PriceAlertService alertService;
    private InstitutionalService institutionalService;
    private MarketIndexService marketIndexService;
    private LineCommandHandler handler;

    @BeforeEach
    void setUp() {
        stockDao = mock(StockDao.class);
        dailyPriceDao = mock(DailyPriceDao.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T07:00:00Z"), ZoneId.of("Asia/Taipei"));
        bindingService = mock(LineBindingService.class);
        memberDao = mock(MemberDao.class);
        assetService = mock(AssetService.class);
        alertService = mock(PriceAlertService.class);
        institutionalService = mock(InstitutionalService.class);
        marketIndexService = mock(MarketIndexService.class);
        handler = new LineCommandHandler(stockDao, dailyPriceDao, bindingService, memberDao, assetService,
                new LineMessageFormatter(), mock(LineDailyReportService.class), alertService,
                institutionalService, marketIndexService, clock);
        when(memberDao.findByLineUserId(anyString())).thenReturn(Optional.empty());

        when(stockDao.findById(anyString())).thenReturn(Optional.empty());
        when(stockDao.findById("2330")).thenReturn(Optional.of(tsmc));
        when(stockDao.search(anyString(), anyInt())).thenReturn(List.of());
        when(stockDao.search("台積電", 1)).thenReturn(List.of(tsmc));
        when(dailyPriceDao.findByStocksSince(any(), any())).thenReturn(List.of(
                price(LocalDate.of(2026, 10, 29), "2415"),
                price(LocalDate.of(2026, 10, 30), "2370")));
    }

    @Test
    @DisplayName("法人：不用綁定，回覆加權指數與三大法人買賣超（億元，標示買超／賣超）")
    void institutional() {
        LocalDate day = LocalDate.of(2026, 11, 12);
        when(institutionalService.latest()).thenReturn(new InstitutionalSummary(day, List.of(
                new InstitutionalSummary.Group("外資", new BigDecimal("38609179554"), new BigDecimal("39293683437"),
                        new BigDecimal("-684503883")),
                new InstitutionalSummary.Group("投信", new BigDecimal("50364151226"), new BigDecimal("37978721000"),
                        new BigDecimal("12385430226")),
                new InstitutionalSummary.Group("自營商", BigDecimal.ZERO, new BigDecimal("654096820"),
                        new BigDecimal("-654096820"))),
                new InstitutionalSummary.Group("合計", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("11046829523"))));
        when(marketIndexService.all()).thenReturn(List.of(new IndexQuote("TAIEX", "加權指數", "台股大盤", "TW", day,
                new BigDecimal("43091.87"), new BigDecimal("-1165.28"), new BigDecimal("-2.63"), List.of())));

        String reply = handler.handle("U1", "法人").getFirst();

        assertThat(reply).startsWith("三大法人買賣超（11/12）")
                .contains("加權指數 43,091.87 ▼1,165.28（-2.63%）")
                .contains("・外資 -6.85 億（賣超）")
                .contains("・投信 +123.85 億（買超）")
                .contains("合計 +110.47 億（買超）");
        assertThat(handler.handle("U1", "三大法人").getFirst()).startsWith("三大法人買賣超");
    }

    @Test
    @DisplayName("傳代號或名稱：回收盤價與漲跌")
    void quote() {
        String byId = handler.handle("U1234567890", " 2330 ").get(0);
        String byName = handler.handle("U1234567890", "台積電").get(0);

        assertThat(byId).isEqualTo("2330 台積電\n收盤 2,370.00（10/30）\n漲跌 ▼-45.00（-1.86%）");
        assertThat(byName).isEqualTo(byId);
    }

    @Test
    @DisplayName("說明、看不懂的指令：回說明")
    void help() {
        assertThat(handler.handle("U1", "說明")).containsExactly(LineCommandHandler.HELP);
        assertThat(handler.handle("U1", "今天天氣如何呢")).singleElement().asString()
                .startsWith("看不懂「今天天氣如何呢」").contains(LineCommandHandler.HELP);
    }

    @Test
    @DisplayName("沒有股價資料：告訴使用者")
    void noPrice() {
        when(dailyPriceDao.findByStocksSince(any(), any())).thenReturn(List.of());

        assertThat(handler.handle("U1", "2330")).containsExactly("2330 台積電\n最近沒有股價資料。");
    }

    @Test
    @DisplayName("綁定：「綁定 123456」與「綁定123456」都可以；錯誤時告訴使用者還能試幾次")
    void bind() {
        Member member = Member.withPassword("a@example.com", "hash", "小明", LocalDateTime.now());
        when(bindingService.bind("U1", "123456")).thenReturn(new LineBindingService.BindResult.Success(member));
        when(bindingService.bind("U1", "654321")).thenReturn(new LineBindingService.BindResult.WrongCode(3));

        assertThat(handler.handle("U1", "綁定 123456").get(0)).startsWith("綁定成功！小明");
        assertThat(handler.handle("U1", "綁定654321").get(0)).contains("還可以試 3 次");
        verify(bindingService).bind("U1", "654321");
    }

    @Test
    @DisplayName("資產：沒綁定時提示綁定；綁定後回淨資產明細")
    void assets() {
        assertThat(handler.handle("U1", "資產")).containsExactly(LineCommandHandler.NOT_LINKED);

        Member member = Member.withPassword("a@example.com", "hash", "小明", LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 7L);
        when(memberDao.findByLineUserId("U2")).thenReturn(Optional.of(member));
        when(assetService.summary(7L)).thenReturn(new AssetSummaryResponse(
                new BigDecimal("20000"), new BigDecimal("2370000"), new BigDecimal("2390000"),
                new BigDecimal("10000"), new BigDecimal("2380000"), new BigDecimal("950000"),
                new BigDecimal("1420000"), new BigDecimal("149.47"), List.of(), List.of(), List.of(), null, null));

        assertThat(handler.handle("U2", "資產").get(0))
                .startsWith("小明 的資產（11/2）\n淨資產 2,380,000 元")
                .contains("・股票 2,370,000（未實現 +1,420,000，+149.47%）");
    }

    @Test
    @DisplayName("到價提醒：「提醒 2330 < 2,200」設定跌破提醒，並告訴使用者目前收盤")
    void createAlert() {
        Member member = Member.withPassword("a@example.com", "hash", "小明", LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 7L);
        when(memberDao.findByLineUserId("U2")).thenReturn(Optional.of(member));
        com.ezstock.model.PriceAlert alert = new com.ezstock.model.PriceAlert(7L, tsmc,
                com.ezstock.model.AlertDirection.BELOW, new BigDecimal("2200"), LocalDateTime.now());
        when(alertService.create(7L, tsmc, com.ezstock.model.AlertDirection.BELOW, new BigDecimal("2200")))
                .thenReturn(new PriceAlertService.Created(alert, new BigDecimal("2370"), false));

        assertThat(handler.handle("U2", "提醒 2330 < 2,200").get(0))
                .startsWith("已設定提醒：2330 台積電 跌破 2,200.00")
                .contains("目前收盤 2,370.00");
        assertThat(handler.handle("U1", "提醒 2330 > 2500")).containsExactly(LineCommandHandler.NOT_LINKED);
    }

    private DailyPrice price(LocalDate date, String close) {
        BigDecimal c = new BigDecimal(close);
        return new DailyPrice(tsmc, date, c, c, c, c, 1_000L);
    }
}
