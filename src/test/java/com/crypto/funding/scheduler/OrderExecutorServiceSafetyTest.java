package com.crypto.funding.scheduler;

import com.crypto.funding.config.TradingExecutionProperties;
import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import com.crypto.funding.persistence.service.OrderExecutionTimeStore;
import com.crypto.funding.trading.TestOrderEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExecutorServiceSafetyTest
{
    @Mock
    private TestOrderEngine testOrderEngine;

    @Mock
    private ApprovedFundingRepository repo;

    @Mock
    private OrderExecutionTimeStore orderExecutionTimeStore;

    @Mock
    private AbstractRestClient bybit;

    @Mock
    private NetworkLatencyService latencyService;

    private OrderExecutorService service;

    @BeforeEach
    void setUp()
    {
        TradingExecutionProperties properties = new TradingExecutionProperties();
        LegacyExecutionGuard guard = new LegacyExecutionGuard( properties );
        service = new OrderExecutorService(
            testOrderEngine,
            repo,
            orderExecutionTimeStore,
            List.of( bybit ),
            latencyService,
            false,
            guard
        );
    }

    @Test
    void disabledModeSkipsLegacyExecutionWithoutMutation() throws Exception
    {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "BTC/USDT",
            Set.of( "bybit" ),
            new BigDecimal( "50" ),
            Instant.now().plusSeconds( 30 )
        );
        entity.setActive( true );
        entity.setExecuted( false );

        when( repo.findById( 10L ) ).thenReturn( Optional.of( entity ) );

        service.executeOnce( 10L );

        verify( testOrderEngine, never() ).placeTestOrder( org.mockito.ArgumentMatchers.any() );
        verify( repo, never() ).save( org.mockito.ArgumentMatchers.any() );
        assertThat( entity.isExecuted() ).isFalse();
    }

    @Test
    void markMissedDoesNotMutateStateWhenLegacyExecutionDisabled()
    {
        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "BTC/USDT",
            Set.of( "bybit" ),
            new BigDecimal( "50" ),
            Instant.now().minusSeconds( 120 )
        );
        entity.setActive( true );
        entity.setExecuted( false );

        service.markMissed( 11L, "BTCUSDT", entity.getNextFundingAt(), Instant.now() );

        verify( repo, never() ).save( org.mockito.ArgumentMatchers.any() );
        assertThat( entity.isExecuted() ).isFalse();
    }
}
