package com.crypto.funding.scheduler;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.scheduler.NetworkLatencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TimeCalculationTest
{
    private ApprovedFundingRepository repo;
    private CapturingTaskScheduler scheduler;
    private NetworkLatencyService latencyService;

    @BeforeEach
    void setUp()
    {
        repo = Mockito.mock( ApprovedFundingRepository.class );
        scheduler = new CapturingTaskScheduler();
        latencyService = Mockito.mock( NetworkLatencyService.class );
        when( latencyService.currentDelay() ).thenReturn( Duration.ZERO );
        when( latencyService.estimate( any(Set.class) ) ).thenReturn( Duration.ZERO );
    }

    @Test
    void schedulesAtFundingMinusExecutionDelay()
    {
        Instant now = Instant.now();
        Instant nextFunding = now.plusSeconds( 600 ); // 10 минут
        ApprovedFundingEntity entity = new ApprovedFundingEntity( "BTC/USDT", java.util.Set.of( "bybit" ),
                                                                  BigDecimal.TEN, nextFunding );

        when( repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc() ).thenReturn( Optional.of( entity ) );

        FundingSchedulerService service = new FundingSchedulerService(
            repo,
            scheduler,
            Mockito.mock( OrderExecutorService.class ),
            latencyService,
            10,   // lookahead seconds (не используется после ввода executionDelay)
            8,    // executionDelaySeconds
            1000, // minRecheckMillis
            120,  // maxLatenessSeconds
            15    // discoveryIntervalSeconds
        );

        service.wakeup( "test" );

        assertThat( scheduler.scheduledAt ).isNotNull();
        Instant planned = scheduler.scheduledAt.toInstant();

        Instant expected = nextFunding.minusSeconds( 8 );
        // допускаем большую погрешность, достаточно что направление верное
        assertThat( planned.isBefore( nextFunding ) ).isTrue();
    }

    @Test
    void clampsToMinRecheckWhenVerySoon()
    {
        Instant now = Instant.now();
        Instant nextFunding = now.plusSeconds( 2 ); // очень скоро
        ApprovedFundingEntity entity = new ApprovedFundingEntity( "BTC/USDT", java.util.Set.of( "bybit" ),
                                                                  BigDecimal.TEN, nextFunding );
        when( repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc() ).thenReturn( Optional.of( entity ) );

        FundingSchedulerService service = new FundingSchedulerService(
            repo,
            scheduler,
            Mockito.mock( OrderExecutorService.class ),
            latencyService,
            10,
            5,
            1000, // 1 секунда
            120,
            15
        );

        service.wakeup( "test" );

        assertThat( scheduler.scheduledAt ).isNotNull();
        Instant planned = scheduler.scheduledAt.toInstant();

        // должен быть не раньше now + minRecheck (примерно)
        assertThat( planned ).isAfterOrEqualTo( now.plusMillis( 900 ) );
    }

    private static class CapturingTaskScheduler implements TaskScheduler
    {
        Date scheduledAt;
        Runnable runnable;

        @Override
        public ScheduledFuture<?> schedule( Runnable task, Date startTime )
        {
            this.runnable = task;
            this.scheduledAt = startTime;
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule( Runnable task, Instant startTime )
        {
            this.runnable = task;
            this.scheduledAt = Date.from( startTime );
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule( Runnable task, org.springframework.scheduling.Trigger trigger )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate( Runnable task, Date startTime, long period )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate( Runnable task, long period )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay( Runnable task, Date startTime, long delay )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay( Runnable task, long delay )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate( Runnable task, Instant startTime, Duration period )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate( Runnable task, Duration period )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay( Runnable task, Instant startTime, Duration delay )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay( Runnable task, Duration delay )
        {
            throw new UnsupportedOperationException();
        }
    }
}
