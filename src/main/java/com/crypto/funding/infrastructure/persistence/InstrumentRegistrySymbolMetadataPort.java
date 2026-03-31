package com.crypto.funding.infrastructure.persistence;

import com.crypto.funding.application.port.SymbolMetadata;
import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Primary
public class InstrumentRegistrySymbolMetadataPort implements SymbolMetadataPort
{
    private final InstrumentMetadataJpaRepository repository;

    public InstrumentRegistrySymbolMetadataPort( InstrumentMetadataJpaRepository repository )
    {
        this.repository = repository;
    }

    @Override
    public Optional<SymbolMetadata> findSymbolMetadata( String venue, String symbol )
    {
        if( venue == null || symbol == null )
        {
            return Optional.empty();
        }

        return repository.findByVenueAndCanonicalSymbolAndStatus(
                             venue.trim().toLowerCase( Locale.ROOT ),
                             symbol.trim().toUpperCase( Locale.ROOT ),
                             InstrumentStatus.ACTIVE
                         )
                         .map( entity -> new SymbolMetadata(
                             entity.getVenue(),
                             entity.getCanonicalSymbol(),
                             entity.getMinOrderQty(),
                             entity.getQtyStep(),
                             entity.getMinNotionalValue()
                         ) );
    }
}
