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
        return normalize( rawSymbol, null );
    }

    public CandidateNormalizationResult normalize( String rawSymbol, String sourceVenue )
    {
        if( rawSymbol == null || rawSymbol.isBlank() )
        {
            return new CandidateNormalizationResult( null, List.of(), "Символ сигнала пустой." );
        }

        String normalized = SymbolMapper.toUnified( rawSymbol );
        if( normalized == null || !CANONICAL_SYMBOL.matcher( normalized ).matches() )
        {
            return new CandidateNormalizationResult( null, List.of(), "Не удалось привести символ к каноническому виду perpetual-контракта." );
        }

        String lockedVenue = normalizeVenue( sourceVenue );
        if( lockedVenue != null )
        {
            if( symbolMetadataPort.findByVenueSymbol( lockedVenue, rawSymbol ).isPresent()
                || symbolMetadataPort.findSymbolMetadata( lockedVenue, normalized ).isPresent()
                || metadataSyncProperties.isBootstrapFallbackEnabled() )
            {
                return new CandidateNormalizationResult( normalized, List.of( lockedVenue ), null );
            }

            return new CandidateNormalizationResult( normalized, List.of( lockedVenue ), null );
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
            return new CandidateNormalizationResult( normalized, List.of(), "Для нормализованного символа не найдено поддерживаемых площадок." );
        }

        return new CandidateNormalizationResult( normalized, venueHints, null );
    }

    private String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            return null;
        }

        String venue = rawVenue.trim().toLowerCase( Locale.ROOT );
        boolean enabled = metadataSyncProperties.getEnabledVenues()
                                               .stream()
                                               .filter( Objects::nonNull )
                                               .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                                               .anyMatch( venue::equals );
        return enabled ? venue : null;
    }
}
