package com.crypto.funding.infrastructure.source;

import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.symbol.SymbolMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
class FundingApiSymbolResolver
{
    private final SymbolMetadataPort symbolMetadataPort;

    FundingApiSymbolResolver( SymbolMetadataPort symbolMetadataPort )
    {
        this.symbolMetadataPort = symbolMetadataPort;
    }

    Optional<ResolvedFundingSymbol> resolve( FundingApiEntry entry )
    {
        String venue = entry.exchange().trim().toLowerCase( Locale.ROOT );
        List<String> candidates = new ArrayList<>();
        addCandidate( candidates, entry.symbol() );
        addCandidate( candidates, entry.coin() );

        for( String candidate : candidates )
        {
            Optional<String> canonicalByVenueSymbol = symbolMetadataPort.findByVenueSymbol( venue, candidate ).map( symbol -> symbol.symbol() );
            if( canonicalByVenueSymbol.isPresent() )
            {
                return Optional.of( new ResolvedFundingSymbol( candidate, canonicalByVenueSymbol.get() ) );
            }
        }

        for( String candidate : candidates )
        {
            String unified = SymbolMapper.toUnified( candidate );
            if( unified != null && symbolMetadataPort.findSymbolMetadata( venue, unified ).isPresent() )
            {
                return Optional.of( new ResolvedFundingSymbol( candidate, unified ) );
            }
        }

        for( String candidate : candidates )
        {
            String fallback = SymbolMapper.toUnified( candidate );
            if( fallback != null && !fallback.isBlank() )
            {
                return Optional.of( new ResolvedFundingSymbol( candidate, fallback ) );
            }
        }

        return Optional.empty();
    }

    private void addCandidate( List<String> candidates, String value )
    {
        if( value == null || value.isBlank() )
        {
            return;
        }
        String normalized = value.trim().toUpperCase( Locale.ROOT );
        if( !candidates.contains( normalized ) )
        {
            candidates.add( normalized );
        }
    }
}
