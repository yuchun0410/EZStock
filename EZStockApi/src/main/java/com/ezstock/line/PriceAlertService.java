package com.ezstock.line;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.PriceAlertDao;
import com.ezstock.exception.BadRequestException;
import com.ezstock.model.AlertDirection;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.PriceAlert;
import com.ezstock.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 到價提醒：用收盤價判斷（我們只有日線，沒有盤中價），每天的回報裡檢查，到價就通知一次。
 */
@Service
public class PriceAlertService {

    static final int MAX_ACTIVE = 20;

    private static final Logger log = LoggerFactory.getLogger(PriceAlertService.class);

    private final PriceAlertDao priceAlertDao;
    private final DailyPriceDao dailyPriceDao;
    private final Clock clock;

    public PriceAlertService(PriceAlertDao priceAlertDao, DailyPriceDao dailyPriceDao, Clock clock) {
        this.priceAlertDao = priceAlertDao;
        this.dailyPriceDao = dailyPriceDao;
        this.clock = clock;
    }

    /**
     * @param latestClose    目前資料庫裡最新的收盤價（沒有就是 null）
     * @param alreadyReached 現在的收盤價是不是已經到價了（今天的回報就會通知）
     */
    public record Created(PriceAlert alert, BigDecimal latestClose, boolean alreadyReached) {
    }

    public Created create(long memberId, Stock stock, AlertDirection direction, BigDecimal target) {
        if (target.signum() <= 0) {
            throw new BadRequestException("目標價要大於 0");
        }
        if (priceAlertDao.findActiveByMember(memberId).size() >= MAX_ACTIVE) {
            throw new BadRequestException("到價提醒最多 " + MAX_ACTIVE + " 個，請先刪除一些");
        }
        PriceAlert alert = priceAlertDao.save(new PriceAlert(memberId, stock, direction, target,
                LocalDateTime.now(clock)));
        BigDecimal close = dailyPriceDao.findLatest(stock.getStockId()).map(DailyPrice::getClosePrice).orElse(null);
        log.info("[到價提醒] 會員 id={} 新增 {} {} {}", memberId, stock.getStockId(), direction.symbol(), target);
        return new Created(alert, close, close != null && direction.isReached(close, target));
    }

    public List<PriceAlert> list(long memberId) {
        return priceAlertDao.findActiveByMember(memberId);
    }

    /** @param number 清單上的編號（從 1 開始） */
    public Optional<PriceAlert> deleteByNumber(long memberId, int number) {
        List<PriceAlert> alerts = list(memberId);
        if (number < 1 || number > alerts.size()) {
            return Optional.empty();
        }
        PriceAlert alert = alerts.get(number - 1);
        priceAlertDao.deleteByIdAndMember(alert.getId(), memberId);
        log.info("[到價提醒] 會員 id={} 刪除提醒 id={}", memberId, alert.getId());
        return Optional.of(alert);
    }

    /**
     * 檢查這個會員的提醒，到價的標記成已通知，回傳要通知的文字（每個提醒一行）。
     * 一個會員的提醒通常只有幾個，每檔查一次最新收盤就好。
     */
    public List<String> checkTriggered(long memberId) {
        List<String> lines = new ArrayList<>();
        for (PriceAlert alert : list(memberId)) {
            Optional<DailyPrice> latest = dailyPriceDao.findLatest(alert.getStock().getStockId());
            if (latest.isEmpty()
                    || !alert.getDirection().isReached(latest.get().getClosePrice(), alert.getTargetPrice())) {
                continue;
            }
            alert.markTriggered(LocalDateTime.now(clock));
            priceAlertDao.save(alert);
            lines.add(describe(alert) + "｜收盤 " + LineMessageFormatter.money2(latest.get().getClosePrice()));
            log.info("[到價提醒] 會員 id={} 提醒 id={} 到價（{} 收盤 {}）", memberId, alert.getId(),
                    alert.getStock().getStockId(), latest.get().getClosePrice());
        }
        return lines;
    }

    /** 「2330 台積電 跌破 2,200.00」 */
    static String describe(PriceAlert alert) {
        return alert.getStock().getStockId() + " " + alert.getStock().getName() + " "
                + alert.getDirection().label() + " " + LineMessageFormatter.money2(alert.getTargetPrice());
    }
}
