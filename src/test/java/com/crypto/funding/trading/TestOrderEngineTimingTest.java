package com.crypto.funding.trading;

import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOrderEngineTimingTest
{
    @Test
    void recordsTestOrderLatencyForEntryTimingEstimation() throws Exception
    {
        AbstractRestClient client = mock( AbstractRestClient.class );
        when( client.exchangeName() ).thenReturn( "gate" );

        PlaceTestOrderCommand command = new PlaceTestOrderCommand(
            "gate",
            "BTC_USDT",
            OrderSide.SELL,
            OrderType.MARKET,
            BigDecimal.ONE,
            null
        );
        when( client.placeTestOrder( command ) ).thenReturn( new TestOrderResult(
            "gate",
            "test-1",
            "BTC_USDT",
            OrderSide.SELL,
            OrderType.MARKET,
            BigDecimal.ONE,
            null,
            "FILLED",
            System.currentTimeMillis(),
            null,
            OrderTimestampSource.UNKNOWN
        ) );

        VenueRequestTimingService timingService = new VenueRequestTimingService();
        TestOrderEngine engine = new TestOrderEngine(
            List.of( client ),
            LegacyExecutionGuard.permissive(),
            timingService
        );

        engine.placeTestOrder( command );

        assertThat( timingService.snapshots( "gate" ) )
            .singleElement()
            .satisfies( snapshot -> {
                assertThat( snapshot.operation() ).isEqualTo( "test-order" );
                assertThat( snapshot.requests() ).isEqualTo( 1 );
                assertThat( snapshot.successes() ).isEqualTo( 1 );
            } );
    }
}
