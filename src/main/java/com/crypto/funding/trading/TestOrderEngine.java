package com.crypto.funding.trading;

import com.crypto.funding.exchanges.AbstractRestClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TestOrderEngine
{
    private final Map<String, AbstractRestClient> clientsByName;

    // этот конструктор соберёт Map из всех бинов ExchangeTradingClient
    public TestOrderEngine( List<AbstractRestClient> clients )
    {
        this.clientsByName = clients.stream()
                                    .collect( Collectors.toUnmodifiableMap(
                                        c -> c.exchangeName().toLowerCase(),
                                        Function.identity()
                                    ) );
    }

    public TestOrderResult placeTestOrder( PlaceTestOrderCommand cmd )
    {
        String ex = cmd.exchange().toLowerCase();
        AbstractRestClient client = clientsByName.get( ex );

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
