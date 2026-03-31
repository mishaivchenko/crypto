package com.crypto.funding.application.venue;

import com.crypto.funding.application.ResourceNotFoundException;
import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.mapper.InstrumentMetadataMapper;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InstrumentRegistryService
{
    private static final Logger log = LoggerFactory.getLogger( InstrumentRegistryService.class );

    private final Map<String, VenueMetadataPort> providersByVenue;
    private final InstrumentMetadataJpaRepository repository;
    private final VenueRequestTimingService venueRequestTimingService;

    public InstrumentRegistryService(
        List<VenueMetadataPort> providers,
        InstrumentMetadataJpaRepository repository,
        VenueRequestTimingService venueRequestTimingService
    )
    {
        this.providersByVenue = providers.stream()
                                         .collect( Collectors.toUnmodifiableMap(
                                             provider -> provider.venue().toLowerCase( Locale.ROOT ),
                                             Function.identity()
                                         ) );
        this.repository = repository;
        this.venueRequestTimingService = venueRequestTimingService;
    }

    @Transactional
    public List<InstrumentMetadata> syncVenue( String rawVenue )
    {
        String venue = normalizeVenue( rawVenue );
        VenueMetadataPort provider = providersByVenue.get( venue );
        if( provider == null )
        {
            throw new ResourceNotFoundException( "Venue metadata provider not found: " + rawVenue );
        }

        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        try
        {
            List<InstrumentMetadata> fetched = provider.fetchPerpetualInstruments()
                                                       .stream()
                                                       .sorted( Comparator.comparing( InstrumentMetadata::canonicalSymbol ) )
                                                       .toList();
            upsertVenueSnapshot( venue, fetched, startedAt );
            venueRequestTimingService.recordSuccess(
                venue,
                "metadata-sync",
                System.nanoTime() - startNanos,
                fetched.size(),
                200
            );
            log.info( "[metadata] synced venue={} instruments={}", venue, fetched.size() );
            return listVenueInstruments( venue );
        }
        catch( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
            venueRequestTimingService.recordFailure( venue, "metadata-sync", System.nanoTime() - startNanos, ex.getMessage() );
            throw new IllegalStateException( "Failed to sync metadata for venue " + venue, ex );
        }
        catch( IOException ex )
        {
            venueRequestTimingService.recordFailure( venue, "metadata-sync", System.nanoTime() - startNanos, ex.getMessage() );
            throw new IllegalStateException( "Failed to sync metadata for venue " + venue, ex );
        }
        catch( RuntimeException ex )
        {
            venueRequestTimingService.recordFailure( venue, "metadata-sync", System.nanoTime() - startNanos, ex.getMessage() );
            throw ex;
        }
    }

    @Transactional
    public void syncVenues( List<String> venues )
    {
        venues.forEach( this::syncVenue );
    }

    @Transactional(readOnly = true)
    public List<InstrumentMetadata> listVenueInstruments( String rawVenue )
    {
        return repository.findAllByVenueOrderByCanonicalSymbolAsc( normalizeVenue( rawVenue ) )
                         .stream()
                         .map( InstrumentMetadataMapper::toDomain )
                         .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findSupportedVenues( String canonicalSymbol )
    {
        return repository.findAllByCanonicalSymbolAndStatusOrderByVenueAsc(
                             canonicalSymbol == null ? null : canonicalSymbol.trim().toUpperCase( Locale.ROOT ),
                             InstrumentStatus.ACTIVE
                         )
                         .stream()
                         .map( InstrumentMetadataEntity::getVenue )
                         .toList();
    }

    @Transactional(readOnly = true)
    public long countActiveInstruments( String rawVenue )
    {
        return repository.countByVenueAndStatus( normalizeVenue( rawVenue ), InstrumentStatus.ACTIVE );
    }

    @Transactional(readOnly = true)
    public boolean hasProvider( String rawVenue )
    {
        return providersByVenue.containsKey( normalizeVenue( rawVenue ) );
    }

    @Transactional(readOnly = true)
    public List<String> supportedVenues()
    {
        return providersByVenue.keySet().stream().sorted().toList();
    }

    private void upsertVenueSnapshot( String venue, List<InstrumentMetadata> fetched, Instant syncedAt )
    {
        Map<String, InstrumentMetadataEntity> existing = repository.findAllByVenueOrderByCanonicalSymbolAsc( venue )
                                                                   .stream()
                                                                   .collect( Collectors.toMap(
                                                                       InstrumentMetadataEntity::getCanonicalSymbol,
                                                                       Function.identity()
                                                                   ) );

        for( InstrumentMetadata instrument : fetched )
        {
            InstrumentMetadataEntity entity = existing.remove( instrument.canonicalSymbol() );
            if( entity == null )
            {
                entity = new InstrumentMetadataEntity();
                entity.setVenue( venue );
                entity.setCanonicalSymbol( instrument.canonicalSymbol() );
            }
            entity.setVenueSymbol( instrument.venueSymbol() );
            entity.setBaseAsset( instrument.baseAsset() );
            entity.setQuoteAsset( instrument.quoteAsset() );
            entity.setInstrumentType( instrument.instrumentType() );
            entity.setStatus( instrument.status() );
            entity.setMinOrderQty( instrument.minOrderQty() );
            entity.setQtyStep( instrument.qtyStep() );
            entity.setMinNotionalValue( instrument.minNotionalValue() );
            entity.setQuantityPrecision( instrument.quantityPrecision() );
            entity.setLastSyncedAt( syncedAt );
            repository.save( entity );
        }

        for( InstrumentMetadataEntity stale : existing.values() )
        {
            stale.setStatus( InstrumentStatus.INACTIVE );
            stale.setLastSyncedAt( syncedAt );
            repository.save( stale );
        }
    }

    private static String normalizeVenue( String rawVenue )
    {
        if( rawVenue == null || rawVenue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        return rawVenue.trim().toLowerCase( Locale.ROOT );
    }
}
