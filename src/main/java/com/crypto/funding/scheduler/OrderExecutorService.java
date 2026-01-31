package com.crypto.funding.scheduler;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.*;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service

public class OrderExecutorService
{
    private static final Logger log = LoggerFactory.getLogger( OrderExecutorService.class);
    private static final Duration MAX_ALIGN_WAIT = Duration.ofSeconds(2);
    private final TestOrderEngine testOrderEngine;
    private final ApprovedFundingRepository repo;

    private final List<AbstractRestClient> exchangeRestClients;
    private final NetworkLatencyService latencyService;
    private final boolean latencyProbeEnabled;

    public OrderExecutorService( TestOrderEngine orderEngine,
                                 ApprovedFundingRepository repository,
                                 List<AbstractRestClient> restClients,
                                 NetworkLatencyService latencyService,
                                 @Value("${funding.latency-probe.enabled:true}") boolean latencyProbeEnabled )
    {
        this.testOrderEngine = orderEngine;
        this.repo = repository;
        this.exchangeRestClients = restClients;
        this.latencyService = latencyService;
        this.latencyProbeEnabled = latencyProbeEnabled;
    }

    @Transactional
    public void executeOnce(Long id) throws Exception
    {

        ApprovedFundingEntity e = repo.findById(id).orElse(null);

        log.info( "Executing {}", e);
        if (e == null) return;
        if (!e.isActive() || e.isExecuted()) return;

        Set<String> exchanges = e.getExchanges().stream().map( OrderExecutorService::normalizeExchange ).collect( Collectors.toSet());
        if ( exchanges.isEmpty() ) {
            log.warn("[scheduler] skip id={} symbol={} because exchanges empty", e.getId(), e.getSymbol());
            e.setExecuted(true);
            repo.save( e );
            return;
        }

        var restClients  = exchangeRestClients.stream().filter( restClient -> exchanges.contains( restClient.exchangeName() ) ).toList();
        restClients = restClients.stream().filter(this::isConfigured).toList();

        for (AbstractRestClient ex : restClients) {
            String exchange = normalizeExchange( ex.exchangeName() );

            FundingInfo fundingInfo = ex.fetchFunding( e.getSymbolUnified() );
            SymbolRules symbolRules = ex.fetchRules( e.getSymbolUnified() );

            BigDecimal price = fundingInfo.price();
            BigDecimal minTradeQty = minQtyRespectingNotional(symbolRules, price);
            BigDecimal requiredUsdt = minTradeQty.multiply(price);

            if (e.getUsdtAmount().compareTo(requiredUsdt) < 0) {
                throw new IllegalArgumentException("Too small. Need >= " + requiredUsdt + " USDT for min order (includes notional & step constraints)");
            }

            if (latencyProbeEnabled) {
                // Предзапуск: минимальный тестовый ордер для оценки задержки сети
                PlaceTestOrderCommand latencyCmd = new PlaceTestOrderCommand(
                    exchange,
                    e.getSymbolUnified(),
                    OrderSide.BUY,
                    OrderType.MARKET,
                    minTradeQty,
                    null
                );
                try {
                    TestOrderResult probeResult = latencyService.measureAndRecord(exchange, () -> testOrderEngine.placeTestOrder(latencyCmd));
                    // Попытка закрыть тестовый ордер тем же объёмом
                    PlaceTestOrderCommand closeCmd = new PlaceTestOrderCommand(
                        exchange,
                        e.getSymbolUnified(),
                        OrderSide.SELL,
                        OrderType.MARKET,
                        probeResult.quantity() != null ? probeResult.quantity() : minTradeQty,
                        null
                    );
                    try {
                        testOrderEngine.placeTestOrder(closeCmd);
                    } catch (Exception closeEx) {
                        log.warn("[scheduler] latency probe close failed for exchange={} symbol={} : {}", exchange, e.getSymbol(), closeEx.getMessage());
                    }
                } catch (Exception probeEx) {
                    log.warn("[scheduler] latency probe failed for exchange={} symbol={} : {}", exchange, e.getSymbol(), probeEx.getMessage());
                    continue;
                }

                Duration measuredDelay = latencyService.estimate(exchange);
                Instant targetFundingAt = Optional.ofNullable(fundingInfo.nextFundingAt()).orElse(e.getNextFundingAt());
                if (targetFundingAt != null && measuredDelay != null && !measuredDelay.isZero() && !measuredDelay.isNegative()) {
                    Instant sendAt = targetFundingAt.minus(measuredDelay);
                    Duration wait = Duration.between(Instant.now(), sendAt);
                    if (wait.compareTo(MAX_ALIGN_WAIT) > 0) {
                        wait = MAX_ALIGN_WAIT;
                    }
                    if (!wait.isNegative()) {
                        try {
                            Thread.sleep(wait.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            BigDecimal rawQty = e.getUsdtAmount().divide(price, 12, RoundingMode.DOWN);
            BigDecimal qty = floorToStep(rawQty, symbolRules.qtyStep());

            if (qty.compareTo(minTradeQty) < 0) {
                BigDecimal needAtLeast = minTradeQty.multiply(price);
                throw new IllegalArgumentException("Too small. Need >= " + needAtLeast + " USDT for minQty/minNotional constraints");
            }

            PlaceTestOrderCommand cmd = new PlaceTestOrderCommand(
                exchange,
                e.getSymbolUnified(),
                OrderSide.BUY,
                OrderType.MARKET,
                qty,
                null
            );

            TestOrderResult r = testOrderEngine.placeTestOrder(cmd);
            log.info("[scheduler] test order result: exchange={} symbol={} status={} orderId={}",
                r.exchange(), r.symbolUnified(), r.status(), r.exchangeOrderId());
        }

        e.setExecuted(true);
        repo.save( e );
    }

    public static BigDecimal floorToStep(BigDecimal qty, BigDecimal step) {
        if (step.signum() <= 0) {
            throw new IllegalArgumentException("step must be > 0");
        }

        BigDecimal steps = qty.divide(step, 0, RoundingMode.DOWN);
        BigDecimal floored = steps.multiply(step);

        int scale = step.stripTrailingZeros().scale();
        return floored.setScale(Math.max(scale, 0), RoundingMode.DOWN);
    }

    public static BigDecimal ceilToStep(BigDecimal qty, BigDecimal step) {
        if (step.signum() <= 0) {
            throw new IllegalArgumentException("step must be > 0");
        }

        BigDecimal steps = qty.divide(step, 0, RoundingMode.UP);
        BigDecimal ceiled = steps.multiply(step);

        int scale = step.stripTrailingZeros().scale();
        return ceiled.setScale(Math.max(scale, 0), RoundingMode.DOWN);
    }

    static BigDecimal minQtyRespectingNotional(SymbolRules rules, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }

        BigDecimal step = rules.qtyStep();
        BigDecimal minQty = ceilToStep(rules.minOrderQty(), step);

        if (rules.minNotionalValue() == null) {
            return minQty;
        }

        BigDecimal qtyByNotional = rules.minNotionalValue().divide(price, 12, RoundingMode.UP);
        BigDecimal alignedNotionalQty = ceilToStep(qtyByNotional, step);

        return minQty.max(alignedNotionalQty);
    }

    @Transactional
    void markMissed(Long id, String symbol, Instant nextFundingAt, Instant now) {
        ApprovedFundingEntity e = repo.findById(id).orElse(null);
        if (e == null || e.isExecuted() || !e.isActive()) return;

        // если кто-то успел изменить — optimistic lock спасёт
        e.setExecuted(true);
        repo.save(e);

        log.warn(
            "[scheduler] funding MISSED: symbol={} id={} nextFundingAt={} (UTC) now={} (UTC). Marked executed=true",
            symbol, id, nextFundingAt, now
        );
    }


    private static String normalizeExchange(String ex) {
        if (ex == null) return "";
        return ex.trim().toLowerCase( Locale.ROOT);
    }

    private boolean isConfigured(AbstractRestClient client) {
        if (client instanceof AbstractRestClient arc) {
            return isNonBlank(arc.getApiKey()) && isNonBlank(arc.getSecretKey()) && isNonBlank(arc.getBaseUrl());
        }
        return true;
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
