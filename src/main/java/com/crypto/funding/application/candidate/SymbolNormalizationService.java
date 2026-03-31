package com.crypto.funding.application.candidate;

import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.utills.SymbolMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class SymbolNormalizationService
{
    private static final Pattern CANONICAL_SYMBOL = Pattern.compile( "^[A-Z][A-Z0-9]{1,14}/(USDT|USDC|BTC|ETH)$" );

    private final SymbolMetadataPort symbolMetadataPort;
    private final MetadataSyncProperties metadataSyncProperties;

    public SymbolNormalizationService(
        SymbolMetadataPort symbolMetadataPort,
        MetadataSyncProperties metadataSyncProperties
    )
    {
        this.symbolMetadataPort = symbolMetadataPort;
        this.metadataSyncProperties = metadataSyncProperties;
    }

    public CandidateNormalizationResult normalize( String rawSymbol )
    {
        if( rawSymbol == null || rawSymbol.isBlank() )
        {
            return new CandidateNormalizationResult( null, List.of(), "raw symbol is blank" );
        }

        String normalized = SymbolMapper.toUnified( rawSymbol );
        if( normalized == null || !CANONICAL_SYMBOL.matcher( normalized ).matches() )
        {
            return new CandidateNormalizationResult( null, List.of(), "unable to normalize symbol into canonical perp form" );
        }

        List<String> venueHints = metadataSyncProperties.getEnabledVenues()
                                                        .stream()
                                                        .filter( Objects::nonNull )
                                                        .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                                                        .filter( venue -> !venue.isBlank() )
                                                        .filter( venue -> symbolMetadataPort.findSymbolMetadata( venue, normalized ).isPresent() )
                                                        .distinct()
                                                        .sorted( Comparator.naturalOrder() )
                                                        .toList();

        if( venueHints.isEmpty() && metadataSyncProperties.isBootstrapFallbackEnabled() )
        {
            venueHints = metadataSyncProperties.getEnabledVenues()
                                              .stream()
                                              .filter( Objects::nonNull )
                                              .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                                              .filter( venue -> !venue.isBlank() )
                                              .distinct()
                                              .sorted( Comparator.naturalOrder() )
                                              .toList();
        }

        if( venueHints.isEmpty() )
        {
            return new CandidateNormalizationResult( normalized, List.of(), "no supported venues found for normalized symbol" );
        }

        return new CandidateNormalizationResult( normalized, venueHints, null );
    }
}
