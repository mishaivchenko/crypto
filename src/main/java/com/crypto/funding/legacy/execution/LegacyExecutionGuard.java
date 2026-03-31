package com.crypto.funding.legacy.execution;

import com.crypto.funding.config.ExecutionMode;
import com.crypto.funding.config.TradingExecutionProperties;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LegacyExecutionGuard
{
    private final TradingExecutionProperties properties;

    public static LegacyExecutionGuard permissive()
    {
        TradingExecutionProperties properties = new TradingExecutionProperties();
        properties.setMode( ExecutionMode.LIVE );
        properties.setLegacyEnabled( true );
        properties.setLiveVenues( Set.of( "binance", "bybit", "gate" ) );
        properties.setBlockedVenues( Set.of() );
        return new LegacyExecutionGuard( properties );
    }

    public LegacyExecutionGuard( TradingExecutionProperties properties )
    {
        this.properties = properties;
    }

    public LegacyExecutionDecision evaluate( Collection<String> requestedVenues, String entryPoint )
    {
        Set<String> normalizedRequested = normalize( requestedVenues );
        ExecutionMode mode = properties.getMode();

        if( mode != ExecutionMode.LIVE )
        {
            return LegacyExecutionDecision.blocked(
                mode,
                "legacy execution blocked for " + entryPoint + " because mode=" + mode,
                normalizedRequested,
                Set.of()
            );
        }

        if( !properties.isLegacyEnabled() )
        {
            return LegacyExecutionDecision.blocked(
                mode,
                "legacy execution blocked for " + entryPoint + " because legacy-enabled=false",
                normalizedRequested,
                Set.of()
            );
        }

        Set<String> blocked = properties.getBlockedVenues();
        Set<String> registeredLiveVenues = properties.getLiveVenues();
        Set<String> executable = normalizedRequested.stream()
                                                    .filter( venue -> !blocked.contains( venue ) )
                                                    .filter( registeredLiveVenues::contains )
                                                    .collect( Collectors.toCollection( LinkedHashSet::new ) );

        if( executable.isEmpty() )
        {
            return LegacyExecutionDecision.blocked(
                mode,
                "legacy execution blocked for " + entryPoint + " because no requested venue is explicitly executable",
                normalizedRequested,
                executable
            );
        }

        return LegacyExecutionDecision.allowed( mode, normalizedRequested, executable );
    }

    public void requireVenue( String venue, String entryPoint )
    {
        LegacyExecutionDecision decision = evaluate( Set.of( venue ), entryPoint );
        if( !decision.allowed() )
        {
            throw new LegacyExecutionBlockedException( decision.reason() );
        }
    }

    public boolean canMutateLegacyState()
    {
        return properties.getMode() == ExecutionMode.LIVE && properties.isLegacyEnabled();
    }

    public ExecutionMode mode()
    {
        return properties.getMode();
    }

    public boolean isLegacyEnabled()
    {
        return properties.isLegacyEnabled();
    }

    public Set<String> liveVenues()
    {
        return Set.copyOf( properties.getLiveVenues() );
    }

    public Set<String> blockedVenues()
    {
        return Set.copyOf( properties.getBlockedVenues() );
    }

    private static Set<String> normalize( Collection<String> values )
    {
        if( values == null )
        {
            return Set.of();
        }
        return values.stream()
                     .filter( value -> value != null && !value.isBlank() )
                     .map( value -> value.trim().toLowerCase( Locale.ROOT ) )
                     .collect( Collectors.toCollection( LinkedHashSet::new ) );
    }
}
