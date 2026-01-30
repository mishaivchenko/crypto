package com.crypto.funding.scheduler;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundingSchedulerServiceTest {

    @Mock
    private ApprovedFundingRepository repo;

    @Mock
    private OrderExecutorService orderExecutorService;
    @Mock
    private NetworkLatencyService latencyService;

    private RecordingTaskScheduler taskScheduler;
    private FundingSchedulerService service;

    @BeforeEach
    void setUp() {
        taskScheduler = new RecordingTaskScheduler();
        service = new FundingSchedulerService(
            repo,
            taskScheduler,
            orderExecutorService,
            latencyService,
            5,   // lookaheadSeconds
            1,   // executionDelaySeconds
            200, // minRecheckMillis
            10,  // maxLatenessSeconds
            2    // discoveryIntervalSeconds
        );
        lenient().when(latencyService.estimate(any(Set.class))).thenReturn(Duration.ZERO);
    }

    @Test
    void processesDueWindowAndExecutesEntries() throws Exception {
        ApprovedFundingEntity next = sampleEntity("BTC/USDT", Instant.now().plusSeconds(5));

        when(repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc())
            .thenReturn(Optional.of(next));
        when(repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween(any(), any()))
            .thenReturn(List.of(next));
        when(latencyService.estimate(any(Set.class))).thenReturn(Duration.ofMillis(0));

        service.wakeup("test");
        taskScheduler.runLatest(); // вызывает tick

        verify(orderExecutorService).executeOnce(next.getId());
        verify(repo, never()).save(any()); // успешный путь не трогает entity внутри scheduler
    }

    @Test
    void deactivatesOnExecutionError() throws Exception {
        ApprovedFundingEntity next = sampleEntity("ETH/USDT", Instant.now().plusSeconds(5));

        when(repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc())
            .thenReturn(Optional.of(next));
        when(repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween(any(), any()))
            .thenReturn(List.of(next));

        doThrow(new RuntimeException("boom")).when(orderExecutorService).executeOnce(anyLong());
        when(latencyService.estimate(any(Set.class))).thenReturn(Duration.ofMillis(0));

        service.wakeup("test");
        taskScheduler.runLatest();

        ArgumentCaptor<ApprovedFundingEntity> captor = ArgumentCaptor.forClass(ApprovedFundingEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void schedulesEarlierByNetworkDelay() {
        Instant nextFunding = Instant.now().plusSeconds(20);
        ApprovedFundingEntity next = sampleEntity("BTC/USDT", nextFunding);

        // Используем увеличенный discoveryInterval, чтобы планирование опиралось на расчётное время
        service = new FundingSchedulerService(
            repo,
            taskScheduler,
            orderExecutorService,
            latencyService,
            5,
            1,
            200,
            10,
            30
        );

        when(repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc())
            .thenReturn(Optional.of(next));
        when(latencyService.estimate(any(Set.class))).thenReturn(Duration.ofSeconds(2)); // учесть 2с задержки

        service.wakeup("test-latency");

        Instant planned = taskScheduler.getLastDate().toInstant();
        Instant expected = nextFunding.minusSeconds(1).minusSeconds(2); // executionDelay=1s + latency 2s
        // допускаем минимальный recheck 200ms
        assertThat(planned).isBetween(expected.minusMillis(10), expected.plusMillis(250));
    }

    // Helpers
    private ApprovedFundingEntity sampleEntity(String symbol, Instant next) {
        ApprovedFundingEntity e = new ApprovedFundingEntity(symbol, Set.of("binance"), new BigDecimal("10"), next);
        e.setActive(true);
        e.setExecuted(false);
        return e;
    }

    /**
     * Минимальный TaskScheduler, который просто запоминает последний runnable и не запускает автоматически.
     */
    static class RecordingTaskScheduler implements TaskScheduler {
        private Runnable lastRunnable;
        private Date lastDate;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
            this.lastRunnable = task;
            this.lastDate = startTime;
            cancelled.set(false);
            return new DummyFuture(cancelled);
        }

        void runLatest() {
            if (lastRunnable != null && !cancelled.get()) {
                lastRunnable.run();
            }
        }

        public Date getLastDate() {
            return lastDate;
        }

        // Unused overloads
        @Override public ScheduledFuture<?> schedule(Runnable task, Instant startTime) { return schedule(task, Date.from(startTime)); }
        @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { return schedule(task, new Date()); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) { return schedule(task, startTime); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) { return schedule(task, Date.from(startTime)); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) { return schedule(task, new Date()); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) { return schedule(task, new Date()); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) { return schedule(task, startTime); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) { return schedule(task, Date.from(startTime)); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) { return schedule(task, new Date()); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) { return schedule(task, new Date()); }
    }

    static class DummyFuture implements ScheduledFuture<Object> {
        private final AtomicBoolean cancelled;

        DummyFuture(AtomicBoolean cancelled) { this.cancelled = cancelled; }
        @Override public long getDelay(java.util.concurrent.TimeUnit unit) { return 0; }
        @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return cancelled.compareAndSet(false, true); }
        @Override public boolean isCancelled() { return cancelled.get(); }
        @Override public boolean isDone() { return cancelled.get(); }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, java.util.concurrent.TimeUnit unit) { return null; }
    }
}
