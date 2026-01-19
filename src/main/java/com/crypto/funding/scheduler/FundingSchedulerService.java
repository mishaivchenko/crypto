package com.crypto.funding.scheduler;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.OrderSide;
import com.crypto.funding.trading.OrderType;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resource-friendly scheduler:
 * - keeps only ONE scheduled task at a time
 * - re-schedules itself to the next nearest funding
 * - executes a small "due" window to avoid missing events
 */
@Service
public class FundingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FundingSchedulerService.class);
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final ApprovedFundingRepository repo;
    private final TestOrderEngine testOrderEngine;
    private final TaskScheduler scheduler;

    private final Duration lookahead;
    private final Duration minRecheckDelay;

    private volatile ScheduledFuture<?> current;
    private volatile Long currentFundingId;
    private volatile Instant currentPlannedAt;
    private final Duration maxLateness;
    private final Duration discoveryInterval;

    private final AtomicBoolean tickRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    public FundingSchedulerService(
        ApprovedFundingRepository repo,
        TestOrderEngine testOrderEngine,
        TaskScheduler fundingTaskScheduler,
        @Value("${funding.scheduler.lookahead-seconds:10}") long lookaheadSeconds,
        @Value("${funding.scheduler.min-recheck-millis:1000}") long minRecheckMillis,
        @Value("${funding.scheduler.max-lateness-seconds:120}") long maxLatenessSeconds,
        @Value("${funding.scheduler.discovery-interval-seconds:15}") long discoveryIntervalSeconds ) {
        this.repo = repo;
        this.testOrderEngine = testOrderEngine;
        this.scheduler = fundingTaskScheduler;
        this.lookahead = Duration.ofSeconds(Math.max(1, lookaheadSeconds));
        this.minRecheckDelay = Duration.ofMillis(Math.max(200, minRecheckMillis));
        this.maxLateness = Duration.ofSeconds(Math.max(5, maxLatenessSeconds));
        this.discoveryInterval = Duration.ofSeconds(Math.max(5, discoveryIntervalSeconds));
    }

    @PostConstruct
    public void start() {
        wakeup("startup");
    }

    public synchronized void wakeup(String reason) {
        reschedule(reason);
    }

    private synchronized void reschedule(String reason) {
        Instant now = Instant.now();

        while (true) {
            Optional<ApprovedFundingEntity> nextOpt =
                repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc();

            if (nextOpt.isEmpty()) {
                scheduleOnce(now.plus(minRecheckDelay.multipliedBy(10)), null);
                return;
            }

            ApprovedFundingEntity next = nextOpt.get();
            Instant nextFundingAt = next.getNextFundingAt();

            // 🔥 ВОТ ЭТО СНИМАЕТ БЕСКОНЕЧНЫЙ ТИК:
            // Если funding слишком старый — считаем "missed" и помечаем executed=true,
            // чтобы scheduler не пытался его бесконечно догонять.
            if (nextFundingAt.isBefore(now.minus(maxLateness))) {
                markMissed(next.getId(), next.getSymbol(), nextFundingAt, now);
                // и снова ищем следующий (loop)
                continue;
            }

            Instant planned = nextFundingAt.minus(lookahead);
            if (planned.isBefore(now.plus(minRecheckDelay))) {
                planned = now.plus(minRecheckDelay);
            }

            Instant discovery = now.plus(discoveryInterval);
            Instant runAt = planned.isBefore(discovery) ? planned : discovery;

            scheduleOnce(runAt, next.getId());
            logNext(next.getSymbol(), nextFundingAt, runAt, reason);
            return;
        }
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

    private void logNext(String symbol, Instant nextFundingAt, Instant planned, String reason) {
        ZonedDateTime kyivFunding = nextFundingAt.atZone(KYIV);
        ZonedDateTime kyivPlanned = planned.atZone(KYIV);

        log.info(
            "[scheduler] next funding {} at {} (UTC) / {} (Kyiv); scheduling run at {} (UTC) / {} (Kyiv) ({})",
            symbol,
            nextFundingAt,
            kyivFunding,
            planned,
            kyivPlanned,
            reason
        );
    }

    private void scheduleOnce(Instant runAt, Long fundingId) {
        if (current != null && !current.isDone() && !current.isCancelled()) {
            // если уже запланировано раньше или точно так же — не трогаем
            if (fundingId != null && fundingId.equals(currentFundingId) && runAt.equals(currentPlannedAt)) {
                return;
            }
            current.cancel(false);
        }

        currentFundingId = fundingId;
        currentPlannedAt = runAt;
        current = scheduler.schedule( this::tick, Date.from( runAt ) );
    }

    private void tick() {
        if (!tickRunning.compareAndSet(false, true)) {
            return;
        }

        log.info( "new tick" );
        try {
            processDueWindow();
        } catch (Exception e) {
            log.error("[scheduler] tick failed", e);
        } finally {
            // больше никаких tick-done/tick-done/...
            tickRunning.set(false);
            wakeup("tick");
        }
    }

    private void processDueWindow() {
        log.info( "Start processing" );
        Instant now = Instant.now();
        Instant from = now.minus(maxLateness);    // ✅ теперь подхватит чуть просроченные
        Instant to = now.plus(lookahead);

        List<ApprovedFundingEntity> due =
            repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween(from, to);

        if (due.isEmpty())
        {
            log.info( "Not found events for processing between {} and {}", LocalDate.ofInstant( from, ZoneId.systemDefault() ),LocalDate.ofInstant( to, ZoneId.systemDefault()  ));
            return;
        }

        for (ApprovedFundingEntity e : due) {
            try {
                log.info( "Approving {}", e );
                executeOnce(e.getId());
            } catch (Exception ex) {
                log.error("[scheduler] execution failed for {} (id={})", e.getSymbol(), e.getId(), ex);
                e.setActive( false );
                repo.save( e );
            }
        }
    }

    @Transactional
    void executeOnce(Long id) {

        ApprovedFundingEntity e = repo.findById(id).orElse(null);
        log.info( "Executing {}", e);
        if (e == null) return;
        if (!e.isActive() || e.isExecuted()) return;

        Set<String> exchanges = e.getExchanges();
        if (exchanges == null || exchanges.isEmpty()) {
            log.warn("[scheduler] skip id={} symbol={} because exchanges empty", e.getId(), e.getSymbol());
            e.setExecuted(true);
            repo.save( e );
            return;
        }

        String symbolUnified = unifySymbol(e.getSymbol());
        BigDecimal qty = BigDecimal.ONE;

        for (String ex : exchanges) {
            String exchange = normalizeExchange(ex);

            PlaceTestOrderCommand cmd = new PlaceTestOrderCommand(
                exchange,
                symbolUnified,
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

    private static String unifySymbol(String symbol) {
        if (symbol == null) return "";
        return symbol.replace("/", "").trim().toUpperCase( Locale.ROOT);
    }

    private static String normalizeExchange(String ex) {
        if (ex == null) return "";
        return ex.trim().toLowerCase(Locale.ROOT);
    }
}
