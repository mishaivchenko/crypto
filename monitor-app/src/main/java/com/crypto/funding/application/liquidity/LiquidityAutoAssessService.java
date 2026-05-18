package com.crypto.funding.application.liquidity;

import com.crypto.funding.application.venue.InstrumentRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LiquidityAutoAssessService
{
    private static final Logger log = LoggerFactory.getLogger( LiquidityAutoAssessService.class );

    private final LiquidityAssessmentService liquidityAssessmentService;
    private final InstrumentRegistryService instrumentRegistryService;

    public LiquidityAutoAssessService(
        LiquidityAssessmentService liquidityAssessmentService,
        InstrumentRegistryService instrumentRegistryService
    )
    {
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.instrumentRegistryService = instrumentRegistryService;
    }

    @Async
    public void assessAfterArm( Long tradeId, String venue, String canonicalSymbol )
    {
        String venueSymbol = instrumentRegistryService
            .resolveVenueSymbol( venue, canonicalSymbol )
            .orElse( null );
        if( venueSymbol == null )
        {
            log.debug( "Skipping auto liquidity assessment for trade {} — no instrument metadata for {}/{}", tradeId, venue, canonicalSymbol );
            return;
        }
        try
        {
            liquidityAssessmentService.assess( venue, venueSymbol, tradeId );
            log.debug( "Auto liquidity assessment completed for trade {} ({}/{})", tradeId, venue, venueSymbol );
        }
        catch( Exception e )
        {
            log.warn( "Auto liquidity assessment failed for trade {} ({}/{}): {}", tradeId, venue, venueSymbol, e.getMessage() );
        }
    }
}
