package com.crypto.funding.infrastructure.source;

import com.crypto.funding.application.port.SymbolMetadata;
import com.crypto.funding.application.port.SymbolMetadataPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FundingApiSymbolResolverTest
{
    @Test
    void prefersVenueSymbolMetadataMatch()
    {
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        FundingApiSymbolResolver resolver = new FundingApiSymbolResolver( symbolMetadataPort );
        FundingApiEntry entry = new FundingApiEntry(
            1L,
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
            "2030-04-04 07:30:00"
        );

        when( symbolMetadataPort.findByVenueSymbol( "bybit", "BTCUSDT" ) )
            .thenReturn( Optional.of( new SymbolMetadata(
                "bybit",
                "BTC/USDT",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ) ) );

        Optional<ResolvedFundingSymbol> resolved = resolver.resolve( entry );

        assertThat( resolved ).contains( new ResolvedFundingSymbol( "BTCUSDT", "BTC/USDT" ) );
    }

    @Test
    void fallsBackToUnifiedSymbolMetadataMatch()
    {
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        FundingApiSymbolResolver resolver = new FundingApiSymbolResolver( symbolMetadataPort );
        FundingApiEntry entry = new FundingApiEntry(
            2L,
            "BTCUSDM",
            "BTC",
            "kucoin",
            "Kucoin",
            null,
            null,
            null,
            null,
            null,
            null,
            8,
            "2030-04-04 07:30:00"
        );

        when( symbolMetadataPort.findByVenueSymbol( "kucoin", "BTCUSDM" ) ).thenReturn( Optional.empty() );
        when( symbolMetadataPort.findByVenueSymbol( "kucoin", "BTC" ) ).thenReturn( Optional.empty() );
        when( symbolMetadataPort.findSymbolMetadata( "kucoin", "BTC/USDT" ) )
            .thenReturn( Optional.of( new SymbolMetadata(
                "kucoin",
                "BTC/USDT",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ) ) );

        Optional<ResolvedFundingSymbol> resolved = resolver.resolve( entry );

        assertThat( resolved ).contains( new ResolvedFundingSymbol( "BTCUSDM", "BTC/USDT" ) );
    }

    @Test
    void fallsBackToUnifiedSymbolStringWhenMetadataIsMissing()
    {
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        FundingApiSymbolResolver resolver = new FundingApiSymbolResolver( symbolMetadataPort );
        FundingApiEntry entry = new FundingApiEntry(
            3L,
            "ETHUSDT",
            "ETH",
            "gate",
            "Gate",
            null,
            null,
            null,
            null,
            null,
            null,
            8,
            "2030-04-04 07:30:00"
        );

        when( symbolMetadataPort.findByVenueSymbol( "gate", "ETHUSDT" ) ).thenReturn( Optional.empty() );
        when( symbolMetadataPort.findByVenueSymbol( "gate", "ETH" ) ).thenReturn( Optional.empty() );
        when( symbolMetadataPort.findSymbolMetadata( "gate", "ETH/USDT" ) ).thenReturn( Optional.empty() );

        Optional<ResolvedFundingSymbol> resolved = resolver.resolve( entry );

        assertThat( resolved ).contains( new ResolvedFundingSymbol( "ETHUSDT", "ETH/USDT" ) );
    }
}
