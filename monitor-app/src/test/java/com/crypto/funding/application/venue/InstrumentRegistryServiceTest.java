package com.crypto.funding.application.venue;

import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentRegistryServiceTest
{
    @Test
    void syncVenueToleratesDuplicateCanonicalSymbolsFromProviderAndRepository() throws Exception
    {
        VenueMetadataPort provider = mock( VenueMetadataPort.class );
        when( provider.venue() ).thenReturn( "gate" );
        when( provider.fetchPerpetualInstruments() ).thenReturn( List.of(
            instrument( "gate", "1000BONK/USDT", "1000BONK_USDT" ),
            instrument( "gate", "1000BONK/USDT", "1000BONK_USDT_2026" ),
            instrument( "gate", "NOM/USDT", "NOM_USDT" )
        ) );

        InstrumentMetadataJpaRepository repository = mock( InstrumentMetadataJpaRepository.class );
        when( repository.findAllByVenueOrderByCanonicalSymbolAsc( "gate" ) ).thenReturn( List.of(
            entity( "gate", "1000BONK/USDT", "1000BONK_USDT" ),
            entity( "gate", "1000BONK/USDT", "1000BONK_USDT_LEGACY" )
        ) );
        when( repository.save( any( InstrumentMetadataEntity.class ) ) ).thenAnswer( invocation -> invocation.getArgument( 0 ) );

        VenueRequestTimingService timingService = mock( VenueRequestTimingService.class );

        InstrumentRegistryService service = new InstrumentRegistryService(
            List.of( provider ),
            repository,
            timingService
        );

        assertThatCode( () -> service.syncVenue( "gate" ) ).doesNotThrowAnyException();

        verify( repository, atLeast( 3 ) ).save( any( InstrumentMetadataEntity.class ) );
        verify( timingService ).recordSuccess( eq( "gate" ), eq( "metadata-sync" ), anyLong(), eq( 3L ), eq( 200 ) );
    }

    private static InstrumentMetadata instrument( String venue, String canonicalSymbol, String venueSymbol )
    {
        Instant syncedAt = Instant.parse( "2026-04-04T17:00:00Z" );
        return new InstrumentMetadata(
            null,
            venue,
            canonicalSymbol,
            venueSymbol,
            canonicalSymbol.split( "/" )[0],
            "USDT",
            "PERPETUAL",
            InstrumentStatus.ACTIVE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            0,
            syncedAt,
            syncedAt,
            syncedAt
        );
    }

    private static InstrumentMetadataEntity entity( String venue, String canonicalSymbol, String venueSymbol )
    {
        InstrumentMetadataEntity entity = new InstrumentMetadataEntity();
        entity.setVenue( venue );
        entity.setCanonicalSymbol( canonicalSymbol );
        entity.setVenueSymbol( venueSymbol );
        entity.setBaseAsset( canonicalSymbol.split( "/" )[0] );
        entity.setQuoteAsset( "USDT" );
        entity.setInstrumentType( "PERPETUAL" );
        entity.setStatus( InstrumentStatus.ACTIVE );
        entity.setMinOrderQty( BigDecimal.ONE );
        entity.setQtyStep( BigDecimal.ONE );
        entity.setMinNotionalValue( BigDecimal.ONE );
        entity.setQuantityPrecision( 0 );
        entity.setLastSyncedAt( Instant.parse( "2026-04-04T16:00:00Z" ) );
        return entity;
    }
}
