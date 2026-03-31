package com.crypto.funding.scheduler;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.legacy.execution.LegacyExecutionDecision;
import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.*;
import com.crypto.funding.persistence.service.OrderExecutionTimeStore;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Legacy funding executor kept only as a transitional component during the Phase 0-1 rewrite.
 * Real trading evolution must happen through the new domain/application layers.
 */
@Service
public class OrderExecutorService
{
    private static final Logger log = LoggerFactory.getLogger( OrderExecutorService.class);
    private final TestOrderEngine testOrderEngine;
    private final ApprovedFundingRepository repo;
    private final OrderExecutionTimeStore orderExecutionTimeStore;

    private final List<AbstractRestClient> exchangeRestClients;
    private final NetworkLatencyService latencyService;
    private final boolean latencyProbeEnabled;
    private final LegacyExecutionGuard legacyExecutionGuard;

    @Autowired
    public OrderExecutorService( TestOrderEngine orderEngine,
                                 ApprovedFundingRepository repository,
                                 OrderExecutionTimeStore orderExecutionTimeStore,
                                 List<? extends AbstractRestClient> restClients,
                                 NetworkLatencyService latencyService,
                                 @Value("${funding.latency-probe.enabled:false}") boolean latencyProbeEnabled,
                                 LegacyExecutionGuard legacyExecutionGuard )
    {
        this.testOrderEngine = orderEngine;
        this.repo = repository;
        this.orderExecutionTimeStore = orderExecutionTimeStore;
        this.exchangeRestClients = List.copyOf( restClients );
        this.latencyService = latencyService;
        this.latencyProbeEnabled = latencyProbeEnabled;
        this.legacyExecutionGuard = legacyExecutionGuard;
    }

    public OrderExecutorService( TestOrderEngine orderEngine,
                                 ApprovedFundingRepository repository,
                                 OrderExecutionTimeStore orderExecutionTimeStore,
                                 List<? extends AbstractRestClient> restClients,
                                 NetworkLatencyService latencyService,
                                 boolean latencyProbeEnabled )
    {
        this(
            orderEngine,
            repository,
            orderExecutionTimeStore,
            restClients,
            latencyService,
            latencyProbeEnabled,
            LegacyExecutionGuard.permissive()
        );
    }

    @Transactional
    public void executeOnce(Long id) throws Exception
    {
        ApprovedFundingEntity entity = repo.findById(id).orElse(null);
        if (entity == null || !entity.isActive() || entity.isExecuted()) {
            return;
        }

        log.info("[scheduler] executing id={} symbol={} exchanges={} amount={} nextFundingAt={}",
            entity.getId(), entity.getSymbol(), entity.getExchanges(), entity.getUsdtAmount(), entity.getNextFundingAt());

        Set<String> exchanges = normalizedExchanges(entity);
        if (exchanges.isEmpty()) {
            log.warn("[scheduler] passive skip id={} symbol={} because exchanges empty", entity.getId(), entity.getSymbol());
            return;
        }

        LegacyExecutionDecision decision = legacyExecutionGuard.evaluate(exchanges, "legacy-funding-scheduler");
        if (!decision.allowed()) {
            log.info(
                "[scheduler] passive execution skip id={} symbol={} mode={} reason={}",
                entity.getId(),
                entity.getSymbol(),
                decision.mode(),
                decision.reason()
            );
            return;
        }

        List<AbstractRestClient> clients = exchangeRestClients.stream()
            .filter(rc -> decision.executableVenues().contains(normalizeExchange(rc.exchangeName())))
            .filter(this::isConfigured)
            .toList();

        if (clients.isEmpty()) {
            log.warn(
                "[scheduler] passive execution skip id={} symbol={} because no executable configured clients matched requestedExchanges={}",
                entity.getId(),
                entity.getSymbol(),
                decision.executableVenues()
            );
            return;
        }

        for (AbstractRestClient client : clients) {
            processExchange(entity, client);
        }

        markExecuted(entity);
        log.info("[scheduler] funding executed id={} symbol={} exchanges={} executedAt={}",
            entity.getId(), entity.getSymbol(), entity.getExchanges(), entity.getExecutedAt());
    }

    private void processExchange(ApprovedFundingEntity entity, AbstractRestClient client) throws Exception {
        String exchange = normalizeExchange(client.exchangeName());

        FundingInfo fundingInfo = client.fetchFunding(entity.getSymbolUnified());
        SymbolRules symbolRules = client.fetchRules(entity.getSymbolUnified());
        BigDecimal price = fundingInfo.price();

        BigDecimal minTradeQty = minQtyRespectingNotional(symbolRules, price);
        BigDecimal requiredUsdt = minTradeQty.multiply(price);
        if (entity.getUsdtAmount().compareTo(requiredUsdt) < 0) {
            throw new IllegalArgumentException("Too small. Need >= " + requiredUsdt + " USDT for min order (includes notional & step constraints)");
        }

        maybeProbeLatency(entity, client, fundingInfo, minTradeQty, exchange);

        BigDecimal qty = calculateOrderQty(entity, price, symbolRules);

        TestOrderResult result = placeOrder(client, exchange, entity.getSymbolUnified(), qty);
        result = ensureExecutionTimestamp(result, client, entity.getSymbolUnified(), exchange);

        orderExecutionTimeStore.save(
            result.exchange(),
            result.exchangeOrderId(),
            result.symbolUnified(),
            result.tsMillis(),
            result.exchangeTsMillis(),
            result.timestampSource().name(),
            entity.getNextFundingAt()
        );

        log.info("[scheduler] order result: exchange={} symbol={} status={} orderId={} serverTs={} exchangeTs={} source={}",
            result.exchange(), result.symbolUnified(), result.status(), result.exchangeOrderId(), result.tsMillis(), result.exchangeTsMillis(), result.timestampSource());
    }

    private void maybeProbeLatency(ApprovedFundingEntity entity,
                                   AbstractRestClient client,
                                   FundingInfo fundingInfo,
                                   BigDecimal minTradeQty,
                                   String exchange) {
        if (!latencyProbeEnabled) return;

        PlaceTestOrderCommand latencyCmd = new PlaceTestOrderCommand(
            exchange,
            entity.getSymbolUnified(),
            OrderSide.BUY,
            OrderType.MARKET,
            minTradeQty,
            null
        );

        try {
            TestOrderResult probeResult = latencyService.measureAndRecord(exchange, () -> testOrderEngine.placeTestOrder(latencyCmd));
            tryCloseTestOrder(client, probeResult);
        } catch (Exception probeEx) {
            log.warn("[scheduler] latency probe failed for exchange={} symbol={} : {}", exchange, entity.getSymbol(), probeEx.getMessage());
            return;
        }

        Duration measuredDelay = latencyService.estimate(exchange);
        Instant targetFundingAt = Optional.ofNullable(fundingInfo.nextFundingAt()).orElse(entity.getNextFundingAt());
        Duration safeDelay = measuredDelay == null ? Duration.ZERO : measuredDelay;
        if (targetFundingAt != null && !safeDelay.isNegative()) {
            Instant sendAt = targetFundingAt.minus(safeDelay);
            Duration wait = Duration.between(Instant.now(), sendAt);
            if (!wait.isNegative()) {
                try {
                    Thread.sleep(wait.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private BigDecimal calculateOrderQty(ApprovedFundingEntity entity, BigDecimal price, SymbolRules rules) {
        BigDecimal rawQty = entity.getUsdtAmount().divide(price, 12, RoundingMode.DOWN);
        BigDecimal qty = floorToStep(rawQty, rules.qtyStep());

        BigDecimal minTradeQty = minQtyRespectingNotional(rules, price);
        if (qty.compareTo(minTradeQty) < 0) {
            BigDecimal needAtLeast = minTradeQty.multiply(price);
            throw new IllegalArgumentException("Too small. Need >= " + needAtLeast + " USDT for minQty/minNotional constraints");
        }
        return qty;
    }

    private TestOrderResult placeOrder(AbstractRestClient client, String exchange, String unifiedSymbol, BigDecimal qty) throws Exception {
        PlaceTestOrderCommand cmd = new PlaceTestOrderCommand(
            exchange,
            unifiedSymbol,
            OrderSide.BUY,
            OrderType.MARKET,
            qty,
            null
        );
        return testOrderEngine.placeTestOrder(cmd);
    }

    private TestOrderResult ensureExecutionTimestamp(TestOrderResult result,
                                                     AbstractRestClient client,
                                                     String unifiedSymbol,
                                                     String exchange) {
        if (result.exchangeExecutedAt() != null && result.exchangeExecutedAt().toEpochMilli() > 0) {
            return result;
        }

        try {
            Long fetchedTs = client.fetchOrderTimestamp(unifiedSymbol, result.exchangeOrderId());
            if (fetchedTs != null) {
                return result.withExchangeTimestamp(fetchedTs, OrderTimestampSource.FOLLOW_UP_QUERY);
            }
        } catch (Exception tsEx) {
            log.warn("[scheduler] failed to fetch order timestamp exchange={} orderId={} : {}", exchange, result.exchangeOrderId(), tsEx.getMessage());
        }

        log.warn("[scheduler] {} missing execution time; defaulting to server timestamp orderId={}", exchange, result.exchangeOrderId());
        return result.withExchangeTimestamp(result.tsMillis(), OrderTimestampSource.UNKNOWN);
    }

    private Set<String> normalizedExchanges(ApprovedFundingEntity entity) {
        return entity.getExchanges().stream()
            .map(OrderExecutorService::normalizeExchange)
            .collect(Collectors.toSet());
    }

    private void markExecuted(ApprovedFundingEntity entity) {
        entity.setExecuted(true);
        entity.setExecutedAt(Instant.now());
        repo.save(entity);
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

    private void tryCloseTestOrder(AbstractRestClient client, TestOrderResult result) {
        try {
            client.cancelTestOrder(result.symbolUnified(), result.exchangeOrderId());
        } catch (Exception closeEx) {
            String msg = closeEx.getMessage();
            // Bybit: retCode=110001 "order not exists or too late to cancel" — считаем безопасным
            if (msg != null && msg.contains("110001")) {
                log.debug("[scheduler] latency probe close ignored (already filled/absent) exchange={} symbol={}", client.exchangeName(), result.symbolUnified());
            } else {
                log.warn("[scheduler] latency probe close failed for exchange={} symbol={} : {}", client.exchangeName(), result.symbolUnified(), msg);
            }
        }
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
        if (!legacyExecutionGuard.canMutateLegacyState()) {
            log.warn(
                "[scheduler] passive stale funding detected: symbol={} id={} nextFundingAt={} now={} mode={}",
                symbol,
                id,
                nextFundingAt,
                now,
                legacyExecutionGuard.mode()
            );
            return;
        }

        ApprovedFundingEntity e = repo.findById(id).orElse(null);
        if (e == null || e.isExecuted() || !e.isActive()) return;

        // если кто-то успел изменить — optimistic lock спасёт
        e.setExecuted(true);
        e.setExecutedAt(now);
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
