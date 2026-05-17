package com.crypto.funding.application.venue;

import com.crypto.funding.application.port.VenueMarkPricePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VenueMarkPriceService
{
    private static final Logger log = LoggerFactory.getLogger( VenueMarkPriceService.class );

    private final Map<String, VenueMarkPricePort> portsByVenue;

    public VenueMarkPriceService( List<VenueMarkPricePort> ports )
    {
        this.portsByVenue = ports.stream().collect( Collectors.toMap( VenueMarkPricePort::venue, Function.identity() ) );
    }

    public Optional<BigDecimal> fetchMarkPrice( String venue, String venueSymbol )
    {
        VenueMarkPricePort port = portsByVenue.get( venue );
        if( port == null || venueSymbol == null || venueSymbol.isBlank() )
        {
            return Optional.empty();
        }
        try
        {
            BigDecimal price = port.getMarkPrice( venueSymbol );
            return Optional.ofNullable( price );
        }
        catch( Exception e )
        {
            log.debug( "Mark price fetch failed for {}/{}: {}", venue, venueSymbol, e.getMessage() );
            return Optional.empty();
        }
    }
}
