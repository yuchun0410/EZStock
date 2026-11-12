package com.ezstock.line;

import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.HoldingResponse;
import com.ezstock.market.IndexQuote;
import com.ezstock.market.InstitutionalSummary;
import com.ezstock.dto.MoneyAccountResponse;
import com.ezstock.model.AccountKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 把資產儀表板變成 LINE 的文字訊息（查詢「資產」與每日回報共用）。
 * LINE 文字訊息不支援粗體、表格，所以用換行和全形符號排版。
 */
@Component
public class LineMessageFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");

    public String assetReport(String displayName, LocalDate date, AssetSummaryResponse summary) {
        StringBuilder text = new StringBuilder()
                .append(displayName).append(" 的資產（").append(date.format(DATE)).append("）\n")
                .append("淨資產 ").append(money(summary.netWorth())).append(" 元\n")
                .append("・現金 ").append(money(summary.cashTotal())).append('\n')
                .append("・股票 ").append(money(summary.stockValue()));
        if (summary.stockReturnPercent() != null) {
            text.append("（未實現 ").append(signedMoney(summary.stockUnrealizedPnl()))
                    .append("，").append(signedPercent(summary.stockReturnPercent())).append("）");
        }
        text.append('\n').append("・負債 ").append(money(summary.liabilityTotal()));

        List<HoldingResponse> holdings = summary.holdings();
        if (!holdings.isEmpty()) {
            text.append("\n\n【持股】");
            for (HoldingResponse h : holdings) {
                text.append('\n').append(h.stockId()).append(' ').append(h.name())
                        .append(' ').append(new DecimalFormat("#,##0.#####").format(h.shares())).append(" 股");
                if (h.marketValue() == null) {
                    text.append("｜沒有股價");
                } else if ("USD".equals(h.currency())) {
                    // 美股：金額是美元，後面附上換算的台幣
                    text.append("｜現值 US$").append(money(h.marketValue()));
                    if (h.marketValueTwd() != null) {
                        text.append("（約 ").append(money(h.marketValueTwd())).append(" 元）");
                    }
                    text.append("｜").append(signedMoney(h.unrealizedPnl())).append(" 美元")
                            .append("（").append(signedPercent(h.returnPercent())).append("）");
                } else {
                    text.append("｜現值 ").append(money(h.marketValue()))
                            .append("｜").append(signedMoney(h.unrealizedPnl()))
                            .append("（").append(signedPercent(h.returnPercent())).append("）");
                }
            }
        }

        List<MoneyAccountResponse> accounts = summary.accounts();
        if (!accounts.isEmpty()) {
            text.append("\n\n【帳戶】");
            for (MoneyAccountResponse a : accounts) {
                text.append('\n').append(a.kind() == AccountKind.CASH ? "現金" : "負債").append("｜")
                        .append(a.name()).append(' ').append(money(a.balance()));
            }
        }
        return text.toString();
    }

    /**
     * 三大法人買賣超，例如：
     * <pre>
     * 三大法人買賣超（11/12）
     * 加權指數 43,091.87 ▼1,165.28（-2.63%）
     * ・外資 -6.85 億（賣超）
     * ・投信 +123.85 億（買超）
     * ・自營商 -6.54 億（賣超）
     * 合計 +110.46 億（買超）
     * </pre>
     *
     * @param taiex 加權指數；抓不到就是 null，不顯示那一行
     */
    public String institutionalReport(InstitutionalSummary summary, IndexQuote taiex) {
        if (summary.date() == null) {
            return "三大法人的資料暫時抓不到，請稍後再試。";
        }
        StringBuilder text = new StringBuilder("三大法人買賣超（").append(summary.date().format(DATE)).append("）");
        if (taiex != null && taiex.close() != null) {
            text.append("\n加權指數 ").append(money2(taiex.close()));
            if (taiex.change() != null) {
                text.append(' ').append(taiex.change().signum() > 0 ? "▲" : taiex.change().signum() < 0 ? "▼" : "")
                        .append(money2(taiex.change().abs()))
                        .append("（").append(signedPercent(taiex.changePercent())).append("）");
            }
        }
        for (InstitutionalSummary.Group group : summary.groups()) {
            text.append("\n・").append(group.name()).append(' ').append(yi(group.net()));
        }
        text.append("\n合計 ").append(yi(summary.total().net()));
        return text.toString();
    }

    /** 金額（元）→「+123.85 億（買超）」 */
    static String yi(BigDecimal amount) {
        BigDecimal yi = amount.divide(new BigDecimal("100000000"), 2, java.math.RoundingMode.HALF_UP);
        String sign = yi.signum() > 0 ? "+" : "";
        return sign + new DecimalFormat("#,##0.00").format(yi) + " 億（" + (amount.signum() >= 0 ? "買超" : "賣超") + "）";
    }

    static String money(BigDecimal value) {
        return value == null ? "-" : new DecimalFormat("#,##0").format(value);
    }

    static String money2(BigDecimal value) {
        return value == null ? "-" : new DecimalFormat("#,##0.00").format(value);
    }

    static String signedMoney(BigDecimal value) {
        return value == null ? "-" : (value.signum() > 0 ? "+" : "") + money(value);
    }

    static String signedPercent(BigDecimal value) {
        return value == null ? "-" : (value.signum() > 0 ? "+" : "") + new DecimalFormat("#,##0.00").format(value) + "%";
    }
}
