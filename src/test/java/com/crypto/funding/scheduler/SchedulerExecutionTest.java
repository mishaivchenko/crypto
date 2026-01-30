package com.crypto.funding.scheduler;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.trading.ExchangeTradingClient;
import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import com.crypto.funding.watchlist.FundingInfo;
import com.crypto.funding.watchlist.SymbolRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Интеграционный тест планировщика: убеждаемся, что executeOnce вызывается,
 * когда задача попадает в due window.
 */
class SchedulerExecutionTest
{
    private ApprovedFundingRepository repo;
    private RecordingTaskScheduler scheduler;
    private OrderExecutorService orderExecutorService;

    @BeforeEach
    void setUp()
    {
        repo = Mockito.mock( ApprovedFundingRepository.class );
        scheduler = new RecordingTaskScheduler();
        orderExecutorService = Mockito.mock( OrderExecutorService.class );
    }

    @Test
    void executesWhenInDueWindow() throws Exception
    {
        Instant now = Instant.now();
        Instant nextFunding = now.plusSeconds( 3 );
        ApprovedFundingEntity entity = new ApprovedFundingEntity( "BTC/USDT", Set.of( "bybit" ),
                                                                  BigDecimal.valueOf( 50 ), nextFunding );
        setId( entity, 42L );

        when( repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBetween( any(), any() ) )
            .thenReturn( List.of( entity ) );
        when( repo.findFirstByActiveTrueAndExecutedFalseOrderByNextFundingAtAsc() ).thenReturn( Optional.of( entity ) );

        FundingSchedulerService service = new FundingSchedulerService(
            repo,
            scheduler,
            orderExecutorService,
            10,
            1,      // executionDelay 1s
            200,    // minRecheckMillis
            120,
            15
        );

        service.wakeup( "test" );

        // имитируем наступление времени - руками запускаем сохранённый runnable
        assertThat( scheduler.lastRunnable ).isNotNull();
        scheduler.lastRunnable.run();

        verify( orderExecutorService, timeout( 1000 ) ).executeOnce( 42L );
    }

    private static void setId( ApprovedFundingEntity entity, long id )
    {
        try
        {
            var f = ApprovedFundingEntity.class.getDeclaredField( "id" );
            f.setAccessible( true );
            f.set( entity, id );
        }
        catch( Exception e )
        {
            throw new RuntimeException( e );
        }
    }

    private static class RecordingTaskScheduler implements TaskScheduler
    {
        Runnable lastRunnable;
        Date scheduledAt;

        @Override
        public ScheduledFuture<?> schedule( Runnable task, Date startTime )
        {
            this.lastRunnable = task;
            this.scheduledAt = startTime;
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule( Runnable task, Instant startTime )
        {
            this.lastRunnable = task;
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
