package com.crypto.funding.application.candidate;

import com.crypto.funding.application.liquidity.LiquidityAssessmentService;
import com.crypto.funding.application.venue.InstrumentRegistryService;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.liquidity.LiquidityAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SignalLiquidityService
{
    private static final Logger log = LoggerFactory.getLogger( SignalLiquidityService.class );

    private final LiquidityAssessmentService liquidityAssessmentService;
    private final InstrumentRegistryService instrumentRegistryService;

    public SignalLiquidityService(
        LiquidityAssessmentService liquidityAssessmentService,
        InstrumentRegistryService instrumentRegistryService
    )
    {
        this.liquidityAssessmentService = liquidityAssessmentService;
        this.instrumentRegistryService = instrumentRegistryService;
    }

    public Optional<LiquidityAssessment> assess( SignalCandidate candidate )
    {
        String venue = resolveVenue( candidate );
        if( venue == null )
        {
            log.debug( "Skipping liquidity assessment for candidate {} — no venue available", candidate.id() );
            return Optional.empty();
        }

        String venueSymbol = instrumentRegistryService.resolveVenueSymbol( venue, candidate.normalizedSymbol() ).orElse( null );
        if( venueSymbol == null )
        {
            log.debug( "Skipping liquidity assessment for candidate {} — no instrument metadata for {}/{}", candidate.id(), venue, candidate.normalizedSymbol() );
            return Optional.empty();
        }

        try
        {
            LiquidityAssessment assessment = liquidityAssessmentService.assessForCandidate( venue, venueSymbol, candidate.id() );
            log.debug( "Liquidity assessment for candidate {} ({}/{}): {}", candidate.id(), venue, venueSymbol, assessment.score() );
            return Optional.of( assessment );
        }
        catch( Exception e )
        {
            log.warn( "Liquidity assessment failed for candidate {} ({}/{}): {}", candidate.id(), venue, venueSymbol, e.getMessage() );
            return Optional.empty();
        }
    }

    private String resolveVenue( SignalCandidate candidate )
    {
        if( candidate.sourceVenue() != null && !candidate.sourceVenue().isBlank() )
        {
            return candidate.sourceVenue();
        }
        List<String> hints = candidate.venueHints();
        if( hints != null && !hints.isEmpty() )
        {
            return hints.get( 0 );
        }
        return null;
    }
}
