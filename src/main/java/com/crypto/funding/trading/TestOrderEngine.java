package com.crypto.funding.trading;

import com.crypto.funding.legacy.execution.LegacyExecutionGuard;
import com.crypto.funding.exchanges.AbstractRestClient;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Legacy order-placement facade kept only for transitional diagnostics and explicitly guarded testing.
 */
@Service
public class TestOrderEngine
{
    private final Map<String, AbstractRestClient> clientsByName;
    private final LegacyExecutionGuard legacyExecutionGuard;
    private final VenueRequestTimingService timingService;

    // этот конструктор соберёт Map из всех бинов ExchangeTradingClient
    @Autowired
    public TestOrderEngine(
        List<AbstractRestClient> clients,
        LegacyExecutionGuard legacyExecutionGuard,
        VenueRequestTimingService timingService
    )
    {
        this.clientsByName = clients.stream()
                                    .collect( Collectors.toUnmodifiableMap(
                                        c -> c.exchangeName().toLowerCase(),
                                        Function.identity()
                                    ) );
        this.legacyExecutionGuard = legacyExecutionGuard;
        this.timingService = timingService;
    }

    public TestOrderEngine( List<AbstractRestClient> clients, LegacyExecutionGuard legacyExecutionGuard )
    {
        this( clients, legacyExecutionGuard, new VenueRequestTimingService() );
    }

    public TestOrderEngine( List<AbstractRestClient> clients )
    {
        this( clients, LegacyExecutionGuard.permissive() );
    }

    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd )
    {
        String ex = cmd.exchange().toLowerCase();
        legacyExecutionGuard.requireVenue( ex, "legacy-test-order-endpoint" );
        AbstractRestClient client = clientsByName.get( ex );

        if( client == null )
        {
            throw new IllegalArgumentException( "Unsupported exchange: " + ex );
        }

        long startNanos = System.nanoTime();
        try
        {
            TestOrderResult result = client.placeTestOrder( cmd );
            timingService.recordSuccess( ex, "test-order", System.nanoTime() - startNanos, 0L, null );
            return result;
        }
        catch( Exception e )
        {
            timingService.recordFailure( ex, "test-order", System.nanoTime() - startNanos, e.getMessage() );
            // позже можно завести свой тип исключения/логирование
            throw new RuntimeException( "Failed to place test order on " + ex, e );
        }
    }
}
