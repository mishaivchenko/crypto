package com.crypto.funding.infrastructure.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Component
class FundingObservationMapper
{
    private static final Logger log = LoggerFactory.getLogger( FundingObservationMapper.class );
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

    private final Clock clock;

    FundingObservationMapper()
    {
        this( Clock.systemUTC() );
    }

    FundingObservationMapper( Clock clock )
    {
        this.clock = clock;
    }

    FundingObservation toObservation( FundingApiEntry entry, String canonicalSymbol )
    {
        Instant referenceTime = parseUpdatedAt( entry.updatedAt() );
        Instant nextFundingAt = computeNextFundingAt( referenceTime, entry.fundingInterval() );
        return new FundingObservation(
            referenceTime,
            canonicalSymbol,
            nextFundingAt,
            decimalOrNull( entry.funding() )
        );
    }

    long syntheticMessageId( FundingApiEntry entry, String symbol )
    {
        if( entry.id() != null )
        {
            return Integer.toUnsignedLong( ( entry.exchange() + ":" + entry.id() ).hashCode() );
        }
        return Integer.toUnsignedLong( ( entry.exchange() + ":" + symbol ).hashCode() );
    }

    private Instant parseUpdatedAt( String rawUpdatedAt )
    {
        if( rawUpdatedAt == null || rawUpdatedAt.isBlank() )
        {
            return Instant.now( clock );
        }
        try
        {
            return LocalDateTime.parse( rawUpdatedAt.trim(), UPDATED_AT_FORMAT ).toInstant( ZoneOffset.UTC );
        }
        catch( DateTimeParseException ex )
        {
            log.debug( "[candidate-source] failed to parse updated_at='{}', fallback to now", rawUpdatedAt, ex );
            return Instant.now( clock );
        }
    }

    private Instant computeNextFundingAt( Instant referenceTime, Integer fundingIntervalHours )
    {
        int intervalHours = fundingIntervalHours == null || fundingIntervalHours <= 0 ? 8 : fundingIntervalHours;
        ZonedDateTime utc = referenceTime.atZone( ZoneOffset.UTC ).truncatedTo( ChronoUnit.HOURS );
        ZonedDateTime midnight = utc.toLocalDate().atStartOfDay( ZoneOffset.UTC );
        int currentHour = utc.getHour();
        int nextBucketHour = ( ( currentHour / intervalHours ) + 1 ) * intervalHours;
        ZonedDateTime nextFunding = midnight.plusHours( nextBucketHour );
        Instant now = Instant.now( clock );
        if( !nextFunding.isAfter( utc ) )
        {
            nextFunding = nextFunding.plusHours( intervalHours );
        }
        while( !nextFunding.toInstant().isAfter( now ) )
        {
            nextFunding = nextFunding.plusHours( intervalHours );
        }
        return nextFunding.toInstant();
    }

    private BigDecimal decimalOrNull( String raw )
    {
        if( raw == null || raw.isBlank() )
        {
            return null;
        }
        return new BigDecimal( raw );
    }
}
