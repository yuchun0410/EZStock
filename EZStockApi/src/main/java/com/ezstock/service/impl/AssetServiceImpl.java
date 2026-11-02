package com.ezstock.service.impl;

import com.ezstock.dao.DailyPriceDao;
import com.ezstock.dao.MoneyAccountDao;
import com.ezstock.dao.StockDao;
import com.ezstock.dao.StockHoldingDao;
import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.HoldingRequest;
import com.ezstock.dto.HoldingResponse;
import com.ezstock.dto.MoneyAccountRequest;
import com.ezstock.dto.MoneyAccountResponse;
import com.ezstock.exception.BadRequestException;
import com.ezstock.exception.ResourceNotFoundException;
import com.ezstock.model.AccountKind;
import com.ezstock.model.DailyPrice;
import com.ezstock.model.MoneyAccount;
import com.ezstock.model.Stock;
import com.ezstock.model.StockHolding;
import com.ezstock.service.AssetService;
import com.ezstock.service.PriceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 資產儀表板的計算。金額一律用 BigDecimal，避免 double 的小數誤差（例如 0.1 + 0.2 ≠ 0.3）。
 * 所有查詢、修改都帶會員 id，不會動到別人的資料。
 */
@Service
public class AssetServiceImpl implements AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetServiceImpl.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** 金額統一到小數 2 位，沒有持股時才不會顯示成「0」，有持股時顯示成「0.00」 */
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final MoneyAccountDao moneyAccountDao;
    private final StockHoldingDao stockHoldingDao;
    private final StockDao stockDao;
    private final DailyPriceDao dailyPriceDao;
    private final PriceLoader priceLoader;
    private final Clock clock;

    public AssetServiceImpl(MoneyAccountDao moneyAccountDao, StockHoldingDao stockHoldingDao, StockDao stockDao,
                            DailyPriceDao dailyPriceDao, PriceLoader priceLoader, Clock clock) {
        this.moneyAccountDao = moneyAccountDao;
        this.stockHoldingDao = stockHoldingDao;
        this.stockDao = stockDao;
        this.dailyPriceDao = dailyPriceDao;
        this.priceLoader = priceLoader;
        this.clock = clock;
    }

    @Override
    public AssetSummaryResponse summary(long memberId) {
        long started = System.currentTimeMillis();
        List<MoneyAccount> accounts = moneyAccountDao.findByMember(memberId);
        List<StockHolding> holdings = stockHoldingDao.findByMember(memberId);
        List<String> notes = new ArrayList<>();

        BigDecimal cash = sum(accounts, AccountKind.CASH);
        BigDecimal liabilities = sum(accounts, AccountKind.LIABILITY);

        List<HoldingResponse> holdingResponses = new ArrayList<>();
        BigDecimal stockValue = ZERO;
        BigDecimal stockCost = ZERO;
        for (StockHolding holding : holdings) {
            HoldingResponse response = toResponse(holding, notes);
            holdingResponses.add(response);
            if (response.marketValue() != null) {
                stockValue = stockValue.add(response.marketValue());
                stockCost = stockCost.add(response.cost());
            }
        }

        BigDecimal totalAssets = cash.add(stockValue);
        BigDecimal netWorth = totalAssets.subtract(liabilities);
        BigDecimal pnl = stockValue.subtract(stockCost);
        log.info("[資產] 會員 id={} 淨資產 {}（現金 {} + 股票 {} − 負債 {}），帳戶 {} 筆、持股 {} 檔，耗時 {} ms",
                memberId, netWorth, cash, stockValue, liabilities, accounts.size(), holdings.size(),
                System.currentTimeMillis() - started);
        return new AssetSummaryResponse(cash, stockValue, totalAssets, liabilities, netWorth,
                stockCost, pnl, percent(pnl, stockCost), holdingResponses,
                accounts.stream().map(MoneyAccountResponse::from).toList(), notes);
    }

    @Override
    public AssetSummaryResponse createAccount(long memberId, MoneyAccountRequest request) {
        if (request.kind() == null) {
            throw new BadRequestException("請選擇是現金還是負債");
        }
        if (moneyAccountDao.findByMember(memberId).size() >= MAX_ACCOUNTS) {
            throw new BadRequestException("帳戶最多 " + MAX_ACCOUNTS + " 個");
        }
        MoneyAccount account = moneyAccountDao.save(new MoneyAccount(memberId, request.kind(),
                request.name().trim(), request.balance(), LocalDateTime.now(clock)));
        log.info("[資產] 會員 id={} 新增{}帳戶 id={}", memberId, request.kind().label(), account.getId());
        return summary(memberId);
    }

    @Override
    public AssetSummaryResponse updateAccount(long memberId, long accountId, MoneyAccountRequest request) {
        MoneyAccount account = findAccount(memberId, accountId);
        account.update(request.name().trim(), request.balance(), LocalDateTime.now(clock));
        moneyAccountDao.save(account);
        log.info("[資產] 會員 id={} 修改帳戶 id={}", memberId, accountId);
        return summary(memberId);
    }

    @Override
    public AssetSummaryResponse deleteAccount(long memberId, long accountId) {
        if (!moneyAccountDao.deleteByIdAndMember(accountId, memberId)) {
            throw notFound(memberId, accountId);
        }
        log.info("[資產] 會員 id={} 刪除帳戶 id={}", memberId, accountId);
        return summary(memberId);
    }

    @Override
    public AssetSummaryResponse saveHolding(long memberId, String stockId, HoldingRequest request) {
        String id = stockId.trim().toUpperCase();
        Stock stock = stockDao.findById(id).orElseThrow(() -> new ResourceNotFoundException("查無股票 " + id));
        List<StockHolding> current = stockHoldingDao.findByMember(memberId);
        boolean exists = current.stream().anyMatch(h -> h.getId().getStockId().equals(id));
        if (!exists && current.size() >= MAX_HOLDINGS) {
            throw new BadRequestException("持股最多 " + MAX_HOLDINGS + " 檔");
        }
        stockHoldingDao.upsert(new StockHolding(memberId, stock, request.shares(), request.averageCost(),
                LocalDateTime.now(clock)));
        log.info("[資產] 會員 id={} {}持股 {} {}：{} 股，平均成本 {}", memberId, exists ? "修改" : "新增",
                id, stock.getName(), request.shares(), request.averageCost());
        return summary(memberId);
    }

    @Override
    public AssetSummaryResponse deleteHolding(long memberId, String stockId) {
        String id = stockId.trim().toUpperCase();
        if (!stockHoldingDao.delete(memberId, id)) {
            throw new ResourceNotFoundException("沒有持有 " + id);
        }
        log.info("[資產] 會員 id={} 刪除持股 {}", memberId, id);
        return summary(memberId);
    }

    /**
     * 一檔持股的現值與損益。先確認股價是最新的（OnDemandPriceLoader 有節流，不會一直打外部 API），
     * 補抓失敗就用資料庫裡現有的最新價格；完全沒有股價就只顯示成本。
     */
    private HoldingResponse toResponse(StockHolding holding, List<String> notes) {
        Stock stock = holding.getStock();
        String stockId = stock.getStockId();
        BigDecimal cost = holding.getAverageCost().multiply(BigDecimal.valueOf(holding.getShares()))
                .setScale(2, RoundingMode.HALF_UP);
        try {
            priceLoader.ensureLoaded(stockId);
        } catch (Exception e) {
            log.warn("[資產] {} 更新股價失敗，使用資料庫現有價格：{}", stockId, e.getMessage());
        }

        Optional<DailyPrice> latest = dailyPriceDao.findLatest(stockId);
        if (latest.isEmpty()) {
            notes.add(stockId + " " + stock.getName() + " 沒有股價資料，現值與損益不計入");
            return new HoldingResponse(stockId, stock.getName(), stock.getType(), holding.getShares(),
                    holding.getAverageCost(), cost, null, null, null, null, null);
        }
        BigDecimal price = latest.get().getClosePrice();
        BigDecimal value = price.multiply(BigDecimal.valueOf(holding.getShares())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pnl = value.subtract(cost);
        return new HoldingResponse(stockId, stock.getName(), stock.getType(), holding.getShares(),
                holding.getAverageCost(), cost, price, latest.get().getTradeDate(), value, pnl, percent(pnl, cost));
    }

    private MoneyAccount findAccount(long memberId, long accountId) {
        return moneyAccountDao.findByIdAndMember(accountId, memberId)
                .orElseThrow(() -> notFound(memberId, accountId));
    }

    /** 不是自己的帳戶也回 404（不回 403），不讓人知道那個 id 存不存在 */
    private static ResourceNotFoundException notFound(long memberId, long accountId) {
        log.warn("[資產] 會員 id={} 查無帳戶 id={}（不存在或不是自己的）", memberId, accountId);
        return new ResourceNotFoundException("查無帳戶");
    }

    private static BigDecimal sum(List<MoneyAccount> accounts, AccountKind kind) {
        return accounts.stream()
                .filter(a -> a.getKind() == kind)
                .map(MoneyAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
