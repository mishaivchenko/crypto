package com.crypto.funding.scheduler;

import com.crypto.funding.legacy.execution.LegacyExecutionBlockedException;
import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legacy resource-friendly scheduler:
 * - keeps only ONE scheduled task at a time
 * - re-schedules itself to the next nearest funding
 * - executes a small "due" window to avoid missing events
 *
 * This service is transitional and intentionally fail-closed in safe execution modes.
 */
@Service
public class FundingSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(FundingSchedulerService.class);
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final ApprovedFundingRepository repo;
    private final TaskScheduler scheduler;

    private final OrderExecutorService orderExecutorService;
    private final NetworkLatencyService latencyService;
    private final LegacyExecutionGuard legacyExecutionGuard;

    private final Duration lookahead;
    private final Duration minRecheckDelay;
    private final Duration executionDelay;

    private volatile ScheduledFuture<?> current;
    private volatile Long currentFundingId;
    private volatile Instant currentPlannedAt;
    private final Duration maxLateness;
    private final Duration discoveryInterval;

    private final AtomicBoolean tickRunning = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Autowired
    public FundingSchedulerService(
        ApprovedFundingRepository repo,
        TaskScheduler fundingTaskScheduler, OrderExecutorService orderExecutorService,
        NetworkLatencyService latencyService,
        LegacyExecutionGuard legacyExecutionGuard,
        @Value("${funding.scheduler.lookahead-seconds:10}") long lookaheadSeconds,
        @Value("${funding.execution-delay-seconds:8}") long executionDelaySeconds,
        @Value("${funding.scheduler.min-recheck-millis:1000}") long minRecheckMillis,
        @Value("${funding.scheduler.max-lateness-seconds:120}") long maxLatenessSeconds,
        @Value("${funding.scheduler.discovery-interval-seconds:15}") long discoveryIntervalSeconds ) {
        this.repo = repo;
        this.scheduler = fundingTaskScheduler;
        this.orderExecutorService = orderExecutorService;
        this.latencyService = latencyService;
        this.legacyExecutionGuard = legacyExecutionGuard;
        this.lookahead = Duration.ofSeconds(Math.max(1, lookaheadSeconds));
        this.executionDelay = Duration.ofSeconds(Math.max(0, executionDelaySeconds));
        this.minRecheckDelay = Duration.ofMillis(Math.max(200, minRecheckMillis));
        this.maxLateness = Duration.ofSeconds(Math.max(5, maxLatenessSeconds));
        this.discoveryInterval = Duration.ofSeconds(Math.max(5, discoveryIntervalSeconds));
    }

    FundingSchedulerService(
        ApprovedFundingRepository repo,
        TaskScheduler fundingTaskScheduler, OrderExecutorService orderExecutorService,
        NetworkLatencyService latencyService,
        long lookaheadSeconds,
        long executionDelaySeconds,
        long minRecheckMillis,
        long maxLatenessSeconds,
        long discoveryIntervalSeconds ) {
        this(
            repo,
            fundingTaskScheduler,
            orderExecutorService,
            latencyService,
            LegacyExecutionGuard.permissive(),
            lookaheadSeconds,
            executionDelaySeconds,
            minRecheckMillis,
            maxLatenessSeconds,
            discoveryIntervalSeconds
        );
    }

    @PostConstruct
    public void start() {
        wakeup("startup");
    }

    @PreDestroy
    void stop()
    {
        shuttingDown.set( true );
        synchronized( this )
        {
            if( current != null )
            {
                current.cancel( false );
            }
        }
    }

    public synchronized void wakeup(String reason) {
        if( shuttingDown.get() )
        {
            return;
        }
        reschedule(reason);
    }

    private synchronized void reschedule(String reason) {
        if( shuttingDown.get() )
        {
            return;
        }
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
            Set<String> exchanges = next.getExchanges().stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

            // 🔥 ВОТ ЭТО СНИМАЕТ БЕСКОНЕЧНЫЙ ТИК:
            // Если funding слишком старый — считаем "missed" и помечаем executed=true,
            // чтобы scheduler не пытался его бесконечно догонять.
            if (nextFundingAt.isBefore(now.minus(maxLateness))) {
                if (legacyExecutionGuard.canMutateLegacyState()) {
                    orderExecutorService.markMissed(next.getId(), next.getSymbol(), nextFundingAt, now);
                    // и снова ищем следующий (loop)
                    continue;
                }

                Optional<ApprovedFundingEntity> nextEligible =
                    repo.findFirstByActiveTrueAndExecutedFalseAndNextFundingAtAfterOrderByNextFundingAtAsc(now.minus(maxLateness));

                if (nextEligible.isEmpty()) {
                    log.info(
                        "[scheduler] passive mode detected stale legacy funding id={} symbol={} and found no newer schedulable items",
                        next.getId(),
                        next.getSymbol()
                    );
                    scheduleOnce(now.plus(discoveryInterval), null);
                    return;
                }

                next = nextEligible.get();
                nextFundingAt = next.getNextFundingAt();
            }

            Duration networkDelay = latencyService.estimate(exchanges);
            Duration totalDelay = executionDelay.plus(networkDelay);

            Instant planned = nextFundingAt.minus(totalDelay);
            if (planned.isBefore(now.plus(minRecheckDelay))) {
                planned = now.plus(minRecheckDelay);
            }

            Instant discovery = now.plus(discoveryInterval);
            Instant runAt = planned.isBefore(discovery) ? planned : discovery;

            scheduleOnce(runAt, next.getId());
            logNext(next.getSymbol(), nextFundingAt, runAt, reason, networkDelay);
            return;
        }
    }

    private void logNext(String symbol, Instant nextFundingAt, Instant planned, String reason, Duration networkDelay) {
        ZonedDateTime kyivFunding = nextFundingAt.atZone(KYIV);
        ZonedDateTime kyivPlanned = planned.atZone(KYIV);

        log.info(
            "[scheduler] scheduled symbol={} fundingAt={} (UTC/{}) runAt={} (UTC/{}) reason={} execDelay={}s netDelay={}ms",
            symbol,
            nextFundingAt, kyivFunding,
            planned, kyivPlanned,
            reason,
            executionDelay.toSeconds(),
            networkDelay.toMillis()
        );
    }

    private void scheduleOnce(Instant runAt, Long fundingId) {
        if( shuttingDown.get() )
        {
            return;
        }
        if (current != null && !current.isDone() && !current.isCancelled()) {
            // если уже запланировано раньше или точно так же — не трогаем
            if (fundingId != null && fundingId.equals(currentFundingId) && runAt.equals(currentPlannedAt)) {
                return;
            }
            current.cancel(false);
        }

        currentFundingId = fundingId;
        currentPlannedAt = runAt;
        try {
            current = scheduler.schedule( this::tick, Date.from( runAt ) );
        } catch (TaskRejectedException e) {
            // Контекст мог начать shutdown (например, при старте с ошибкой).
            log.warn("[scheduler] schedule rejected (shutdown in progress): {}", e.getMessage());
        }
    }

    private void tick() {
        if( shuttingDown.get() )
        {
            return;
        }
        if (!tickRunning.compareAndSet(false, true)) {
            return;
        }

        log.debug("[scheduler] tick start");
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
        log.debug("[scheduler] processing due window");
        Instant now = Instant.now();
        Instant targetFundingAt = now.plus(executionDelay);
        Instant from = targetFundingAt.minus(maxLateness);    // ✅ теперь подхватит чуть просроченные
        Instant to = targetFundingAt.plus(lookahead);

        List<ApprovedFundingEntity> nextFundings =
            repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween(from, to);

        if (nextFundings.isEmpty()) {
            log.debug(
                "[scheduler] no fundings between {} and {}",
                LocalDate.ofInstant(from, ZoneId.systemDefault()),
                LocalDate.ofInstant(to, ZoneId.systemDefault())
            );
            return;
        }

        log.info("[scheduler] processing {} funding(s) in window {}..{}", nextFundings.size(),
            LocalDate.ofInstant(from, ZoneId.systemDefault()),
            LocalDate.ofInstant(to, ZoneId.systemDefault()));

        for (ApprovedFundingEntity funding : nextFundings) {
            try {
                log.info("[scheduler] executing funding id={} symbol={} exchanges={} nextAt={}",
                    funding.getId(), funding.getSymbol(), funding.getExchanges(), funding.getNextFundingAt());
                orderExecutorService.executeOnce( funding.getId() );
            } catch (LegacyExecutionBlockedException ex) {
                log.info("[scheduler] passive execution blocked for {} (id={}): {}",
                    funding.getSymbol(), funding.getId(), ex.getMessage());
            } catch (Exception ex) {
                log.error("[scheduler] execution failed for {} (id={})", funding.getSymbol(), funding.getId(), ex);
                funding.setActive( false );
                repo.save( funding );
            }
        }
    }
}
