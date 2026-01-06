package com.crypto.funding.trading;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TestOrderEngine
{
    private final Map<String, ExchangeTradingClient> clientsByName;

    // этот конструктор соберёт Map из всех бинов ExchangeTradingClient
    public TestOrderEngine( List<ExchangeTradingClient> clients )
    {
        this.clientsByName = clients.stream()
                                    .collect( Collectors.toUnmodifiableMap(
                                        c -> c.name().toLowerCase(),
                                        Function.identity()
                                    ) );
    }

    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd )
    {
        String ex = cmd.exchange().toLowerCase();
        ExchangeTradingClient client = clientsByName.get( ex );

        if( client == null )
        {
            throw new IllegalArgumentException( "Unsupported exchange: " + ex );
        }

        try
        {
            return client.placeTestOrder( cmd );
        }
        catch( Exception e )
        {
            // позже можно завести свой тип исключения/логирование
            throw new RuntimeException( "Failed to place test order on " + ex, e );
        }
    }
}
