package com.crypto.funding.infrastructure.source;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FundingObservationMapperTest
{
    @Test
    void mapsFundingObservationUsingParsedUpdatedAtAndNextFundingBucket()
    {
        FundingObservationMapper mapper = new FundingObservationMapper( fixedClock( "2030-04-04T11:35:00Z" ) );
        FundingApiEntry entry = new FundingApiEntry(
            11L,
            "BTCUSDT",
            "BTC",
            "bybit",
            "Bybit",
            null,
            "0.0125",
            null,
            null,
            null,
            null,
            8,
            "2030-04-04 07:30:00"
        );

        FundingObservation observation = mapper.toObservation( entry, "BTC/USDT" );

        assertThat( observation.detectedAt() ).isEqualTo( Instant.parse( "2030-04-04T07:30:00Z" ) );
        assertThat( observation.fundingTime() ).isEqualTo( Instant.parse( "2030-04-04T16:00:00Z" ) );
        assertThat( observation.fundingRatePct() ).isEqualByComparingTo( "0.0125" );
    }

    @Test
    void fallsBackToClockWhenUpdatedAtCannotBeParsed()
    {
        FundingObservationMapper mapper = new FundingObservationMapper( fixedClock( "2030-04-04T11:35:00Z" ) );
        FundingApiEntry entry = new FundingApiEntry(
            12L,
            "BTCUSDT",
            "BTC",
            "bybit",
            "Bybit",
            null,
            null,
            null,
            null,
            null,
            null,
            8,
            "not-a-date"
        );

        FundingObservation observation = mapper.toObservation( entry, "BTC/USDT" );

        assertThat( observation.detectedAt() ).isEqualTo( Instant.parse( "2030-04-04T11:35:00Z" ) );
        assertThat( observation.fundingTime() ).isEqualTo( Instant.parse( "2030-04-04T16:00:00Z" ) );
        assertThat( observation.fundingRatePct() ).isNull();
    }

    @Test
    void generatesStableSyntheticMessageIds()
    {
        FundingObservationMapper mapper = new FundingObservationMapper( fixedClock( "2030-04-04T11:35:00Z" ) );
        FundingApiEntry entry = new FundingApiEntry(
            42L,
            "NOMUSDT",
            "NOM",
            "gate",
            "Gate",
            null,
            "-0.0125",
            null,
            null,
            null,
            null,
            4,
            "2030-04-04 07:30:00"
        );

        long first = mapper.syntheticMessageId( entry, "NOM/USDT" );
        long second = mapper.syntheticMessageId( entry, "NOM/USDT" );

        assertThat( first ).isEqualTo( second );
    }

    private static Clock fixedClock( String instant )
    {
        return Clock.fixed( Instant.parse( instant ), ZoneOffset.UTC );
    }
}
